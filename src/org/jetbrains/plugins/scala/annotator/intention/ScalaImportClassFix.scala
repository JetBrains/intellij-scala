package org.jetbrains.plugins
package scala
package annotator
package intention


import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackaging
import com.intellij.codeInsight.hint.{HintManager, HintManagerImpl, QuestionAction}
import com.intellij.codeInsight.CodeInsightUtilBase

import com.intellij.ide.util.FQNameCellRenderer
import com.intellij.openapi.editor.{LogicalPosition, Editor}
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.command.CommandProcessor
import com.intellij.psi._
import lang.psi.api.expr.ScMethodCall
import lang.psi.api.toplevel.templates.ScTemplateBody
import lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScObject, ScClass}
import lang.psi.impl.toplevel.typedef.ScTypeDefinitionImpl
import lang.resolve.ResolveUtils
import java.awt.Point
import com.intellij.psi.util.PsiTreeUtil
import lang.psi.ScImportsHolder
import scala.util.ScalaUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.codeInspection.HintAction
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import lang.formatting.settings.ScalaCodeStyleSettings
import lang.psi.api.base.ScReferenceElement
import lang.psi.api.ScalaFile
import com.intellij.codeInsight.completion.JavaCompletionUtil
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.util.ObjectUtils
import javax.swing.{Icon, JList}
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction
import com.intellij.openapi.ui.popup.{JBPopupFactory, PopupStep, PopupChooserBuilder}

/**
 * User: Alexander Podkhalyuzin
 * Date: 15.07.2009
 */

class ScalaImportClassFix(private var classes: Array[PsiClass], ref: ScReferenceElement) extends {
    val project = ref.getProject
  } with  HintAction {
  def getText = ScalaBundle.message("import.with", classes(0).getQualifiedName)

  def getFamilyName = ScalaBundle.message("import.class")

  def isAvailable(project: Project, editor: Editor, file: PsiFile) = file.isInstanceOf[ScalaFile]

  def invoke(project: Project, editor: Editor, file: PsiFile) = {
    CommandProcessor.getInstance().runUndoTransparentAction(new Runnable {
      def run() {
        if (!ref.isValid) return
        classes = ScalaImportClassFix.getClasses(ref, project)
        new ScalaAddImportAction(editor, classes, ref).execute()
      }
    })
  }

  private val scalaSettings: ScalaCodeStyleSettings = CodeStyleSettingsManager.getSettings(project).getCustomSettings(classOf[ScalaCodeStyleSettings])
  def showHint(editor: Editor): Boolean = {
    if (!ref.isValid) return false
    ref.qualifier match {
      case Some(_) => return false
      case None => {
        classes = ScalaImportClassFix.getClasses(ref, project)
        classes.length match {
          case 0 => return false
          case 1 if scalaSettings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY  &&
                  !caretNear(editor) => {
            CommandProcessor.getInstance().runUndoTransparentAction(new Runnable {
              def run() {
                new ScalaAddImportAction(editor, classes, ref).execute()
              }
            })
            return false
          }
          case _ => {
            fixesAction(editor)
            return true
          }
        }
      }
    }
  }

  private def caretNear(editor: Editor): Boolean = ref.getTextRange.grown(1).contains(editor.getCaretModel.getOffset)

  private def range(editor: Editor) = {
    val visibleRectangle = editor.getScrollingModel.getVisibleArea;
    val startPosition = editor.xyToLogicalPosition(new Point(visibleRectangle.x, visibleRectangle.y));
    val myStartOffset = editor.logicalPositionToOffset(startPosition);
    val endPosition = editor.xyToLogicalPosition(new Point(visibleRectangle.x + visibleRectangle.width, visibleRectangle.y + visibleRectangle.height));
    val myEndOffset = editor.logicalPositionToOffset(new LogicalPosition(endPosition.line + 1, 0));
    new TextRange(myStartOffset, myEndOffset);
  }

  private def startOffset(editor: Editor) = range(editor).getStartOffset
  private def endOffset(editor: Editor) = range(editor).getEndOffset

  private def fixesAction(editor: Editor) {
    ApplicationManager.getApplication.invokeLater(new Runnable {
      def run {
        if (!ref.isValid) return
        if (ref.resolve != null) return

        if (HintManagerImpl.getInstanceImpl.hasShownHintsThatWillHideByOtherHint()) return
        val action = new ScalaAddImportAction(editor, classes: Array[PsiClass], ref: ScReferenceElement)

        val offset = ref.getTextRange.getStartOffset
        if (classes.length > 0 && offset >= startOffset(editor) && offset <= endOffset(editor) && editor != null &&
                offset <= editor.getDocument.getTextLength) {
          HintManager.getInstance().showQuestionHint(editor,
          if (classes.length == 1) classes(0).getQualifiedName + "? Alt+Enter"
          else classes(0).getQualifiedName + "? (multiple choices...) Alt+Enter",
          offset,
          offset + ref.getTextLength(),
          action)
          return
        }
      }
    })
  }

  def startInWriteAction(): Boolean = true



  class ScalaAddImportAction(editor: Editor, classes: Array[PsiClass], ref: ScReferenceElement) extends QuestionAction {
    def addImport(clazz: PsiClass) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        def run() {
          if (!ref.isValid || !CodeInsightUtilBase.prepareFileForWrite(ref.getContainingFile)) return;
          ScalaUtils.runWriteAction(new Runnable {
            def run: Unit = {
              PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
              ScalaImportClassFix.getImportHolder(ref, project).addImportForClass(clazz, ref)
            }
          }, clazz.getProject, "Add import action")
        }
      })
    }

    def chooseClass {
      val list = new JList(classes.asInstanceOf[Array[Object]])
      list.setCellRenderer(new FQNameCellRenderer())
      /*new PopupChooserBuilder(list).setTitle(ScalaBundle.message("class.import.title")).
              setItemChoosenCallback(new Runnable {
        def run {
          val index = list.getSelectedIndex()
          if (index < 0) return
          PsiDocumentManager.getInstance(project).commitAllDocuments()
          addImport(classes(index))
        }
      }).createPopup().showInBestPositionFor(editor)*/

      val popup = new BaseListPopupStep[PsiClass](QuickFixBundle.message("class.to.import.chooser.title"), classes) {
        override def getIconFor(aValue: PsiClass): Icon = {
          return aValue.getIcon(0)
        }

        override def getTextFor(value: PsiClass): String = {
          return ObjectUtils.assertNotNull(value.getQualifiedName)
        }

        import PopupStep.FINAL_CHOICE
        override def onChosen(selectedValue: PsiClass, finalChoice: Boolean): PopupStep[_] = {
          if (selectedValue == null) {
            return FINAL_CHOICE
          }
          if (finalChoice) {
            PsiDocumentManager.getInstance(project).commitAllDocuments
            addImport(selectedValue)
            return FINAL_CHOICE
          }
          var qname: String = selectedValue.getQualifiedName
          if (qname == null) return FINAL_CHOICE
          var toExclude: java.util.List[String] = AddImportAction.getAllExcludableStrings(qname)
          return new BaseListPopupStep[String](null, toExclude) {
            override def onChosen(selectedValue: String, finalChoice: Boolean): PopupStep[_] = {
              if (finalChoice) {
                AddImportAction.excludeFromImport(project, selectedValue)
              }
              return super.onChosen(selectedValue, finalChoice)
            }

            override def getTextFor(value: String): String = {
              return "Exclude '" + value + "' from auto-import"
            }
          }
        }

        override def hasSubstep(selectedValue: PsiClass): Boolean = {
          return true
        }
      }
      JBPopupFactory.getInstance.createListPopup(popup).showInBestPositionFor(editor)
    }

    def execute: Boolean = {
      for (clazz <- classes if !clazz.isValid) return false

      PsiDocumentManager.getInstance(project).commitAllDocuments()
      if (classes.length == 1) addImport(classes(0))
      else chooseClass

      return true
    }
  }
}

