package org.jetbrains.plugins.scala
package lang.refactoring.introduceField

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.refactoring.introduceField.ScalaIntroduceFieldHandlerBase._
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.IntroduceException
import org.jetbrains.plugins.scala.lang.refactoring.util.{DialogConflictsReporter, ScalaRefactoringUtil, ScalaVariableValidator}


/**
 * Nikolay.Tropin
 * 7/15/13
 */
class IntroduceFieldContext[T <: PsiElement](val project: Project,
                                             val editor: Editor,
                                             val file: PsiFile,
                                             val element: T,
                                             val types: Array[ScType],
                                             val aClass: ScTemplateDefinition) {

  val occurrences = element match {
    case expr: ScExpression =>
      ScalaRefactoringUtil.getOccurrenceRanges(ScalaRefactoringUtil.unparExpr(expr), aClass.extendsBlock)
    case _ => null
  }

  val validator = ScalaVariableValidator(new DialogConflictsReporter {}, project, editor, file, element, occurrences)

  val canBeInitInDecl = element match {
    case expr: ScExpression => canBeInitializedInDeclaration(expr, aClass)
    case _ => throw new IntroduceException
  }

  val possibleNames = element match {
    case expr: ScExpression => NameSuggester.suggestNames(expr, validator)
    case _ => throw new IntroduceException
  }

  def canBeInitLocally(replaceAll: Boolean) = ScalaIntroduceFieldHandlerBase.canBeInitInLocalScope(this, replaceAll)
}
