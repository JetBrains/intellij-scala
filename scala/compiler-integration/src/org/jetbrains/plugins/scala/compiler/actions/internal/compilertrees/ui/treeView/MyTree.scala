package org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.ui.treeView

import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.ui.AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED
import com.intellij.ui.ClientProperty
import com.intellij.ui.treeStructure.Tree
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.PhaseWithTreeText
import org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.ui.TreeDisplayOptions

import javax.swing.event.TreeSelectionEvent
import javax.swing.plaf.basic.BasicTreeUI
import javax.swing.tree.{DefaultMutableTreeNode, TreeSelectionModel}

private[ui] class MyTree(
  onPhaseSelected: PhaseWithTreeText => Unit,
  lastSelectedPhaseProperty: GraphProperty[String],
  initialDisplayOptions: TreeDisplayOptions
) extends Tree {

  // Create model once and reuse it
  private val myModel: MyTreeModel = new MyTreeModel(initialDisplayOptions)
  private var loadingVisible: Boolean = true

  locally {
    //Set tree properties
    //don't need the root node, just show phases at the top level
    setRootVisible(false)
    setShowsRootHandles(false)

    // Don't add any extra phantom row in the bottom
    setAdditionalRowsCount(0)

    // Ensure no extra space added to the nodes
    setUI(new BasicTreeUI {
      override def getLeftChildIndent: Int = 0
      override def getRightChildIndent: Int = 0
    })

    // Enable the animated icon rendering in lists (otherwise it will be rendered statically)
    // Needed to render the loading progress node in the end of the tree
    ClientProperty.put(this, ANIMATION_IN_RENDERER_ALLOWED, true: java.lang.Boolean)

    setCellRenderer(new MyNodeRenderer)
    //don't allow selecting multiple nodes, it makes no sense
    getSelectionModel.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION)

    // Set model once, the visibility of nodes in the model will be updated by modifying the mutable state of the modal.
    // This is done for performance reasons, to make sure that changing node visibility filters is fast
    setModel(myModel)

    //Change editor text to the tree corresponding to the selected phase
    addTreeSelectionListener((e: TreeSelectionEvent) => {
      e.getPath.getPath.lastOption match {
        case Some(node: DefaultMutableTreeNode) =>
          node.getUserObject match {
            case nodeDescriptor: PhaseTreeNode.Descriptor =>
              val selectedPhase = nodeDescriptor.phase.phaseName
              val phaseWithTree = myModel.visiblePhasesWithTrees.find(_.phaseName == selectedPhase)
              phaseWithTree.foreach(onPhaseSelected)
              lastSelectedPhaseProperty.set(selectedPhase)
            case _ => //shouldn't happen
          }
        case _ => //shouldn't happen
      }
    })

    // Apply initial filters
    updateSelectedNode()
  }

  def updateDisplayOptions(options: TreeDisplayOptions): Unit = {
    myModel.updateDisplayOptions(options)

    // Update selection to nearest visible node if current selection is now hidden
    updateSelectedNode()
  }

  def addPhase(phase: PhaseWithTreeText): Unit = {
    myModel.addPhase(phase)
    updateSelectedNode()
    revalidate()
    repaint()
  }

  def setLoadingVisible(visible: Boolean): Unit = {
    myModel.setLoadingVisible(visible)
    loadingVisible = visible
    updateSelectedNode()
    revalidate()
    repaint()
  }

  private def updateSelectedNode(): Unit = {
    val selectRowIdx = calculateSelectedRowIndex
    this.setSelectionRow(selectRowIdx)
  }

  private def calculateSelectedRowIndex: Int = {
    val lastSelectedPhase = lastSelectedPhaseProperty.get()
    val visiblePhases = myModel.visiblePhasesWithTrees
    visiblePhases.indexWhere(_.phaseName == lastSelectedPhase).max(0)
  }

  @TestOnly
  def phasesFromModelForTests: Seq[PhaseWithTreeText] = myModel.phasesWithTrees
}
