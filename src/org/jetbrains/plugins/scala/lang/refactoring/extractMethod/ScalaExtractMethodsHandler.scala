package org.jetbrains.plugins.scala.lang.refactoring.extractMethod

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.{ScrollType, Editor}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.refactoring.util.RefactoringMessageDialog
import com.intellij.refactoring.{HelpID, RefactoringActionHandler}
import org.jetbrains.plugins.scala.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.{ScDeclarationSequenceHolder, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.psi.dataFlow.impl.reachingDefs.ReachingDefintionsCollector
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import com.intellij.openapi.command.CommandProcessor
import com.intellij.psi.{PsiExpression, PsiDocumentManager, PsiElement, PsiFile}
import com.intellij.openapi.util.Pass
import collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/**
 * User: Alexander Podkhalyuzin
 * Date: 11.01.2010
 */
class ScalaExtractMethodHandler extends RefactoringActionHandler {
  private val REFACTORING_NAME: String = ScalaBundle.message("extract.method.title")

  def invoke(project: Project, elements: Array[PsiElement], dataContext: DataContext): Unit = {/*do nothing*/}

  def invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext): Unit = {
    editor.getScrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    if (!file.isInstanceOf[ScalaFile]) return
    if (!editor.getSelectionModel.hasSelection) {
      editor.getSelectionModel.selectLineAtCaret
    }

    invokeOnEditor(project, editor, file.asInstanceOf[ScalaFile], dataContext)
  }

  private def invokeOnEditor(project: Project, editor: Editor, file: ScalaFile, dataContext: DataContext): Unit = {
    if (!editor.getSelectionModel.hasSelection) return
    ScalaRefactoringUtil.trimSpacesAndComments(editor, file, false)
    val startElement: PsiElement = file.findElementAt(editor.getSelectionModel.getSelectionStart)
    val endElement: PsiElement = file.findElementAt(editor.getSelectionModel.getSelectionEnd - 1)
    val elements = ScalaPsiUtil.getElementsRange(startElement, endElement).toArray
    if (elements.length == 0) {
      showErrorMessage(ScalaBundle.message("cannot.extract.empty.message"), project)
      return
    }

    var hasReturn = false
    for (element <- elements) {
      if (element.isInstanceOf[ScSelfInvocation]) {
        showErrorMessage(ScalaBundle.message("cannot.extract.self.invocation"), project)
        return
      }

      element.accept(new ScalaRecursiveElementVisitor {
        override def visitReturnStatement(ret: ScReturnStmt) = {
          hasReturn = true
        }
      })
    }
    val siblings: Array[PsiElement] = getSiblings(elements(0))
    val scope: PsiElement = {
      if (file.isScriptFile()) {
        file
      } else if (siblings.length > 0) {
        val lastSibling = siblings(0)
        val element = elements(0)
        var parent = element.getParent
        var res: PsiElement = null
        while (parent != lastSibling) {
          parent match {
            case bl: ScBlock => res = bl
            case _ =>
          }

          parent = parent.getParent
        }
        res
      } else null
    }
    if (scope == null) return
    if (siblings.length > 1) {
      ScalaExtractMethodUtils.showChooser(editor, siblings, {selectedValue =>
        invokeDialog(project, editor, elements, hasReturn, selectedValue, scope)
      }, "Choose level for Extract Method", getTextForElement _)
      return
    }
    else if (siblings.length == 1) {
      invokeDialog(project, editor, elements, hasReturn, siblings(0), scope)
    } else return
  }

  private def getSiblings(element: PsiElement): Array[PsiElement] = {
    val res = new ArrayBuffer[PsiElement]
    var prev = element
    var parent = element.getParent
    while (parent != null) {
      parent match {
        case file: ScalaFile if file.isScriptFile() => res += prev
        case block: ScBlockExpr => res += prev
        case tryBlcok: ScTryBlock => res += prev
        case templ: ScTemplateBody => res += prev
        case _ =>
      }
      prev = parent
      parent = parent.getParent
    }
    return res.toArray.reverse
  }

  private def invokeDialog(project: Project, editor: Editor, elements: Array[PsiElement], hasReturn: Boolean, sibling: PsiElement, scope: PsiElement) {
    val settings: ScalaExtractMethodSettings = if (!ApplicationManager.getApplication.isUnitTestMode) {
      val dialog = new ScalaExtractMethodDialog(project, elements, hasReturn, sibling, scope)
      dialog.show
      if (!dialog.isOK) {
        return
      }
      dialog.getSettings
    } else {
      return //todo: unit tests is not supported yet
    }
    performRefactoring(settings, editor)
  }

  private def getTextForElement(element: PsiElement): String = {
    element.getParent match {
      case _: ScTemplateBody => "Extract class method"
      case _: ScBlock => "Extract local method"
      case _: ScalaFile => "Extract file method"
      case _ => "Unknown extraction"
    }
  }

  private def performRefactoring(settings: ScalaExtractMethodSettings, editor: Editor) {
    val method = ScalaExtractMethodUtils.createMethodFromSettings(settings)
    if (method == null) return
    val runnable = new Runnable {
      def run: Unit = {
        PsiDocumentManager.getInstance(editor.getProject).commitDocument(editor.getDocument)
        val scope = settings.scope
        val sibling = settings.nextSibling
        sibling.getParent.getNode.addChild(method.getNode, sibling.getNode)
        sibling.getParent.getNode.addChild(ScalaPsiElementFactory.createNewLineNode(method.getManager), sibling.getNode)
        val methodName = settings.methodName
        val call = ScalaPsiElementFactory.createExpressionFromText(methodName /*todo*/, settings.elements.apply(0).getManager)
        settings.elements.apply(0).replace(call)
        var i = 1
        while (i < settings.elements.length) {
          settings.elements.apply(i).getParent.getNode.removeChild(settings.elements.apply(i).getNode)
          i = i + 1
        }
      }
    }
    CommandProcessor.getInstance.executeCommand(editor.getProject, new Runnable {
      def run: Unit = {
        ApplicationManager.getApplication.runWriteAction(runnable)
        editor.getSelectionModel.removeSelection
      }
    }, REFACTORING_NAME, null)
  }

  private def showErrorMessage(text: String, project: Project): Unit = {
    if (ApplicationManager.getApplication.isUnitTestMode) throw new RuntimeException(text)
    val dialog = new RefactoringMessageDialog(REFACTORING_NAME, text,
            HelpID.EXTRACT_METHOD, "OptionPane.errorIcon", false, project)
    dialog.show
  }
}