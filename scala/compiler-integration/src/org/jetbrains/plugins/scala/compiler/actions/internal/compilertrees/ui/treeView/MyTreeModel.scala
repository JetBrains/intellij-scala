package org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.ui.treeView

import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.PhaseWithTreeText
import org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.ui.TreeDisplayOptions

import javax.swing.tree.{DefaultMutableTreeNode, DefaultTreeModel}
import scala.collection.mutable
import scala.jdk.CollectionConverters._

private class MyTreeModel(
  initialDisplayOptions: TreeDisplayOptions
) extends DefaultTreeModel(new DefaultMutableTreeNode("<ignored root node>")) {

  // Contains all collected phases in the original collected order (it's different from the order used in the tree on UI)
  private val allCollectedPhasesWithTrees: mutable.ArrayBuffer[PhaseWithTreeText] =
    mutable.ArrayBuffer.empty

  // Map from phase name to node for a quick lookup (mutable for dynamic updates)
  private val phaseToTreeNode: mutable.Map[String, PhaseTreeNode] =
    mutable.Map[String, PhaseTreeNode]()

  private val loadingNode: LoadingProgressTreeNode = new LoadingProgressTreeNode
  private var loadingVisible: Boolean = true

  // Current display options (mutable for performance reasons)
  private var currentDisplayOptions: TreeDisplayOptions = initialDisplayOptions

  locally {
    rebuildTree()
  }

  def updateDisplayOptions(options: TreeDisplayOptions): Unit = {
    currentDisplayOptions = options

    rebuildTree()
  }

  def visiblePhasesWithTrees: Seq[PhaseWithTreeText] =
    getSortedPhases.filter(isPhaseVisible(_, currentDisplayOptions))

  /**
   * Returns phases sorted by kind: Regular phases first, then Tasty, then UncapturedOutput
   */
  private def getSortedPhases: Seq[PhaseWithTreeText] = {
    import PhaseWithTreeText.PhaseKind

    val (regular, tasty, uncaptured) = allCollectedPhasesWithTrees.foldLeft(
      (Seq.empty[PhaseWithTreeText], Seq.empty[PhaseWithTreeText], Seq.empty[PhaseWithTreeText])
    ) { case ((reg, tst, unc), phase) =>
      phase.phaseKind match {
        case PhaseKind.Regular => (reg :+ phase, tst, unc)
        case PhaseKind.TastyOutput => (reg, tst :+ phase, unc)
        case PhaseKind.UncapturedOutput => (reg, tst, unc :+ phase)
      }
    }

    regular ++ tasty ++ uncaptured
  }

  def addPhase(phase: PhaseWithTreeText): Unit = {
    allCollectedPhasesWithTrees += phase

    val node = new PhaseTreeNode(phase)
    phaseToTreeNode(phase.phaseName) = node

    rebuildTree()
  }

  /**
   * Rebuilds the entire tree structure with phases in sorted order.
   * Sorting: Regular phases first, then Tasty, then UncapturedOutput
   */
  private def rebuildTree(): Unit = {
    val rootNode = root.asInstanceOf[DefaultMutableTreeNode]

    // Build list of nodes that should be visible in sorted order
    val visibleNodes: Seq[PhaseTreeNode] =
      getSortedPhases.flatMap { phase =>
        if (isPhaseVisible(phase, currentDisplayOptions))
          phaseToTreeNode.get(phase.phaseName)
        else
          None
      }

    // Remove all children from the root
    rootNode.removeAllChildren()

    // Add back only visible nodes in sorted order
    visibleNodes.foreach { node =>
      rootNode.add(node)
    }

    if (loadingVisible) {
      rootNode.add(loadingNode)
    }

    // Notify listeners that the tree structure changed
    reload()
  }

  private def isPhaseVisible(
    phase: PhaseWithTreeText,
    displayOptions: TreeDisplayOptions
  ): Boolean = {
    import PhaseWithTreeText.PhaseKind

    val includeEmptyPhases = displayOptions.showEmptyPhases || phase.phaseText.nonEmpty

    val includeByKind = phase.phaseKind match {
      case PhaseKind.Regular => true
      case PhaseKind.TastyOutput => displayOptions.showTasty
      case PhaseKind.UncapturedOutput => displayOptions.showUncapturedMessages
    }

    includeEmptyPhases && includeByKind
  }

  @TestOnly
  def phasesWithTrees: Seq[PhaseWithTreeText] = {
    val rootNode = getRoot.asInstanceOf[DefaultMutableTreeNode]
    rootNode.children().asScala.toSeq.collect {
      case node: PhaseTreeNode => node.phase
    }
  }

  def setLoadingVisible(visible: Boolean): Unit = {
    if (loadingVisible != visible) {
      loadingVisible = visible
      rebuildTree()
    }
  }
}
