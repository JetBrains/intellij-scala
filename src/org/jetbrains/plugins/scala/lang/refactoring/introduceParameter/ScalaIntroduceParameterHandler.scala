package org.jetbrains.plugins.scala
package lang
package refactoring
package introduceParameter


import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import psi.api.ScalaFile
import com.intellij.openapi.application.ApplicationManager
import refactoring.util.ScalaRefactoringUtil.IntroduceException
import com.intellij.refactoring.ui.ConflictsDialog
import psi.types.ScType
import psi.api.expr._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.wm.WindowManager
import namesSuggester.NameSuggester
import psi.api.statements.ScFunctionDefinition
import refactoring.util.{ScalaVariableValidator, ConflictsReporter, ScalaRefactoringUtil}
import org.jetbrains.plugins.scala.util.ScalaUtils
import collection.mutable.ArrayBuffer
import com.intellij.psi._
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.ide.util.SuperMethodWarningUtil
import com.intellij.refactoring.{RefactoringBundle, HelpID, RefactoringActionHandler}

/**
 * User: Alexander Podkhalyuzin
 * Date: 11.06.2009
 */

class ScalaIntroduceParameterHandler extends RefactoringActionHandler with ConflictsReporter {
  val REFACTORING_NAME = ScalaBundle.message("introduce.parameter.title")

