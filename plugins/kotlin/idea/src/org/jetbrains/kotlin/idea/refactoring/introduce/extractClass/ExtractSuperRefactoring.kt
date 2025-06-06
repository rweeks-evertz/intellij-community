// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.introduce.extractClass

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.extractSuperclass.ExtractSuperClassUtil
import com.intellij.refactoring.memberPullUp.PullUpProcessor
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.refactoring.util.DocCommentPolicy
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.idea.actions.createKotlinFileFromTemplate
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.codeInsight.shorten.performDelayedRefactoringRequests
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.core.getFqNameWithImplicitPrefix
import org.jetbrains.kotlin.idea.core.getPackage
import org.jetbrains.kotlin.idea.core.quoteSegmentsIfNeeded
import org.jetbrains.kotlin.idea.core.util.runSynchronouslyWithProgress
import org.jetbrains.kotlin.idea.refactoring.introduce.insertDeclaration
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.refactoring.memberInfo.getChildrenToAnalyze
import org.jetbrains.kotlin.idea.refactoring.memberInfo.toJavaMemberInfo
import org.jetbrains.kotlin.idea.refactoring.move.KotlinMoveConflictCheckerInfo
import org.jetbrains.kotlin.idea.refactoring.move.KotlinMoveTarget
import org.jetbrains.kotlin.idea.refactoring.move.checkAllConflicts
import org.jetbrains.kotlin.idea.refactoring.pullUp.checkVisibilityInAbstractedMembers
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperClassNotAny
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.utils.findClassifier

data class ExtractSuperInfo(
    val originalClass: KtClassOrObject,
    val memberInfos: Collection<KotlinMemberInfo>,
    val targetParent: PsiElement,
    val targetFileName: String,
    val newClassName: String,
    val isInterface: Boolean,
    val docPolicy: DocCommentPolicy
)

