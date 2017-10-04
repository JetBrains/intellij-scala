package org.jetbrains.plugins.scala.annotator.intention.sbt.ui

import java.awt.{BorderLayout, Component, Dimension}
import javax.swing.event.{TreeModelListener, TreeSelectionEvent, TreeSelectionListener}
import javax.swing.tree.{TreeCellRenderer, TreeModel, TreePath, TreeSelectionModel}
import javax.swing.{JPanel, JTree, ScrollPaneConstants}

import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.editor.markup.{HighlighterLayer, HighlighterTargetArea}
import com.intellij.openapi.editor.{Editor, EditorFactory, LogicalPosition, ScrollType}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.ui.treeStructure.Tree
import com.intellij.ui.{ScrollPaneFactory, SimpleColoredComponent, SimpleTextAttributes}
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.annotator.intention.sbt.{AddSbtDependencyUtils, DependencyPlaceInfo}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
  * Created by afonichkin on 7/19/17.
  */
class SbtPossiblePlacesPanel(project: Project, wizard: SbtArtifactSearchWizard, fileLines: Seq[DependencyPlaceInfo]) extends JPanel {

  private val EDITOR_TOP_MARGIN = 7

  private val myResultList: Tree = new Tree()
  private val myLayout = new BorderLayout()

  private var myCurFileLine: DependencyPlaceInfo = _
  private var myCurEditor: Editor = _

  var canGoNext: Boolean = false


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
            case fileLine: DependencyPlaceInfo if myCurFileLine != fileLine =>
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

  private def updateEditor(): Unit = {
    if (myCurFileLine == null) {
      remove(myLayout.getLayoutComponent(BorderLayout.SOUTH))
      updateUI()
    } else for (editor <- createEditor(myCurFileLine.path)) {
      val editorHighlighter = EditorHighlighterFactory.getInstance.createEditorHighlighter(project, ScalaFileType.INSTANCE)
      editor.asInstanceOf[EditorEx].setHighlighter(editorHighlighter)
      editor.getCaretModel.moveToOffset(myCurFileLine.offset)
      val scrollingModel = editor.getScrollingModel
      scrollingModel.scrollToCaret(ScrollType.CENTER)
      val oldPos = editor.offsetToLogicalPosition(myCurFileLine.offset)
      scrollingModel.scrollTo(new LogicalPosition(math.max(1, oldPos.line - EDITOR_TOP_MARGIN), oldPos.column), ScrollType.CENTER)

      releaseEditor()
      myCurEditor = editor

      add(editor.getComponent, BorderLayout.SOUTH)
      updateUI()
    }
  }

  def releaseEditor(): Unit = {
    val prevSouthComponent = myLayout.getLayoutComponent(BorderLayout.SOUTH)
    if (prevSouthComponent != null)
      remove(prevSouthComponent)

    if (myCurEditor != null)
      EditorFactory.getInstance.releaseEditor(myCurEditor)
  }

  private def createEditor(path: String): Option[Editor] = {
    val document = FileDocumentManager.getInstance.getDocument(project.getBaseDir.findFileByRelativePath(path))
    val tmpFile = ScalaPsiElementFactory.createScalaFileFromText(document.getText)(project)
    var tmpElement = tmpFile.findElementAt(myCurFileLine.element.getTextOffset)
    while (tmpElement.getTextRange != myCurFileLine.element.getTextRange)
      tmpElement = tmpElement.getParent


    val viewer = EditorFactory.getInstance.createViewer(
      EditorFactory.getInstance().createDocument(tmpFile.getText)
    )

    val scheme = viewer.getColorsScheme
    val attributes = scheme.getAttributes(CodeInsightColors.MATCHED_BRACE_ATTRIBUTES)

    for {dep: PsiElement <- AddSbtDependencyUtils.addDependency(tmpElement, wizard.resultArtifact.get)(project)} yield {

      viewer.getMarkupModel.addRangeHighlighter(dep.getTextRange.getStartOffset, dep.getTextRange.getEndOffset, HighlighterLayer.SELECTION, attributes, HighlighterTargetArea.EXACT_RANGE)

      viewer.getComponent.setPreferredSize(new Dimension(1600, 500))
      viewer.getComponent.updateUI()

      viewer
    }
  }

  def getResult: Option[DependencyPlaceInfo] =
    myResultList.getSelectionPaths.collectFirst {
      case path if path.getLastPathComponent.isInstanceOf[DependencyPlaceInfo] =>
        path.getLastPathComponent.asInstanceOf[DependencyPlaceInfo]
    }

  private class MyTreeModel(elements: Seq[DependencyPlaceInfo]) extends TreeModel {
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
        case info: DependencyPlaceInfo =>
          myComponent.setIcon(AllIcons.Nodes.PpFile)
          myComponent.append(info.path + ":", SimpleTextAttributes.REGULAR_ATTRIBUTES)
          myComponent.append(info.line.toString, getGrayAttributes(selected))
          if (info.affectedProjects.nonEmpty)
            myComponent.append(" (" + info.affectedProjects.map(_.toString).mkString(", ") + ")", SimpleTextAttributes.REGULAR_ATTRIBUTES)
        case _ =>
      }

      this
    }

    private def getGrayAttributes(selected: Boolean): SimpleTextAttributes =
      if (!selected) SimpleTextAttributes.GRAY_ATTRIBUTES else SimpleTextAttributes.REGULAR_ATTRIBUTES
  }

}