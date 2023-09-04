package org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.ui

import com.intellij.ide.projectView.PresentationData
import com.intellij.ui.SimpleTextAttributes

import java.awt.Color

class MyNodeRenderer extends com.intellij.ide.util.treeView.NodeRenderer {
  override def getSimpleTextAttributes(presentation: PresentationData, color: Color, node: Any): SimpleTextAttributes = {
    val descriptor: MyNodeDescriptor = node.asInstanceOf[MyNodeDescriptor]

    if (descriptor.hasEmptyTree)
      SimpleTextAttributes.GRAY_ATTRIBUTES
    else
      super.getSimpleTextAttributes(presentation, color, node)
  }
}
