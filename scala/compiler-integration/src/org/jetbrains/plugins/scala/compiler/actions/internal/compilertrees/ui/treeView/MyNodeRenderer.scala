package org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.ui.treeView

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.{NodeDescriptor, NodeRenderer}
import com.intellij.ui.SimpleTextAttributes

import java.awt.Color
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

private class MyNodeRenderer extends NodeRenderer {

  override def getSimpleTextAttributes(presentation: PresentationData, color: Color, node: Any): SimpleTextAttributes = {
    node match {
      case descriptor: PhaseTreeNode.Descriptor =>
        val hasEmptyTree = descriptor.phase.phaseText.isEmpty
        if (hasEmptyTree)
          SimpleTextAttributes.GRAY_ATTRIBUTES
        else
          super.getSimpleTextAttributes(presentation, color, node)
      case _: LoadingProgressTreeNode.Descriptor =>
        SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES
      case _ =>
        super.getSimpleTextAttributes(presentation, color, node)
    }
  }

  override def customizeCellRenderer(tree: JTree, value: Any, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean): Unit = {
    super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus)

    setIconFromDescriptorIfExists(value)
  }

  private def setIconFromDescriptorIfExists(value: Any): Unit = {
    val userObject = value match {
      case node: DefaultMutableTreeNode => node.getUserObject
      case other => other
    }

    userObject match {
      case descriptor: NodeDescriptor[_] =>
        setIcon(descriptor.getIcon)
      case _ =>
    }
  }
}
