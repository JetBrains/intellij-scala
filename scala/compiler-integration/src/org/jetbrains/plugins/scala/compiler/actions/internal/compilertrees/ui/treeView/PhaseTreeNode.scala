package org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.ui.treeView

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.PresentableNodeDescriptor
import org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.PhaseWithTreeText

import javax.swing.tree.DefaultMutableTreeNode

private final class PhaseTreeNode(val phase: PhaseWithTreeText)
  extends DefaultMutableTreeNode(new PhaseTreeNode.Descriptor(phase))

private object PhaseTreeNode {
  final class Descriptor(val phase: PhaseWithTreeText)
    extends PresentableNodeDescriptor[String](null, null) {

    override def update(presentation: PresentationData): Unit = {}

    override def getElement: String = phase.phaseName

    override def toString: String = phase.phaseName
  }
}
