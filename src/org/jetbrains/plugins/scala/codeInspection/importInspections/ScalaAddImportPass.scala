package org.jetbrains.plugins.scala.codeInspection.importInspections

import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.util.{PsiTreeUtil, PsiUtil}
import lang.psi.api.expr.{ScMethodCall, ScReferenceExpression}
import lang.psi.api.ScalaFile
import lang.psi.api.toplevel.imports.ScImportStmt
import lang.psi.api.base.{ScReferenceElement, ScStableCodeReferenceElement}
import lang.psi.api.toplevel.templates.ScTemplateBody
import lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition, ScObject}
import lang.psi.ScImportsHolder
import lang.resolve.ResolveUtils
import com.intellij.codeInsight.hint.{HintManagerImpl, HintManager, QuestionAction}
import lang.formatting.settings.ScalaCodeStyleSettings
import _root_.scala.collection.mutable.HashSet
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.openapi.command.CommandProcessor
import _root_.scala.collection.mutable.ArrayBuffer
import com.intellij.openapi.ui.popup.PopupChooserBuilder
import com.intellij.ide.util.FQNameCellRenderer
import javax.swing.JList
import scala.util.ScalaUtils
import com.intellij.util.ActionRunner
import com.intellij.codeInsight.CodeInsightUtilBase
import com.intellij.openapi.application.Result
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.codeStyle.CodeStyleSettings
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
/**
 * User: Alexander Podkhalyuzin
 * Date: 07.07.2008
 */

class ScalaAddImportPass(file: PsiFile, editor: Editor) extends {
  val project = file.getProject
  val document = editor.getDocument
  val scalaSettings = CodeStyleSettingsManager.getSettings(project).getCustomSettings(classOf[ScalaCodeStyleSettings])
}  with TextEditorHighlightingPass(project, document) {
  def doCollectInformation(progress: ProgressIndicator) {
  }
  def doApplyInformationToEditor {
    val added = new ArrayBuffer[PsiClass]
    ApplicationManager.getApplication.assertIsDispatchThread
    if (!editor.getContentComponent.hasFocus) return
    for (visibleHighlight <- visibleHighlights) {
      ProgressManager.getInstance.checkCanceled
      val element = file.findElementAt(visibleHighlight.startOffset)
      if (element != null && element.getNode != null &&
          element.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER &&
          !importsElement(element)) {
        element.getParent match {
          case x: ScReferenceElement if x.refName != null && (x.multiResolve(false).length == 0) => {
            val classes = ScalaAddImportPass.getClasses(x, myProject)
            classes.length match {
              case 0 =>
              case 1 if scalaSettings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY &&
                      !added.contains(classes(0)) &&
                      !caretNear(x) => {
                CommandProcessor.getInstance().runUndoTransparentAction(new Runnable {
                  def run() {
                    new ScalaAddImportAction(classes, x).execute()
                  }
                })
                added += classes(0)
              }
              case _ => fixesAction(x)
            }
          }
          case _ =>
        }
      }
    }
  }

  private def importsElement(element: PsiElement): Boolean = {
    var elem = element
    while (elem != null && !elem.isInstanceOf[ScImportStmt]) elem = elem.getParent
    return elem != null
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
  private def visibleHighlights = {
    val highlights = DaemonCodeAnalyzerImpl.getHighlights(document, project)
    if (highlights == null) Array[HighlightInfo]() else {
      import collection.jcl.Conversions.convertList
      for (info <- highlights if isWrongRef(info.`type`) && startOffset <= info.startOffset && endOffset >= info.endOffset &&
              !editor.getFoldingModel.isOffsetCollapsed(info.startOffset)) yield info
    }
  }

  private def startOffset = range.getStartOffset
  private def endOffset = range.getEndOffset

  private def fixesAction(ref: ScReferenceElement) {
    ApplicationManager.getApplication.invokeLater(new Runnable {
      def run {
        if (!ref.isValid) return

        if (HintManagerImpl.getInstanceImpl.hasShownHintsThatWillHideByOtherHint()) return

        val classes = ScalaAddImportPass.getClasses(ref, myProject)
        val action = new ScalaAddImportAction(classes: Array[PsiClass], ref: ScReferenceElement)
        
        val offset = ref.getTextRange.getStartOffset
        if (classes.length > 0 && offset >= startOffset && offset <= endOffset && editor != null &&
                offset <= editor.getDocument.getTextLength) {
          HintManager.getInstance().showQuestionHint(editor,
          if (classes.length == 1) classes(0).getQualifiedName + "? Alt+Enter"
          else classes(0).getQualifiedName + "? (multiple choices...) Alt+Enter",
          offset,
          offset + ref.getTextLength(),
          action)
        }
      }
    })
  }

  private def isWrongRef(info: HighlightInfoType): Boolean = info.getAttributesKey == HighlightInfoType.WRONG_REF.getAttributesKey

  class ScalaAddImportAction(classes: Array[PsiClass], ref: ScReferenceElement) extends QuestionAction {
    def addImport(clazz: PsiClass) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        def run() {
          if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;
          file match {
            case file: ScalaFile => {
              ScalaUtils.runWriteAction(new Runnable {
                def run: Unit = {
                  PsiDocumentManager.getInstance(project).commitDocument(document)
                  getImportHolder.addImportForClass(clazz, ref)
                }
              }, clazz.getProject, "Add import action")
            }
          }
        }
      })
    }

    def getImportHolder: ScImportsHolder = if (scalaSettings.ADD_IMPORT_MOST_CLOSE_TO_REFERENCE)
        PsiTreeUtil.getParentOfType(ref, classOf[ScImportsHolder])
      else file.asInstanceOf[ScImportsHolder]

    def chooseClass {
      val list = new JList(classes.asInstanceOf[Array[Object]])
      list.setCellRenderer(new FQNameCellRenderer())
      new PopupChooserBuilder(list).setTitle(ScalaBundle.message("class.import.title")).
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

object ScalaAddImportPass {
  private def notInner(clazz: PsiClass, ref: PsiElement): Boolean = {
    clazz match {
      case t: ScTypeDefinition => {
        t.getParent match {
          case _: ScalaFile => true
          case _: ScTemplateBody if t.getContainingClass.isInstanceOf[ScObject] => {
            val obj = t.getContainingClass.asInstanceOf[ScObject]
            ResolveUtils.isAccessible(obj, ref) && notInner(obj, ref)
          }
          case _ => false
        }
      }
      case _ => true
    }
  }

  def getClasses(ref : ScReferenceElement, myProject: Project) = {
    val kinds = ref.getKinds(false)
    val cache = JavaPsiFacade.getInstance(myProject).getShortNamesCache
    val classes = cache.getClassesByName(ref.refName, ref.getResolveScope).filter {
      clazz => clazz != null && clazz.getQualifiedName() != null && clazz.getQualifiedName.indexOf(".") > 0 &&
              ResolveUtils.kindMatches(clazz, kinds) && notInner(clazz, ref) && ResolveUtils.isAccessible(clazz, ref)
    }
    if (ref.getParent.isInstanceOf[ScMethodCall]) {
      classes.filter((clazz: PsiClass) => (clazz.isInstanceOf[ScClass] && clazz.hasModifierProperty("case")) ||
              (clazz.isInstanceOf[ScObject] && clazz.asInstanceOf[ScObject].functionsByName("apply").toSeq.length > 0))
    }
    else classes
  }
}