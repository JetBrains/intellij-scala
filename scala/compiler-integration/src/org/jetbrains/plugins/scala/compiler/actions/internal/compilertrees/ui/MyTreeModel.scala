package org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.ui

import org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.CompilerTrees
import org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.CompilerTrees.PhaseWithTreeText

import javax.swing.tree.{DefaultMutableTreeNode, DefaultTreeModel}

private class MyTreeModel(
  compilerTrees: CompilerTrees
) extends DefaultTreeModel(new DefaultMutableTreeNode("")) {

  private val phasesWithTrees: Seq[PhaseWithTreeText] = {
    val allPhasesMetaPhase = PhaseWithTreeText("== All phases ==", compilerTrees.allPhasesTextConcatenated)
    allPhasesMetaPhase +: compilerTrees.phasesTrees
  }

  // Current display options (mutable for performance reasons)
  private var currentDisplayOptions: TreeDisplayOptions = TreeDisplayOptions.Default

  def visiblePhasesWithTrees: Seq[PhaseWithTreeText] = {
    phasesWithTrees.filter(isPhaseVisible(_, currentDisplayOptions))
  }

  // Map from phase name to node for a quick lookup
  private val phaseToTreeNode: Map[String, DefaultMutableTreeNode] = {
    val rootNode = root.asInstanceOf[DefaultMutableTreeNode]
    val nodesMap = scala.collection.mutable.Map[String, DefaultMutableTreeNode]()

    phasesWithTrees.foreach { pt =>
      val descriptor = new MyNodeDescriptor(pt.phase, pt.phaseText.isEmpty)
      val node = new DefaultMutableTreeNode(descriptor)
      rootNode.add(node)
      nodesMap(pt.phase) = node
    }

    nodesMap.toMap
  }

  private def isPhaseVisible(
    phase: PhaseWithTreeText,
    displayOptions: TreeDisplayOptions
  ): Boolean = {
    import CompilerTrees.PhaseKind

    val includeEmptyPhases = displayOptions.showEmptyPhases || phase.phaseText.nonEmpty

    val includeByKind = phase.kind match {
      case PhaseKind.Regular => true
      case PhaseKind.TastyOutput => displayOptions.showTasty
      case PhaseKind.UncapturedOutput => displayOptions.showUncapturedMessages
    }

    includeEmptyPhases && includeByKind
  }

  def updateNodesVisibility(options: TreeDisplayOptions): Unit = {
    currentDisplayOptions = options
    updateNodesVisibilityInner(options)
  }

  private def updateNodesVisibilityInner(
    displayOptions: TreeDisplayOptions
  ): Unit = {
    val rootNode = root.asInstanceOf[DefaultMutableTreeNode]

    // Build list of nodes that should be visible in the original order
    val visibleNodes: Seq[DefaultMutableTreeNode] =
      phasesWithTrees.flatMap { phase =>
        if (isPhaseVisible(phase, displayOptions))
          phaseToTreeNode.get(phase.phase)
        else
          None
      }

    // Remove all children from the root
    rootNode.removeAllChildren()

    // Add back only visible nodes in the original order
    visibleNodes.foreach { node =>
      rootNode.add(node)
    }

    // Notify listeners that the tree structure changed
    reload()
  }
}