package org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.ui

import org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.CompilerTrees
import org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.CompilerTrees.PhaseWithTreeText

import javax.swing.tree.{DefaultMutableTreeNode, DefaultTreeModel}

private class MyTreeModel(
  compilerTrees: CompilerTrees,
  showEmptyPhases: Boolean
) extends DefaultTreeModel(new DefaultMutableTreeNode("")) {

  //we could use a Map, but the amount of phases is small enough to just use "find" instead of "get"
  val phasesWithTrees: Seq[PhaseWithTreeText] = createPhasesWithTrees

  locally {
    val phasesTreeNodes = phasesWithTrees.map { pt =>
      val descriptor = new MyNodeDescriptor(pt.phase, pt.treeText.isEmpty)
      new DefaultMutableTreeNode(descriptor)
    }
    val rootNode = root.asInstanceOf[DefaultMutableTreeNode]
    phasesTreeNodes.foreach(rootNode.add)
  }

  private def createPhasesWithTrees: Seq[PhaseWithTreeText] = {
    val AllTreesNodeTitle = "== All trees =="
    //Adding extra node with concatenated trees from all phases
    val phasesWithTreesAdjusted: Seq[PhaseWithTreeText] =
      PhaseWithTreeText(AllTreesNodeTitle, compilerTrees.allPhasesTextConcatenated) +: compilerTrees.phasesTrees

    if (showEmptyPhases)
      phasesWithTreesAdjusted
    else
      phasesWithTreesAdjusted.filterNot(_.treeText.isEmpty)
  }
}