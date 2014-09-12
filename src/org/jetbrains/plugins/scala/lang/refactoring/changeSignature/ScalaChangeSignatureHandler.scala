package org.jetbrains.plugins.scala
package lang.refactoring.changeSignature

import com.intellij.ide.util.SuperMethodWarningUtil
import com.intellij.openapi.actionSystem.{CommonDataKeys, DataContext}
import com.intellij.openapi.editor.{Editor, ScrollType}
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile, PsiMethod}
import com.intellij.refactoring.changeSignature.{ChangeSignatureHandler, ChangeSignatureUtil}
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.refactoring.{HelpID, RefactoringBundle}
import org.jetbrains.plugins.scala.extensions.Resolved
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDeclaration, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.light.isWrapper

/**
 * Nikolay.Tropin
 * 2014-08-29
 */
class ScalaChangeSignatureHandler extends ChangeSignatureHandler {

  def invokeWithDialog(project: Project, fun: ScFunction) {
    val dialog = new ScalaChangeSignatureDialog(project, new ScalaMethodDescriptor(fun))
    dialog.show()
  }

  private def invokeOnElement(project: Project, editor: Editor, element: PsiElement): Unit = {
    def showErrorHint(message: String) = {
      val name = ChangeSignatureHandler.REFACTORING_NAME
      CommonRefactoringUtil.showErrorHint(project, editor, message, name, HelpID.CHANGE_SIGNATURE)
    }
    def isSupportedFor(fun: ScFunction): Boolean = {
      fun match {
        case fun: ScFunction if fun.paramClauses.clauses.length > 1 =>
          val message = ScalaBundle.message("change.signature.not.supported.multiple.parameter.clauses")
          showErrorHint(message)
          false
        case fun: ScFunction if fun.isConstructor =>
          val message = ScalaBundle.message("change.signature.not.supported.constructors")
          showErrorHint(message)
          false
        case _ => true
      }
    }

    def unwrapMethod(element: PsiElement) = element match {
      case null => None
      case isWrapper(fun: ScFunction) => Some(fun)
      case m: PsiMethod => Some(m)
      case _ => None
    }

    unwrapMethod(element) match {
      case Some(method) =>
        if (!CommonRefactoringUtil.checkReadOnlyStatus(project, method)) return
        method match {
          case f: ScFunction if f.isSynthetic => return
          case _ =>
        }

        val newMethod = SuperMethodWarningUtil.checkSuperMethod(method, RefactoringBundle.message("to.refactor"))
        unwrapMethod(newMethod) match {
          case Some(fun: ScFunction) =>
            if (isSupportedFor(fun)) invokeWithDialog(project, fun)
          case Some(m) => ChangeSignatureUtil.invokeChangeSignatureOn(m, project)
          case None =>
        }
      case None =>
        val message = RefactoringBundle.getCannotRefactorMessage(getTargetNotFoundMessage)
        showErrorHint(message)
    }
  }

  override def invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext): Unit = {
    editor.getScrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    val element = Option(findTargetMember(file, editor))
            .getOrElse(CommonDataKeys.PSI_ELEMENT.getData(dataContext))

    invokeOnElement(project, editor, element)
  }

  override def invoke(project: Project, elements: Array[PsiElement], dataContext: DataContext): Unit = {
    if (elements.length != 1) return
    val editor: Editor = if (dataContext == null) null else CommonDataKeys.EDITOR.getData(dataContext)
    invokeOnElement(project, editor, elements(0))
  }

  override def getTargetNotFoundMessage: String = ScalaBundle.message("error.wrong.caret.position.method.name")

  override def findTargetMember(element: PsiElement): PsiElement = {
    if (element.isInstanceOf[PsiMethod]) return element

    def resolvedMethod = PsiTreeUtil.getParentOfType(element, classOf[ScReferenceElement]) match {
      case null => null
      case Resolved(m: PsiMethod, _) => m
      case _ => null
    }
    def currentFunction = PsiTreeUtil.getParentOfType(element, classOf[ScFunction]) match {
      case null => null
      case funDef: ScFunctionDefinition if !funDef.body.exists(_.isAncestorOf(element)) => funDef
      case decl: ScFunctionDeclaration => decl
      case _ => null
    }
    Option(resolvedMethod) getOrElse currentFunction
  }

  override def findTargetMember(file: PsiFile, editor: Editor): PsiElement = {
    val offset = editor.getCaretModel.getOffset
    val element = file.findElementAt(offset)
    Option(findTargetMember(element)) getOrElse {
      file.findReferenceAt(offset) match {
        case Resolved(m: PsiMethod, _) => m
        case _ => null
      }
    }
  }
}
