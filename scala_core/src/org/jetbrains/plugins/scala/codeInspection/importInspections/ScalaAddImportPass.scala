package org.jetbrains.plugins.scala.codeInspection.importInspections

import com.intellij.openapi.command.CommandProcessor
import _root_.scala.collection.mutable.ArrayBuffer
import com.intellij.openapi.ui.popup.PopupChooserBuilder
import com.intellij.ide.util.FQNameCellRenderer
import javax.swing.JList
import util.ScalaUtils
import com.intellij.util.ActionRunner
import com.intellij.psi.PsiDocumentManager
import com.intellij.codeInsight.CodeInsightUtilBase
import lang.psi.ScalaFile
import com.intellij.openapi.application.Result
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiClass
import com.intellij.codeInsight.hint.QuestionAction
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.JavaPsiFacade
import lang.psi.api.base.ScReferenceElement
import lang.lexer.ScalaTokenTypes
import com.intellij.openapi.application.ApplicationManager
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.openapi.util.TextRange
import java.awt.Point
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

/**
 * User: Alexander Podkhalyuzin
 * Date: 07.07.2008
 */

class ScalaAddImportPass(file: PsiFile, editor: Editor) extends {val project = file.getProject
  val document = editor.getDocument}
  with TextEditorHighlightingPass(project, document) {
  def doCollectInformation(progress: ProgressIndicator) {
  }
  def doApplyInformationToEditor {
    val added = new ArrayBuffer[PsiClass]
    ApplicationManager.getApplication.assertIsDispatchThread
    if (!editor.getContentComponent.hasFocus) return
    var flag = false
    for (visibleHighlight <- visibleHighlights) {
      ProgressManager.getInstance.checkCanceled
      val element = file.findElementAt(visibleHighlight.startOffset)
      if (element != null && element.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER /*&&
         visibleHighlight.`type`.getAttributesKey() == HighlightInfoType.WRONG_REF.getAttributesKey()*/ ) {
        element.getParent match {
          case x: ScReferenceElement if x.refName != null => {
            val function = JavaPsiFacade.getInstance(myProject).getShortNamesCache().getClassesByName _
            val classes = function(x.refName, GlobalSearchScope.allScope(myProject))
            classes.length match {
              case 0 =>
              case 1 if CodeStyleSettingsManager.getSettings(project).ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY &&
                      !added.contains(classes(0)) &&
                      !caretNear(x) => {
                CommandProcessor.getInstance().runUndoTransparentAction(new Runnable {
                  def run() {
                    new ScalaAddImportAction(classes, x).execute()
                  }
                })
                added += classes(0)
              }
              case _ if !flag => {
                fixesAction(x)
                flag = true
              }
              case _ =>
            }
          }
          case _ =>
        }
      }
    }
  }

  private def caretNear(ref: ScReferenceElement): Boolean = ref.getTextRange.grown(1).contains(editor.getCaretModel.getOffset)

  private def range = {
    val visibleRect = editor.getScrollingModel.getVisibleArea;
    val startPosition = editor.xyToLogicalPosition(new Point(visibleRect.x, visibleRect.y));
    val myStartOffset = editor.logicalPositionToOffset(startPosition);
    val endPosition = editor.xyToLogicalPosition(new Point(visibleRect.x + visibleRect.width, visibleRect.y + visibleRect.height));
    val myEndOffset = editor.logicalPositionToOffset(new LogicalPosition(endPosition.line + 1, 0));
    new TextRange(myStartOffset, myEndOffset);
  }
  private def visibleHighlights: Array[HighlightInfo] = {
    val highlights = DaemonCodeAnalyzerImpl.getHighlights(document, project)
    if (highlights == null) return Array[HighlightInfo]()
    for (info <- highlights if startOffset <= info.startOffset && endOffset >= info.endOffset &&
            !editor.getFoldingModel.isOffsetCollapsed(info.startOffset)) yield info
  }

  private def startOffset = range.getStartOffset
  private def endOffset = range.getEndOffset

  private def fixesAction(ref: ScReferenceElement) {
    ApplicationManager.getApplication.invokeLater(new Runnable {
      def run {
        if (HintManager.getInstance().hasShownHintsThatWillHideByOtherHint()) {
          return;
        }
        val function = JavaPsiFacade.getInstance(myProject).getShortNamesCache().getClassesByName _
        val classes = function(ref.refName, GlobalSearchScope.allScope(myProject))
        val action = new ScalaAddImportAction(classes: Array[PsiClass], ref: ScReferenceElement)
        val offset = ref.getTextRange.getStartOffset
        if (classes.length > 0 && offset >= startOffset && offset <= endOffset) {
          HintManager.getInstance().showQuestionHint(editor,
          if (classes.length == 1) classes(0).getQualifiedName + "? Alt+Enter"
          else classes(0).getQualifiedName + "? (multiple choises...) Alt+Enter",
          offset,
          offset + ref.getTextLength(),
          action)
        }
      }
    })
  }

  private class ScalaAddImportAction(classes: Array[PsiClass], ref: ScReferenceElement) extends QuestionAction {
    def addImport(clazz: PsiClass) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        def run() {
          if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;
          file match {
            case file: ScalaFile => {
              ScalaUtils.runWriteAction(new Runnable {
                def run: Unit = file.addImportForClass(clazz)
              }, clazz.getProject, "Add import action")
            }
          }
        }
      })
    }

    def chooseClass {
      val list = new JList(classes.asInstanceOf[Array[Object]])
      list.setCellRenderer(new FQNameCellRenderer())
      new PopupChooserBuilder(list).setTitle(ScalaBundle.message("class.import.title", Array[Object]())).
              setItemChoosenCallback(new Runnable {
        def run {
          val index = list.getSelectedIndex()
          if (index < 0) return
          PsiDocumentManager.getInstance(myProject).commitAllDocuments()
          addImport(classes(index))
        }
      }).createPopup().showInBestPositionFor(editor)
    }

    def execute: Boolean = {
      for (clazz <- classes if !clazz.isValid) return false

      PsiDocumentManager.getInstance(myProject).commitAllDocuments()
      if (classes.length == 1) addImport(classes(0))
      else chooseClass

      return true
    }
  }
}