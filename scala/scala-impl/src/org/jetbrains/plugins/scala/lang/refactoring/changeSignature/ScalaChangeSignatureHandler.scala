package org.jetbrains.plugins.scala
package lang
package refactoring
package changeSignature

import com.intellij.ide.util.SuperMethodWarningUtil
import com.intellij.openapi.actionSystem.{CommonDataKeys, DataContext}
import com.intellij.openapi.editor.{Editor, ScrollType}
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile, PsiMethod}
import com.intellij.refactoring.changeSignature.{ChangeSignatureHandler, ChangeSignatureUtil}
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.refactoring.{HelpID, RefactoringBundle}
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, ResolvesTo}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScMethodLike, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDeclaration, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.light.isWrapper
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}

import scala.annotation.tailrec

/**
 * Nikolay.Tropin
 * 2014-08-29
 */
class ScalaChangeSignatureHandler extends ChangeSignatureHandler with ScalaRefactoringActionHandler {

  def invokeWithDialog(fun: ScMethodLike)
                      (implicit project: Project): Unit = {
    Stats.trigger(FeatureKey.changeSignature)
    new ScalaChangeSignatureDialog(
      new ScalaMethodDescriptor(fun),
      needSpecifyTypeChb = true
    ).show()
  }

  private def invokeOnElement(element: PsiElement)
                             (implicit project: Project, editor: Editor): Unit = {
    //noinspection ReferencePassedToNls
    def showErrorHint =
      ScalaRefactoringUtil.showErrorHint(_: String, ChangeSignatureHandler.REFACTORING_NAME, HelpID.CHANGE_SIGNATURE)

    def isSupportedFor(fun: ScMethodLike): Boolean = {
      fun match {
        case fun: ScFunction if fun.paramClauses.clauses.exists(_.isImplicit) =>
          val message = ScalaBundle.message("change.signature.not.supported.implicit.parameters")
          showErrorHint(message)
          false
        case fun: ScFunction if fun.hasModifierProperty("implicit") =>
          val message = ScalaBundle.message("change.signature.not.supported.implicit.functions")
          showErrorHint(message)
          false
        case fun: ScFunction if fun.name == "unapply" || fun.name == "unapplySeq" =>
          val message = ScalaBundle.message("change.signature.not.supported.extractors")
          showErrorHint(message)
          false
        case _ => true
      }
    }

    @tailrec
    def unwrapMethod(element: PsiElement): Option[PsiMethod] = element match {
      case null => None
      case isWrapper(fun: ScFunction) => unwrapMethod(fun)
      case fun: ScFunction if fun.isSynthetic =>
        fun.syntheticCaseClass match {
          case null => None
          case clazz => clazz.constructor
        }
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
          case Some(fun: ScMethodLike) =>
            if (isSupportedFor(fun)) invokeWithDialog(fun)
          case Some(m) if m != method => ChangeSignatureUtil.invokeChangeSignatureOn(m, project)
          case _ =>
        }
      case None =>
        val message = RefactoringBundle.getCannotRefactorMessage(getTargetNotFoundMessage)
        showErrorHint(message)
    }
  }

  override def invoke(file: PsiFile)
                     (implicit project: Project, editor: Editor, dataContext: DataContext): Unit = {
    editor.getScrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)

    val element = file.findScalaLikeFile.flatMap(
      scalaFile => Option(findTargetMember(scalaFile, editor))
    ).getOrElse(CommonDataKeys.PSI_ELEMENT.getData(dataContext))

    invokeOnElement(element)
  }


  override def invoke(elements: Array[PsiElement])
                     (implicit project: Project, dataContext: DataContext): Unit =
    elements match {
      case Array(head) =>
        implicit val editor: Editor = if (dataContext != null) CommonDataKeys.EDITOR.getData(dataContext)
        else null
        invokeOnElement(head)
      case _ =>
    }

  override def getTargetNotFoundMessage: String = ScalaBundle.message("error.wrong.caret.position.method.name")

  override def findTargetMember(element: PsiElement): PsiElement = {
    if (element.isInstanceOf[PsiMethod]) return element

    def resolvedMethod = PsiTreeUtil.getParentOfType(element, classOf[ScReference]) match {
      case null => null
      case ResolvesTo(m: PsiMethod) => m
      case _ => null
    }
    def currentFunction = PsiTreeUtil.getParentOfType(element, classOf[ScFunction]) match {
      case null => null
      case funDef: ScFunctionDefinition if !funDef.body.exists(_.isAncestorOf(element)) => funDef
      case decl: ScFunctionDeclaration => decl
      case _ => null
    }
    def primaryConstr = PsiTreeUtil.getParentOfType(element, classOf[ScClass]) match {
      case null => null
      case c: ScClass =>
        c.constructor match {
          case Some(constr)
            if PsiTreeUtil.isAncestor(c.nameId, element, false) || PsiTreeUtil.isAncestor(constr, element, false) => constr
          case _ => null
        }
    }
    Option(resolvedMethod) orElse Option(currentFunction) getOrElse primaryConstr
  }

  override def findTargetMember(file: PsiFile, editor: Editor): PsiElement = {
    val offset = editor.getCaretModel.getOffset
    val element = file.findElementAt(offset)
    Option(findTargetMember(element)) getOrElse {
      file.findReferenceAt(offset) match {
        case ResolvesTo(m: PsiMethod) => m
        case _ => null
      }
    }
  }
}