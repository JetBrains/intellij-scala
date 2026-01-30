package org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.ui.treeView

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.PresentableNodeDescriptor
import com.intellij.ui.AnimatedIcon

import javax.swing.tree.DefaultMutableTreeNode

private final class LoadingProgressTreeNode
  extends DefaultMutableTreeNode(new LoadingProgressTreeNode.Descriptor)

private object LoadingProgressTreeNode {

  /**
   * Note, extra rendering logic for this node is located in  [[MyNodeRenderer]]
   */
  final class Descriptor extends PresentableNodeDescriptor[String](null, null) {

    this.setIcon(new AnimatedIcon.Default())

    override def getElement: String = "Compiling..."

    override def toString: String = getElement

    override def update(presentation: PresentationData): Unit = {}
  }
}
