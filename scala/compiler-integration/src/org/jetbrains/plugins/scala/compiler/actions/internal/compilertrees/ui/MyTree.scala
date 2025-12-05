package org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.ui

import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.ui.treeStructure.Tree
import org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.CompilerTrees
import org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.CompilerTrees.PhaseWithTreeText

import javax.swing.event.TreeSelectionEvent
import javax.swing.tree.{DefaultMutableTreeNode, TreeSelectionModel}

private class MyTree(
  compilerTrees: CompilerTrees,
  onPhaseSelected: PhaseWithTreeText => Unit,
  lastSelectedPhaseProperty: GraphProperty[String]
) extends Tree {

  // Create model once and reuse it
  private val myModel: MyTreeModel = new MyTreeModel(compilerTrees)

  locally {
    //Set tree properties
    //don't need the root node, just show phases at the top level
    setRootVisible(false)
    setShowsRootHandles(false)
    setCellRenderer(new MyNodeRenderer)
    //don't allow selecting multiple nodes, it makes no sense
    getSelectionModel.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION)

    // Set model once, the visibility of nodes in hte model will be updated by modifying the mutable state of the modal
    // This is done for the performance reasons, to make sure that changing node visibility filters is fast
    setModel(myModel)

    //Change editor text to the tree corresponding to the selected phase
    addTreeSelectionListener((e: TreeSelectionEvent) => {
      val selectedLeafNode = e.getPath.getPath.last.asInstanceOf[DefaultMutableTreeNode]
      val nodeDescriptor = selectedLeafNode.getUserObject.asInstanceOf[MyNodeDescriptor]
      val selectedPhase = nodeDescriptor.phase

      val phaseWithTree = myModel.visiblePhasesWithTrees.find(_.phase == selectedPhase)
      phaseWithTree.foreach(onPhaseSelected)

      lastSelectedPhaseProperty.set(selectedPhase)
    })

    // Apply initial filters
    updateSelectedNode()
  }

  // Public method to update filters - called directly from CompilerTreesDialog
  def updateNodesVisibility(options: TreeDisplayOptions): Unit = {
    // Just update filters - no model rebuild, no PSI reparsing
    myModel.updateNodesVisibility(options)

    // Update selection to nearest visible node if current selection is now hidden
    updateSelectedNode()
  }

  private def updateSelectedNode(): Unit = {
    val selectRowIdx = calculateSelectedRowIndex
    this.setSelectionRow(selectRowIdx)
  }

  private def calculateSelectedRowIndex: Int = {
    val lastSelectedPhase = lastSelectedPhaseProperty.get()
    val visiblePhases = myModel.visiblePhasesWithTrees
    visiblePhases.indexWhere(_.phase == lastSelectedPhase).max(0)
  }
}