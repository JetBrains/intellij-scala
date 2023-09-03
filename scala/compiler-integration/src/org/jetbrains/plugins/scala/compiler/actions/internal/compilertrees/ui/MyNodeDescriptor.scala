package org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.ui

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.PresentableNodeDescriptor

private class MyNodeDescriptor(val phase: String, val hasEmptyTree: Boolean)
  extends PresentableNodeDescriptor[String](null, null) {

  override def update(presentation: PresentationData): Unit = {}

  override def getElement: String = phase

  override def toString: String = phase
}