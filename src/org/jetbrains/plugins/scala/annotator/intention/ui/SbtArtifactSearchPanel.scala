package org.jetbrains.plugins.scala.annotator.intention.ui

import java.awt.{BorderLayout, Component}
import javax.swing._
import javax.swing.event.{TreeModelListener, TreeSelectionEvent, TreeSelectionListener}
import javax.swing.tree.{TreeCellRenderer, TreeModel, TreePath, TreeSelectionModel}

import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.EditorFactory
import com.intellij.psi.PsiFileFactory
import com.intellij.ui.treeStructure.Tree
import com.intellij.ui.{ScrollPaneFactory, SimpleColoredComponent, SimpleTextAttributes}
import com.intellij.util.ui.UIUtil
import org.jetbrains.sbt.resolvers.ArtifactInfo

/**
  * Created by user on 7/13/17.
  */
class SbtArtifactSearchPanel(dialog: SbtArtifactSearchDialog, artifactInfoSet: Set[ArtifactInfo]) extends JPanel {
  val myResultList: Tree = new Tree()

  init()

  def init(): Unit = {
    myResultList.setExpandableItemsEnabled(false)
    myResultList.getEmptyText.setText("Loading...")
    myResultList.setRootVisible(false)
    myResultList.setShowsRootHandles(true)

    myResultList.setModel(new MyTreeModel(artifactInfoSet.toSeq))
    myResultList.getSelectionModel.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION)

    setLayout(new BorderLayout())

    val pane = ScrollPaneFactory.createScrollPane(myResultList)
    pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS) // Don't remove this line.

    add(pane, BorderLayout.CENTER)

    myResultList.setCellRenderer(new MyCellRenderer)

    myResultList.addTreeSelectionListener(new TreeSelectionListener {
      override def valueChanged(treeSelectionEvent: TreeSelectionEvent): Unit = {
        dialog.setOKActionEnabled(treeSelectionEvent.getNewLeadSelectionPath != null)
      }
    })
  }

  def getResult(): Option[ArtifactInfo] = {
    for (path: TreePath <- myResultList.getSelectionPaths) {
      path.getLastPathComponent match {
        case info: ArtifactInfo => return Some(info)
        case _ =>
      }
    }
    None
  }

  private class MyTreeModel(elements: Seq[ArtifactInfo]) extends TreeModel {
    override def getIndexOfChild(parent: scala.Any, child: scala.Any): Int = elements.indexOf(child)

    override def valueForPathChanged(treePath: TreePath, o: scala.Any): Unit = {}

    override def getChild(parent: scala.Any, index: Int): AnyRef = elements(index)

    override def addTreeModelListener(treeModelListener: TreeModelListener): Unit = {}

    override def isLeaf(node: scala.Any): Boolean = node != elements

    override def removeTreeModelListener(treeModelListener: TreeModelListener): Unit = {}

    override def getChildCount(o: scala.Any): Int = elements.size

    override def getRoot: AnyRef = elements
  }

  private class MyCellRenderer extends JPanel with TreeCellRenderer {
    val myComponent: SimpleColoredComponent = new SimpleColoredComponent

    init()

    def init(): Unit = {
      myComponent.setOpaque(false)
      myComponent.setIconOpaque(false)
      add(myComponent)
    }

    override def getTreeCellRendererComponent(tree: JTree, value: scala.Any, selected: Boolean, expanded: Boolean,
                                              leaf: Boolean, row: Int, hasFocus: Boolean): Component = {
      myComponent.clear()

      setBackground(if (selected) UIUtil.getTreeSelectionBackground else tree.getBackground)

      value match {
        case info: ArtifactInfo =>
          myComponent.setIcon(AllIcons.Nodes.PpLib)
          myComponent.append(info.groupId + ":", getGrayAttributes(selected))
          myComponent.append(info.artifactId, SimpleTextAttributes.REGULAR_ATTRIBUTES)
          myComponent.append(":" + info.version, getGrayAttributes(selected))
        case _ =>
      }

      this
    }

    private def getGrayAttributes(selected: Boolean): SimpleTextAttributes =
      if (!selected) SimpleTextAttributes.GRAY_ATTRIBUTES else SimpleTextAttributes.REGULAR_ATTRIBUTES
  }
}
