// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.savedPatches

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.shelf.*
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.VcsBundleTag
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.JBIterable
import com.intellij.util.text.DateFormatUtil
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class ShelfProvider(private val project: Project, parent: Disposable) : SavedPatchesProvider<ShelvedChangeList>, Disposable {
  private val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Shelved Changes Loader", 1)
  private val shelveManager: ShelveChangesManager get() = ShelveChangesManager.getInstance(project)

  override val dataClass: Class<ShelvedChangeList> get() = ShelvedChangeList::class.java
  override val tag: ChangesBrowserNode.Tag = VcsBundleTag("shelf.root.node.title")
  override val applyAction: AnAction get() = ActionManager.getInstance().getAction("Vcs.Shelf.Apply")
  override val popAction: AnAction get() = ActionManager.getInstance().getAction("Vcs.Shelf.Pop")

  init {
    Disposer.register(parent, this)
    project.messageBus.connect(this).subscribe(ShelveChangesManager.SHELF_TOPIC, ShelveChangesManagerListener {
      preloadChanges()
    })
    preloadChanges()
  }

  private fun preloadChanges() {
    BackgroundTaskUtil.submitTask(executor, this) {
      for (list in shelveManager.allLists) {
        ProgressManager.checkCanceled()
        list.loadChangesIfNeeded(project)
      }
    }
  }

  override fun subscribeToPatchesListChanges(disposable: Disposable, listener: () -> Unit) {
    val disposableFlag = Disposer.newCheckedDisposable()
    Disposer.register(disposable, disposableFlag)
    val listener = ShelveChangesManagerListener {
      if (ApplicationManager.getApplication().isDispatchThread) {
        listener()
      }
      else {
        ApplicationManager.getApplication().invokeLater(listener) { disposableFlag.isDisposed }
      }
    }
    project.messageBus.connect(disposable).subscribe(ShelveChangesManager.SHELF_TOPIC, listener)
    listener.shelvedListsChanged()
  }

  private fun mainLists(): List<ShelvedChangeList> {
    return shelveManager.allLists.filter { l -> !l.isDeleted && !l.isRecycled }
  }

  private fun deletedLists() = shelveManager.allLists.filter { l -> l.isDeleted }

  override fun isEmpty() = mainLists().isEmpty() && deletedLists().isEmpty()

  override fun buildPatchesTree(modelBuilder: TreeModelBuilder, showRootNode: Boolean) {
    val shelvesList = mainLists().sortedByDescending { it.date }
    val shelvesRoot = if (showRootNode) SavedPatchesTree.TagWithCounterChangesBrowserNode(tag).also {
      modelBuilder.insertSubtreeRoot(it)
    }
    else {
      modelBuilder.myRoot
    }

    modelBuilder.insertShelves(shelvesRoot, shelvesList)

    val deletedShelvesList = deletedLists().sortedByDescending { it.date }
    if (deletedShelvesList.isNotEmpty()) {
      val deletedShelvesRoot = SavedPatchesTree.TagWithCounterChangesBrowserNode(VcsBundle.message("shelve.recently.deleted.node"),
                                                                                 expandByDefault = false, sortWeight = 20)
      modelBuilder.insertSubtreeRoot(deletedShelvesRoot, shelvesRoot)
      modelBuilder.insertShelves(deletedShelvesRoot, deletedShelvesList)
    }
  }

  private fun TreeModelBuilder.insertShelves(root: ChangesBrowserNode<*>, shelvesList: List<ShelvedChangeList>) {
    for (shelve in shelvesList) {
      insertSubtreeRoot(ShelvedChangeListChangesBrowserNode(ShelfObject(shelve)), root)
    }
  }

  override fun uiDataSnapshot(sink: DataSink, selectedObjects: Iterable<SavedPatchesProvider.PatchObject<*>>) {
    val shelvedLists = JBIterable.from(selectedObjects)
      .map(SavedPatchesProvider.PatchObject<*>::data)
      .filter(dataClass)
      .toList()

    sink[ShelvedChangesViewManager.SHELVED_CHANGELIST_KEY] = shelvedLists.filter { l -> !l.isRecycled && !l.isDeleted }
    sink[ShelvedChangesViewManager.SHELVED_RECYCLED_CHANGELIST_KEY] = shelvedLists.filter { l -> l.isRecycled && !l.isDeleted }
    sink[ShelvedChangesViewManager.SHELVED_DELETED_CHANGELIST_KEY] = shelvedLists.filter { l -> l.isDeleted }
  }

  override fun dispose() {
    executor.shutdown()
    try {
      executor.awaitTermination(10, TimeUnit.MILLISECONDS)
    }
    finally {
    }
  }

  class ShelvedChangeListChangesBrowserNode(private val shelf: ShelfObject) : ChangesBrowserNode<ShelfObject>(shelf) {
    override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
      val listName = shelf.data.description.ifBlank { VcsBundle.message("changes.nodetitle.empty.changelist.name") }
      val attributes = if (shelf.data.isRecycled || shelf.data.isDeleted) {
        SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES
      }
      else {
        SimpleTextAttributes.REGULAR_ATTRIBUTES
      }
      renderer.appendTextWithIssueLinks(listName, attributes)

      renderer.toolTipText = VcsBundle.message("saved.patch.created.on.date.at.time.tooltip", VcsBundle.message("shelf.tooltip.title"),
                                               DateFormatUtil.formatDate(shelf.data.date),
                                               DateFormatUtil.formatTime(shelf.data.date))
    }

    override fun getTextPresentation(): String = shelf.data.toString()
  }

  inner class ShelfObject(override val data: ShelvedChangeList) : SavedPatchesProvider.PatchObject<ShelvedChangeList> {
    override fun cachedChanges(): Collection<SavedPatchesProvider.ChangeObject>? = data.getChangeObjects()

    override fun loadChanges(): CompletableFuture<SavedPatchesProvider.LoadingResult>? {
      val cachedChangeObjects = data.getChangeObjects()
      if (cachedChangeObjects != null) {
        return CompletableFuture.completedFuture(SavedPatchesProvider.LoadingResult.Changes(cachedChangeObjects))
      }
      return BackgroundTaskUtil.submitTask(executor, this@ShelfProvider, Computable {
        try {
          data.loadChangesIfNeeded(project)

          val changesLoadingError = data.changesLoadingError
          if (changesLoadingError != null) {
            return@Computable SavedPatchesProvider.LoadingResult.Error(changesLoadingError)
          }
          return@Computable SavedPatchesProvider.LoadingResult.Changes(data.getChangeObjects()!!)
        }
        catch (throwable: Throwable) {
          return@Computable SavedPatchesProvider.LoadingResult.Error(throwable)
        }
      }).future
    }

    private fun ShelvedChangeList.getChangeObjects(): Set<SavedPatchesProvider.ChangeObject>? {
      val cachedChanges = changes ?: return null
      return cachedChanges.map { MyShelvedWrapper(it, null, this) }
        .union(binaryFiles.map { MyShelvedWrapper(null, it, this) })
    }

    override fun getDiffPreviewTitle(changeName: String?): String {
      return changeName?.let { name ->
        VcsBundle.message("shelve.editor.diff.preview.title", name)
      } ?: VcsBundle.message("shelved.version.name")
    }
  }

  private class MyShelvedWrapper(shelvedChange: ShelvedChange?,
                                 binaryFile: ShelvedBinaryFile?,
                                 changeList: ShelvedChangeList) : ShelvedWrapper(shelvedChange, binaryFile, changeList) {
    override fun getTag(): ChangesBrowserNode.Tag? = null
  }
}