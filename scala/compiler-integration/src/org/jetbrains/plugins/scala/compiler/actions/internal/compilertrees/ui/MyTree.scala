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
  showEmptyPhasesProperty: GraphProperty[java.lang.Boolean],
  lastSelectedPhaseProperty: GraphProperty[String]
) extends Tree {

  locally {
    //Set tree properties
    //don't need root node, just show phases at the top level
    setRootVisible(false)
    setShowsRootHandles(false)
    setCellRenderer(new MyNodeRenderer)
    //don't allow selecting multiple nodes, it makes no sense
    getSelectionModel.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION)

    //Register property propagation
    showEmptyPhasesProperty.afterPropagation(() => {
      updateTreeModel()
      kotlin.Unit.INSTANCE
    })

    updateTreeModel()
  }

  private def updateTreeModel(): Unit = {
    val showEmptyPhases = showEmptyPhasesProperty.get()
    val model = new MyTreeModel(compilerTrees, showEmptyPhases)
    setModel(model)

    //Remove old listeners
    getTreeSelectionListeners.foreach(this.removeTreeSelectionListener)

    //Change editor text to the tree corresponding to the compiler phase
    addTreeSelectionListener((e: TreeSelectionEvent) => {
      val selectedLeafNode = e.getPath.getPath.last.asInstanceOf[DefaultMutableTreeNode]
      val nodeDescriptor = selectedLeafNode.getUserObject.asInstanceOf[MyNodeDescriptor]
      val selectedPhase = nodeDescriptor.phase

      val phaseWithTree = model.phasesWithTrees.find(_.phase == selectedPhase)
      phaseWithTree.foreach(onPhaseSelected)

      lastSelectedPhaseProperty.set(selectedPhase)
    })

    updateSelectedNode()
  }

  private def updateSelectedNode(): Unit = {
    val lastSelectedPhase = lastSelectedPhaseProperty.get()
    val selectRowIdx =
      if (lastSelectedPhase == null) 0
      else getModel.asInstanceOf[MyTreeModel].phasesWithTrees.indexWhere(_.phase == lastSelectedPhase).max(0)

    this.setSelectionRow(selectRowIdx)
  }
}