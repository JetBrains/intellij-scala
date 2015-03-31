package org.jetbrains.plugins.scala
package lang
package refactoring
package introduceParameter


import com.intellij.ide.util.SuperMethodWarningUtil
import com.intellij.internal.statistic.UsageTrigger
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.refactoring.{RefactoringActionHandler, RefactoringBundle}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScMethodLike, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.refactoring.introduceParameter.ScalaIntroduceParameterHandler._
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.{IntroduceException, showErrorMessage}
import org.jetbrains.plugins.scala.lang.refactoring.util.{DialogConflictsReporter, ScalaRefactoringUtil, ScalaVariableValidator}

import scala.collection.mutable.ArrayBuffer

/**
 * User: Alexander Podkhalyuzin
 * Date: 11.06.2009
 */
class ScalaIntroduceParameterHandler extends RefactoringActionHandler with DialogConflictsReporter {

  def invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
    val canBeIntroduced: (ScExpression) => Boolean = ScalaRefactoringUtil.checkCanBeIntroduced(_)
    ScalaRefactoringUtil.afterExpressionChoosing(project, editor, file, dataContext, "Introduce Parameter", canBeIntroduced) {
      ScalaRefactoringUtil.trimSpacesAndComments(editor, file)
      invoke(project, editor, file, editor.getSelectionModel.getSelectionStart, editor.getSelectionModel.getSelectionEnd)
    }
  }

  def invoke(project: Project, editor: Editor, file: PsiFile, startOffset: Int, endOffset: Int) {
    try {
      UsageTrigger.trigger(ScalaBundle.message("introduce.parameter.id"))

      PsiDocumentManager.getInstance(project).commitAllDocuments()
      ScalaRefactoringUtil.checkFile(file, project, editor, REFACTORING_NAME)

      val (expr: ScExpression, types: Array[ScType]) = ScalaRefactoringUtil.getExpression(project, editor, file, startOffset, endOffset).
              getOrElse(showErrorMessage(ScalaBundle.message("cannot.refactor.not.expression"), project, editor, REFACTORING_NAME))

      chooseEnclosingMethod(project, editor, file, startOffset, endOffset, expr, types)
    }
    catch {
      case _: IntroduceException =>
    }
  }

  def runDialog(project: Project, editor: Editor, file: PsiFile, startOffset: Int, endOffset: Int,
                methodLike: ScMethodLike, expr: ScExpression, types: Array[ScType]) {
    try {
      if (methodLike == null) {
        showErrorMessage(ScalaBundle.message("cannot.refactor.no.function"), project, editor, REFACTORING_NAME)
      }

      val methodToSearchFor: PsiMethod = SuperMethodWarningUtil.checkSuperMethod(methodLike, RefactoringBundle.message("to.refactor"))
      if (methodToSearchFor == null) return
      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, methodToSearchFor)) return
      
      val occurrencesScope = methodLike match {
        case ScFunctionDefinition.withBody(body) => body
        case pc: ScPrimaryConstructor => pc.containingClass.extendsBlock
        case _ => methodLike
      }

      val occurrences: Array[TextRange] =
        ScalaRefactoringUtil.getOccurrenceRanges(ScalaRefactoringUtil.unparExpr(expr), occurrencesScope)
      // Getting settings
      val validator = new ScalaVariableValidator(this, project, expr, occurrences.isEmpty, methodLike, methodLike)
      // Add occurrences highlighting
      if (occurrences.length > 1)
        ScalaRefactoringUtil.highlightOccurrences(project, occurrences, editor)

      val possibleNames = NameSuggester.suggestNames(expr, validator)
      val dialog = new ScalaIntroduceParameterDialog(project, editor, types, occurrences,
        validator, possibleNames, methodToSearchFor, startOffset, endOffset, methodLike, expr)
      dialog.show()
    }
    catch {
      case _: IntroduceException =>
    }
  }

  def invoke(project: Project, elements: Array[PsiElement], dataContext: DataContext) {/*do nothing*/}

  private def getEnclosingMethods(expr: PsiElement): Seq[ScMethodLike] = {
    var enclosingMethods = new ArrayBuffer[ScMethodLike]
    var elem: PsiElement = expr
    while (elem != null) {
      val newFun = PsiTreeUtil.getContextOfType(elem, true, classOf[ScFunctionDefinition], classOf[ScClass])
      newFun match {
        case f @ ScFunctionDefinition.withBody(body) if PsiTreeUtil.isContextAncestor(body, expr, false) =>
          enclosingMethods += f
        case cl: ScClass => enclosingMethods ++= cl.constructor
        case _ =>
      }
      elem = newFun
    }
    if (enclosingMethods.size > 1) {
      val methodsNotImplementingLibraryInterfaces = enclosingMethods.filter {
        case f: ScFunctionDefinition if f.superMethods.exists(isLibraryInterfaceMethod) => false
        case _ => true
      }
      if (methodsNotImplementingLibraryInterfaces.nonEmpty)
        return methodsNotImplementingLibraryInterfaces
    }
    enclosingMethods
  }

  private def getTextForElement(method: ScMethodLike): String = {
    method match {
      case pc: ScPrimaryConstructor => pc.containingClass.name
      case (f: ScFunctionDefinition) && ContainingClass(c: ScNewTemplateDefinition) => s"${f.name} (<anonymous>)"
      case (f: ScFunctionDefinition) && ContainingClass(c) => s"${f.name} (${c.qualifiedName})"
      case f: ScFunctionDefinition => s"${f.name} (<local>)"
    }
  }

  def chooseEnclosingMethod(project: Project, editor: Editor,
                            file: PsiFile, startOffset: Int, endOffset: Int,
                            expr: ScExpression, types: Array[ScType]) {
    val validEnclosingMethods: Seq[ScMethodLike] = getEnclosingMethods(expr)
    if (validEnclosingMethods.size > 1 && !ApplicationManager.getApplication.isUnitTestMode) {
      ScalaRefactoringUtil.showChooser[ScMethodLike](editor, validEnclosingMethods.toArray, {selectedValue =>
        runDialog(project, editor, file, startOffset, endOffset,
          selectedValue.asInstanceOf[ScMethodLike], expr, types)
      }, s"Choose function for $REFACTORING_NAME", getTextForElement, false)
    }
    else if (validEnclosingMethods.size == 1) {
      runDialog(project, editor, file, startOffset, endOffset, validEnclosingMethods(0), expr, types)
    } else runDialog(project, editor, file, startOffset, endOffset, null, expr, types)
  }

  private def isLibraryInterfaceMethod(method: PsiMethod): Boolean = {
    (method.hasModifierPropertyScala(PsiModifier.ABSTRACT) || method.isInstanceOf[ScFunctionDefinition]) &&
      !method.getManager.isInProject(method)
  }
}

object ScalaIntroduceParameterHandler {
  val REFACTORING_NAME = ScalaBundle.message("introduce.parameter.title")
}