object ScalaImportClassFix {
  def getImportHolder(ref: ScReferenceElement, project: Project): ScImportsHolder = {
    val scalaSettings: ScalaCodeStyleSettings =
      CodeStyleSettingsManager.getSettings(project).getCustomSettings(classOf[ScalaCodeStyleSettings])
    if (scalaSettings.ADD_IMPORT_MOST_CLOSE_TO_REFERENCE)
      PsiTreeUtil.getParentOfType(ref, classOf[ScImportsHolder])
    else {
      PsiTreeUtil.getParentOfType(ref, classOf[ScPackaging]) match {
        case null => ref.getContainingFile.asInstanceOf[ScImportsHolder]
        case packaging: ScPackaging => packaging
      }
    }
  }

  private def notInner(clazz: PsiClass, ref: PsiElement): Boolean = {
    def parent(t: ScTypeDefinition): PsiElement = {
      val stub = t.asInstanceOf[ScTypeDefinitionImpl].getStub
      if (stub != null) stub.getParentStub.getPsi
      else t.getParent
    }
    clazz match {
      case t: ScTypeDefinition => {
       parent(t) match {
          case _: ScalaFile => true
          case _: ScPackaging => true
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

  def getClasses(ref: ScReferenceElement, myProject: Project): Array[PsiClass] = {
    if (!ref.isValid) return Array.empty
    val kinds = ref.getKinds(false)
    val cache = JavaPsiFacade.getInstance(myProject).getShortNamesCache
    val classes = cache.getClassesByName(ref.refName, ref.getResolveScope).filter {
      clazz => clazz != null && clazz.getQualifiedName() != null && clazz.getQualifiedName.indexOf(".") > 0 &&
              ResolveUtils.kindMatches(clazz, kinds) && notInner(clazz, ref) && ResolveUtils.isAccessible(clazz, ref) &&
              !JavaCompletionUtil.isInExcludedPackage(clazz)
    }
    if (ref.getParent.isInstanceOf[ScMethodCall]) {
      classes.filter((clazz: PsiClass) => (clazz.isInstanceOf[ScClass] && clazz.hasModifierProperty("case")) ||
              (clazz.isInstanceOf[ScObject] && clazz.asInstanceOf[ScObject].functionsByName("apply").toSeq.length > 0))
    }
    else classes
  }
}