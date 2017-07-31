package org.jetbrains.plugins.scala.annotator.intention.sbt.ui

import java.awt.{BorderLayout, Component, Dimension}
import javax.swing.event.{TreeModelListener, TreeSelectionEvent, TreeSelectionListener}
import javax.swing.tree.{TreeCellRenderer, TreeModel, TreePath, TreeSelectionModel}
import javax.swing.{JPanel, JTree, ScrollPaneConstants}

import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.editor.{Editor, EditorFactory, LogicalPosition, ScrollType}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.ui.treeStructure.Tree
import com.intellij.ui.{ScrollPaneFactory, SimpleColoredComponent, SimpleTextAttributes}
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.annotator.intention.sbt.FileLine

/**
  * Created by user on 7/19/17.
  */
class SbtPossiblePlacesPanel(project: Project, wizard: SbtArtifactSearchWizard, fileLines: Seq[FileLine]) extends JPanel {
  val myResultList: Tree = new Tree()
  var myCurFileLine: FileLine = _

  var canGoNext: Boolean = false

  val myLayout = new BorderLayout()

  init()

  def init(): Unit = {
    myResultList.setExpandableItemsEnabled(false)

    if (fileLines.isEmpty)
      myResultList.getEmptyText.setText("Nothing to show")
    else
      myResultList.getEmptyText.setText("Loading...")

    myResultList.setRootVisible(false)
    myResultList.setShowsRootHandles(true)

    myResultList.setModel(new MyTreeModel(fileLines))
    myResultList.getSelectionModel.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION)

    setLayout(myLayout)

    val pane = ScrollPaneFactory.createScrollPane(myResultList)
    pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS) // Don't remove this line.

    add(pane, BorderLayout.CENTER)

    myResultList.setCellRenderer(new MyCellRenderer)

    myResultList.addTreeSelectionListener(new TreeSelectionListener {
      override def valueChanged(treeSelectionEvent: TreeSelectionEvent): Unit = {
        val path = treeSelectionEvent.getNewLeadSelectionPath
        if (path == null) {
          canGoNext = false
          myCurFileLine = null
          updateEditor()
        } else {
          path.getLastPathComponent match {
            case fileLine: FileLine if myCurFileLine != fileLine =>
              canGoNext = true
              myCurFileLine = fileLine
              updateEditor()
            case _ =>
          }
        }
        wizard.updateWizardButtons()
      }
    })
  }

  def updateEditor(): Unit = {
    if (myCurFileLine == null) {
      remove(myLayout.getLayoutComponent(BorderLayout.SOUTH))
      updateUI()
    } else {
      // TODO: make a map to store editors
      val editor = createEditor(myCurFileLine.path)
      val editorHighlighter = EditorHighlighterFactory.getInstance.createEditorHighlighter(project, ScalaFileType.INSTANCE)
      editor.asInstanceOf[EditorEx].setHighlighter(editorHighlighter)
      editor.getCaretModel.moveToOffset(myCurFileLine.line)
      editor.getScrollingModel.scrollToCaret(ScrollType.CENTER)

      val prevSouthComponent = myLayout.getLayoutComponent(BorderLayout.SOUTH)
      if (prevSouthComponent != null)
        remove(prevSouthComponent)

      add(editor.getComponent, BorderLayout.SOUTH)
      updateUI()
    }
  }

  def createEditor(path: String): Editor = {
    val viewer = EditorFactory.getInstance.createViewer(
      FileDocumentManager.getInstance.getDocument(project.getBaseDir.findFileByRelativePath(path))
    )
    viewer.getComponent.setPreferredSize(new Dimension(600, 400))
    viewer.getComponent.updateUI()

    viewer
  }

  def getResult: Option[FileLine] = {
    for (path: TreePath <- myResultList.getSelectionPaths) {
      path.getLastPathComponent match {
        case info: FileLine => return Some(info)
        case _ =>
      }
    }
    None
  }

  private class MyTreeModel(elements: Seq[FileLine]) extends TreeModel {
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
        case info: FileLine =>
          myComponent.setIcon(AllIcons.Nodes.PpFile)
          myComponent.append(info.path + ":", SimpleTextAttributes.REGULAR_ATTRIBUTES)
          myComponent.append(info.line.toString, getGrayAttributes(selected))
        case _ =>
      }

      this
    }

    private def getGrayAttributes(selected: Boolean): SimpleTextAttributes =
      if (!selected) SimpleTextAttributes.GRAY_ATTRIBUTES else SimpleTextAttributes.REGULAR_ATTRIBUTES
  }

}