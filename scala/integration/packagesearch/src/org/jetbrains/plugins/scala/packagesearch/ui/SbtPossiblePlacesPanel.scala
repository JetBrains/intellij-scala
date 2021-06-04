package org.jetbrains.plugins.scala.packagesearch.ui

import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.openapi.editor.{Editor, EditorFactory, LogicalPosition, ScrollType}
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.editor.markup.{HighlighterLayer, HighlighterTargetArea}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.ui.{CollectionListModel, ColoredListCellRenderer, GuiUtils, ScrollPaneFactory, SimpleTextAttributes}
import com.intellij.ui.components.JBList
import org.jetbrains.plugins.scala.{ScalaFileType, inWriteAction}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.packagesearch.PackageSearchSbtBundle
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.sbt.language
import org.jetbrains.sbt.language.utils.{ArtifactInfo, DependencyOrRepositoryPlaceInfo, SbtDependencyUtils}

import java.awt.BorderLayout
import javax.swing.{JList, JPanel, JSplitPane, ListSelectionModel, ScrollPaneConstants}
import javax.swing.event.ListSelectionEvent
import scala.jdk.CollectionConverters.IterableHasAsJava

private class SbtPossiblePlacesPanel(project: Project, wizard: AddDependencyOrRepositoryPreviewWizard, fileLines: Seq[DependencyOrRepositoryPlaceInfo]) extends JPanel {
  val myResultList: JBList[DependencyOrRepositoryPlaceInfo] = new JBList[DependencyOrRepositoryPlaceInfo]()
  var myCurEditor: Editor = createEditor()

  private val EDITOR_TOP_MARGIN = 7

  init()

  def init(): Unit = {
    setLayout(new BorderLayout())

    val splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT)

    myResultList.setModel(new CollectionListModel[DependencyOrRepositoryPlaceInfo](fileLines.asJavaCollection))
    myResultList.getSelectionModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)

    val scrollPane = ScrollPaneFactory.createScrollPane(myResultList)
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS) // Don't remove this line.
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS)
    splitPane.setContinuousLayout(true)
    splitPane.add(scrollPane)
    splitPane.add(myCurEditor.getComponent)

    add(splitPane, BorderLayout.CENTER)

    GuiUtils.replaceJSplitPaneWithIDEASplitter(splitPane)
    myResultList.setCellRenderer(new PlacesCellRenderer)

    myResultList.addListSelectionListener { (_: ListSelectionEvent) =>
      val place = myResultList.getSelectedValue
      if (place != null) {
        if (myCurEditor == null)
          myCurEditor = createEditor()
        updateEditor(place)
      }
      wizard.updateButtons(true, place != null, false)
    }
  }

  private def updateEditor(myCurFileLine: DependencyOrRepositoryPlaceInfo): Unit = {
    val document    = FileDocumentManager.getInstance.getDocument(project.baseDir.findFileByRelativePath(myCurFileLine.path))
    val tmpFile     = ScalaPsiElementFactory.createScalaFileFromText(document.getText)(project)
    var tmpElement  = tmpFile.findElementAt(myCurFileLine.element.getTextOffset)
    while (tmpElement.getTextRange != myCurFileLine.element.getTextRange) {
      tmpElement = tmpElement.getParent
    }

    var dep: Option[PsiElement] = null
    wizard.elementToAdd match {
      case info: ArtifactInfo =>
        dep = SbtDependencyUtils.addDependency(tmpElement, info)(project)
      case repo: UnifiedDependencyRepository =>
        dep = SbtDependencyUtils.addRepository(tmpElement, repo)(project)
    }

    inWriteAction {
      myCurEditor.getDocument.setText {
        if (dep.isDefined) tmpFile.getText
        else PackageSearchSbtBundle.message("packagesearch.dependency.sbt.could.not.generate.expression.string.to.add")
      }
    }


    myCurEditor.getCaretModel.moveToOffset(myCurFileLine.offset)
    val scrollingModel  = myCurEditor.getScrollingModel
    val oldPos          = myCurEditor.offsetToLogicalPosition(myCurFileLine.offset)
    scrollingModel.scrollTo(new LogicalPosition(math.max(1, oldPos.line - EDITOR_TOP_MARGIN), oldPos.column),
      ScrollType.CENTER)
    val attributes = myCurEditor.getColorsScheme.getAttributes(CodeInsightColors.MATCHED_BRACE_ATTRIBUTES)

    val (startOffset, endOffset) = dep match {
      case Some(elem) =>
        (elem.getTextRange.getStartOffset, elem.getTextRange.getEndOffset)
      case None => (0, 0)
    }
    // Reset all highlighters (if exist)
    myCurEditor.getMarkupModel.removeAllHighlighters()
    myCurEditor.getMarkupModel.addRangeHighlighter(
      startOffset,
      endOffset,
      HighlighterLayer.SELECTION,
      attributes,
      HighlighterTargetArea.EXACT_RANGE
    )
  }

  def releaseEditor(): Unit = {
    if (myCurEditor != null && !myCurEditor.isDisposed) {
      EditorFactory.getInstance.releaseEditor(myCurEditor)
      myCurEditor = null
    }
  }

  private def createEditor(): Editor = {
    val viewer = EditorFactory.getInstance.createViewer(EditorFactory.getInstance().createDocument(""))
    val editorHighlighter = EditorHighlighterFactory.getInstance.createEditorHighlighter(project, ScalaFileType.INSTANCE)
    viewer.asInstanceOf[EditorEx].setHighlighter(editorHighlighter)
    viewer
  }

  private class PlacesCellRenderer extends ColoredListCellRenderer[DependencyOrRepositoryPlaceInfo] {

    //noinspection ReferencePassedToNls,ScalaExtractStringToBundle
    override def customizeCellRenderer(list: JList[_ <: DependencyOrRepositoryPlaceInfo],
                                       info: DependencyOrRepositoryPlaceInfo,
                                       index: Int,
                                       selected: Boolean,
                                       hasFocus: Boolean): Unit = {
      setIcon(language.SbtFileType.getIcon)
      append(info.path + ":")
      append(info.line.toString, SimpleTextAttributes.GRAY_ATTRIBUTES)
      if (info.affectedProjects.nonEmpty)
        append(" (" + info.affectedProjects.mkString(", ") + ")")
    }
  }
}