  def invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext): Unit = {
    def invokes() {
      ScalaRefactoringUtil.trimSpacesAndComments(editor, file)
      invoke(project, editor, file, editor.getSelectionModel.getSelectionStart, editor.getSelectionModel.getSelectionEnd)
    }
    ScalaRefactoringUtil.invokeRefactoring(project, editor, file, dataContext, "Introduce Variable", invokes _)
  }

  def invoke(project: Project, editor: Editor, file: PsiFile, startOffset: Int, endOffset: Int) {
    try {
      PsiDocumentManager.getInstance(project).commitAllDocuments
      if (!file.isInstanceOf[ScalaFile])
        showErrorMessage(ScalaBundle.message("only.for.scala"), project, editor)

      if (!ScalaRefactoringUtil.ensureFileWritable(project, file))
        showErrorMessage(ScalaBundle.message("file.is.not.writable"), project, editor)

      val (expr: ScExpression, typez: ScType) = ScalaRefactoringUtil.getExpression(project, editor, file, startOffset, endOffset).
              getOrElse(showErrorMessage(ScalaBundle.message("cannot.refactor.not.expression"), project, editor))

      val typeText = ScType.presentableText(typez)

      expr.getParent match {
        case inf: ScInfixExpr if inf.operation == expr => showErrorMessage(ScalaBundle.message("cannot.refactor.not.expression"), project, editor)
        case post: ScPostfixExpr if post.operation == expr => showErrorMessage(ScalaBundle.message("cannot.refactor.not.expression"), project, editor)
        case _: ScGenericCall => showErrorMessage(ScalaBundle.message("connot.refactor.under.generic.call"), project, editor)
        case _ if expr.isInstanceOf[ScConstrExpr] => showErrorMessage(ScalaBundle.message("cannot.refactor.constr.expression"), project, editor)
        case _ =>
      }

     chooseEnclosingMethod(project, editor, file, startOffset, endOffset, expr, typez)
    }
    catch {
      case _: IntroduceException => return
    }
  }

  def runDialog(project: Project, editor: Editor, file: PsiFile, startOffset: Int, endOffset: Int,
                function: ScFunctionDefinition, expr: ScExpression, typez: ScType) {
    try {
      if (function == null) {
        showErrorMessage(ScalaBundle.message("cannot.refactor.no.function"), project, editor)
      }

      val methodToSearchFor: PsiMethod = SuperMethodWarningUtil.checkSuperMethod(function, RefactoringBundle.message("to.refactor"))
      if (methodToSearchFor == null) return
      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, methodToSearchFor)) return

      val occurrences: Array[TextRange] = ScalaRefactoringUtil.getOccurrences(ScalaRefactoringUtil.unparExpr(expr), function)
      // Getting settings
      val validator = new ScalaVariableValidator(this, project, expr, occurrences, function, function)
      // Add occurrences highlighting
      if (occurrences.length > 1)
        ScalaRefactoringUtil.highlightOccurrences(project, occurrences, editor)

      val possibleNames = NameSuggester.suggestNames(expr, validator)
      val dialog = new ScalaIntroduceParameterDialog(project, editor, typez, occurrences,
        validator, possibleNames, methodToSearchFor, startOffset, endOffset, function, expr)
      dialog.show
    }
    catch {
      case _: IntroduceException => return
    }
  }

  def invoke(project: Project, elements: Array[PsiElement], dataContext: DataContext): Unit = {/*do nothing*/}

  def reportConflicts(conflicts: Array[String], project: Project): Boolean = {
    val conflictsDialog = new ConflictsDialog(project, conflicts: _*)
    conflictsDialog.show
    return conflictsDialog.isOK
  }

  /**
   * @throws IntroduceException
   */
  def showErrorMessage(text: String, project: Project, editor: Editor): Nothing = {
    if (ApplicationManager.getApplication.isUnitTestMode) throw new RuntimeException(text)
    CommonRefactoringUtil.showErrorHint(project, editor, text, REFACTORING_NAME, HelpID.INTRODUCE_PARAMETER)
    throw new IntroduceException
  }

  private def getEnclosingMethods(expr: PsiElement): Seq[ScFunctionDefinition] = {
    var enclosingMethods = new ArrayBuffer[ScFunctionDefinition]
    var elem: PsiElement = expr
    while (elem != null) {
      val newFun: ScFunctionDefinition = PsiTreeUtil.getContextOfType(elem, true, classOf[ScFunctionDefinition])
      if (newFun != null && newFun.body != None && PsiTreeUtil.isContextAncestor(newFun.body.get, expr, false))
        enclosingMethods += newFun
      elem = newFun
    }
    if (enclosingMethods.size > 1) {
      var methodsNotImplementingLibraryInterfaces = new ArrayBuffer[ScFunctionDefinition]
      for (enclosing <- enclosingMethods) {
        val superMethods: Seq[PsiMethod] = enclosing.superMethods
        var libraryInterfaceMethod: Boolean = false
        for (superMethod <- superMethods) {
          libraryInterfaceMethod |= isLibraryInterfaceMethod(superMethod)
        }
        if (!libraryInterfaceMethod) {
          methodsNotImplementingLibraryInterfaces += enclosing
        }
      }
      if (methodsNotImplementingLibraryInterfaces.size > 0) {
        return methodsNotImplementingLibraryInterfaces
      }
    }
    return enclosingMethods
  }

  private def getTextForElement(method: ScFunctionDefinition): String = {
    val res: StringBuilder = new StringBuilder(method.getName)
    method.containingClass match {
      case Some(td) =>
        val qual = td.getQualifiedName
        if (qual == null) res.append(" (<anonymous>)")
        else res.append(" (").append(qual).append(")")
      case _ => res.append(" (<local>)")
    }
    res.toString
  }

  def chooseEnclosingMethod(project: Project, editor: Editor,
                            file: PsiFile, startOffset: Int, endOffset: Int,
                            expr: ScExpression, typez: ScType) {
    val validEnclosingMethods: Seq[ScFunctionDefinition] = getEnclosingMethods(expr)
    if (validEnclosingMethods.size > 1 && !ApplicationManager.getApplication.isUnitTestMode) {
      ScalaRefactoringUtil.showChooser[ScFunctionDefinition](editor, validEnclosingMethods.toArray, {selectedValue =>
        runDialog(project, editor, file, startOffset, endOffset,
          selectedValue.asInstanceOf[ScFunctionDefinition], expr, typez)
      }, "Choose level for Extract Method", getTextForElement _, false)
    }
    else if (validEnclosingMethods.size == 1) {
      runDialog(project, editor, file, startOffset, endOffset, validEnclosingMethods(0), expr, typez)
    } else runDialog(project, editor, file, startOffset, endOffset, null, expr, typez)
  }

  private def isLibraryInterfaceMethod(method: PsiMethod): Boolean = {
    return (method.hasModifierProperty(PsiModifier.ABSTRACT) || method.isInstanceOf[ScFunctionDefinition]) &&
      !method.getManager.isInProject(method)
  }
}