class ExtractSuperRefactoring(
    private var extractInfo: ExtractSuperInfo
) {
    companion object {
        private fun getElementsToMove(
            memberInfos: Collection<KotlinMemberInfo>,
            originalClass: KtClassOrObject,
            isExtractInterface: Boolean
        ): Map<KtElement, KotlinMemberInfo?> {
            val project = originalClass.project
            val elementsToMove = LinkedHashMap<KtElement, KotlinMemberInfo?>()
            runReadAction {
                val superInterfacesToMove = ArrayList<KtElement>()
                for (memberInfo in memberInfos) {
                    val member = memberInfo.member ?: continue
                    if (memberInfo.isSuperClass) {
                        superInterfacesToMove += member
                    } else {
                        elementsToMove[member] = memberInfo
                    }
                }

                val superTypeList = originalClass.getSuperTypeList()
                if (superTypeList != null) {
                    for (superTypeListEntry in originalClass.superTypeListEntries) {
                        val superType =
                            superTypeListEntry.analyze(BodyResolveMode.PARTIAL)[BindingContext.TYPE, superTypeListEntry.typeReference]
                                ?: continue
                        val superClassDescriptor = superType.constructor.declarationDescriptor ?: continue
                        val superClass = DescriptorToSourceUtilsIde.getAnyDeclaration(project, superClassDescriptor) as? KtClass ?: continue
                        if ((!isExtractInterface && !superClass.isInterface()) || superClass in superInterfacesToMove) {
                            elementsToMove[superTypeListEntry] = null
                        }
                    }
                }
            }
            return elementsToMove
        }

        fun collectConflicts(
            originalClass: KtClassOrObject,
            memberInfos: List<KotlinMemberInfo>,
            targetParent: PsiElement,
            newClassName: String,
            isExtractInterface: Boolean
        ): MultiMap<PsiElement, String> {
            val conflicts = MultiMap<PsiElement, String>()

            val project = originalClass.project

            if (targetParent is KtElement) {
                val targetSibling = originalClass.parentsWithSelf.first { it.parent == targetParent } as KtElement
                targetSibling.getResolutionScope()
                    .findClassifier(Name.identifier(newClassName), NoLookupLocation.FROM_IDE)
                    ?.let { DescriptorToSourceUtilsIde.getAnyDeclaration(project, it) }
                    ?.let {
                        conflicts.putValue(
                            it,
                            KotlinBundle.message(
                                "text.class.0.already.exists.in.the.target.scope",
                                newClassName
                            )
                        )
                    }
            }

            val elementsToMove = getElementsToMove(memberInfos, originalClass, isExtractInterface).keys

            val moveTarget = if (targetParent is PsiDirectory) {
                val targetPackage = targetParent.getPackage() ?: return conflicts
                KotlinMoveTarget.DeferredFile(FqName(targetPackage.qualifiedName), targetParent.virtualFile)
            } else {
                KotlinMoveTarget.ExistingElement(targetParent as KtElement)
            }
            val conflictChecker = KotlinMoveConflictCheckerInfo(
                project,
                elementsToMove - memberInfos.asSequence().filter { it.isToAbstract }.mapNotNull { it.member }.toSet(),
                moveTarget,
                originalClass,
            )

            project.runSynchronouslyWithProgress(RefactoringBundle.message("detecting.possible.conflicts"), true) {
                runReadAction {
                    val usages = LinkedHashSet<UsageInfo>()
                    for (element in elementsToMove) {
                        ReferencesSearch.search(element).asIterable().mapTo(usages) { MoveRenameUsageInfo(it, element) }
                        if (element is KtCallableDeclaration) {
                            element.toLightMethods().flatMapTo(usages) {
                                MethodReferencesSearch.search(it).asIterable().map { reference -> MoveRenameUsageInfo(reference, element) }
                            }
                        }
                    }
                    conflicts.putAllValues(checkAllConflicts(conflictChecker, usages, LinkedHashSet()))
                    if (targetParent is PsiDirectory) {
                        ExtractSuperClassUtil.checkSuperAccessible(targetParent, conflicts, originalClass.toLightClass())
                    }

                    checkVisibilityInAbstractedMembers(memberInfos, originalClass.getResolutionFacade(), conflicts)
                }
            }

            return conflicts
        }
    }

    private val project = extractInfo.originalClass.project
    private val psiFactory = KtPsiFactory(project)
    private val typeParameters = LinkedHashSet<KtTypeParameter>()

    private val bindingContext = extractInfo.originalClass.analyze(BodyResolveMode.PARTIAL)

    private fun collectTypeParameters(refTarget: PsiElement?) {
        if (refTarget is KtTypeParameter && refTarget.getStrictParentOfType<KtTypeParameterListOwner>() == extractInfo.originalClass) {
            typeParameters += refTarget
            refTarget.accept(
                object : KtTreeVisitorVoid() {
                    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                        (expression.mainReference.resolve() as? KtTypeParameter)?.let { typeParameters += it }
                    }
                }
            )
        }
    }

    private fun analyzeContext() {
        val visitor = object : KtTreeVisitorVoid() {
            override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                val refTarget = expression.mainReference.resolve()
                collectTypeParameters(refTarget)
            }
        }
        getElementsToMove(extractInfo.memberInfos, extractInfo.originalClass, extractInfo.isInterface)
            .asSequence()
            .flatMap {
                val (element, info) = it
                info?.getChildrenToAnalyze()?.asSequence() ?: sequenceOf(element)
            }
            .forEach { it.accept(visitor) }
    }

    private fun createClass(superClassEntry: KtSuperTypeListEntry?): KtClass? {
        val targetParent = extractInfo.targetParent
        val newClassName = extractInfo.newClassName.quoteIfNeeded()
        val originalClass = extractInfo.originalClass

        val kind = if (extractInfo.isInterface) "interface" else "class"
        val prototype = psiFactory.createClass("$kind $newClassName")
        val newClass = if (targetParent is PsiDirectory) {
            val file = targetParent.findFile(extractInfo.targetFileName) ?: run {
                val template = FileTemplateManager.getInstance(project).getInternalTemplate("Kotlin File")
                createKotlinFileFromTemplate(extractInfo.targetFileName, template, targetParent) ?: return null
            }
            file.add(prototype) as KtClass
        } else {
            val targetSibling = originalClass.parentsWithSelf.first { it.parent == targetParent }
            insertDeclaration(prototype, targetSibling)
        }

        val shouldBeAbstract = extractInfo.memberInfos.any { it.isToAbstract }
        if (!extractInfo.isInterface) {
            newClass.addModifier(if (shouldBeAbstract) KtTokens.ABSTRACT_KEYWORD else KtTokens.OPEN_KEYWORD)
        }

        if (typeParameters.isNotEmpty()) {
            val typeParameterListText = typeParameters.sortedBy { it.startOffset }.joinToString(prefix = "<", postfix = ">") { it.text }
            newClass.addAfter(psiFactory.createTypeParameterList(typeParameterListText), newClass.nameIdentifier)
        }

        val targetPackageFqName = (targetParent as? PsiDirectory)?.getFqNameWithImplicitPrefix()?.quoteSegmentsIfNeeded()

        val superTypeText = buildString {
            if (!targetPackageFqName.isNullOrEmpty()) {
                append(targetPackageFqName).append('.')
            }
            append(newClassName)
            if (typeParameters.isNotEmpty()) {
                append(typeParameters.sortedBy { it.startOffset }.map { it.name }.joinToString(prefix = "<", postfix = ">"))
            }
        }
        val needSuperCall = !extractInfo.isInterface
                && (superClassEntry is KtSuperTypeCallEntry
                || originalClass.hasPrimaryConstructor()
                || originalClass.secondaryConstructors.isEmpty())
        val newSuperTypeListEntry = if (needSuperCall) {
            psiFactory.createSuperTypeCallEntry("$superTypeText()")
        } else {
            psiFactory.createSuperTypeEntry(superTypeText)
        }
        if (superClassEntry != null) {
            val qualifiedTypeRefText = bindingContext[BindingContext.TYPE, superClassEntry.typeReference]?.let {
                IdeDescriptorRenderers.SOURCE_CODE.renderType(it)
            }
            val superClassEntryToAdd = if (qualifiedTypeRefText != null) {
                superClassEntry.copied().apply { typeReference?.replace(psiFactory.createType(qualifiedTypeRefText)) }
            } else superClassEntry
            newClass.addSuperTypeListEntry(superClassEntryToAdd)
            ShortenReferences.DEFAULT.process(superClassEntry.replaced(newSuperTypeListEntry))
        } else {
            ShortenReferences.DEFAULT.process(originalClass.addSuperTypeListEntry(newSuperTypeListEntry))
        }

        ShortenReferences.DEFAULT.process(newClass)

        return newClass
    }

    fun performRefactoring() {
        val originalClass = extractInfo.originalClass

        val handler = if (extractInfo.isInterface) KotlinExtractInterfaceHandler else KotlinExtractSuperclassHandler
        handler.getErrorMessage(originalClass)?.let { throw CommonRefactoringUtil.RefactoringErrorHintException(it) }

        val superClassEntry = if (!extractInfo.isInterface) {
            val originalClassDescriptor = originalClass.unsafeResolveToDescriptor() as ClassDescriptor
            val superClassDescriptor = originalClassDescriptor.getSuperClassNotAny()
            originalClass.superTypeListEntries.firstOrNull {
                bindingContext[BindingContext.TYPE, it.typeReference]?.constructor?.declarationDescriptor == superClassDescriptor
            }
        } else null

        project.runSynchronouslyWithProgress(RefactoringBundle.message("progress.text"), true) { runReadAction { analyzeContext() } }

        project.executeWriteCommand(KotlinExtractSuperclassHandler.REFACTORING_NAME) {
            val newClass = createClass(superClassEntry) ?: return@executeWriteCommand

            val subClass = extractInfo.originalClass.toLightClass() ?: return@executeWriteCommand
            val superClass = newClass.toLightClass() ?: return@executeWriteCommand

            PullUpProcessor(
                subClass,
                superClass,
                extractInfo.memberInfos.mapNotNull { it.toJavaMemberInfo() }.toTypedArray(),
                extractInfo.docPolicy
            ).moveMembersToBase()

            performDelayedRefactoringRequests(project)
        }
    }
}
