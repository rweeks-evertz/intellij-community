// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.shared.widget.popup

import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.git.shared.widget.tree.GitBranchesTreeRenderer
import org.jetbrains.annotations.ApiStatus
import javax.swing.JTree

@ApiStatus.Internal
class GitBranchesTreePopupMinimalRenderer(step: GitBranchesTreePopupStepBase) :
  GitBranchesTreeRenderer(step, favoriteToggleOnClickSupported = false) {

  override val mainPanel: BorderLayoutPanel =
    JBUI.Panels.simplePanel(mainTextComponent).addToLeft(mainIconComponent).andTransparent()

  override fun configureTreeCellComponent(
    tree: JTree,
    userObject: Any?,
    value: Any?,
    selected: Boolean,
    expanded: Boolean,
    leaf: Boolean,
    row: Int,
    hasFocus: Boolean,
  ) {
  }
}