package org.jetbrains.plugins.scala
package lang.refactoring.introduceField

import java.{util => ju}

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.refactoring.introduceField.ScalaIntroduceFieldHandlerBase._
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.IntroduceException
import org.jetbrains.plugins.scala.lang.refactoring.util.{DialogConflictsReporter, ScalaRefactoringUtil, ScalaVariableValidator, ValidationReporter}

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

  val occurrences: Array[TextRange] = element match {
    case expr: ScExpression =>
      ScalaRefactoringUtil.getOccurrenceRanges(ScalaRefactoringUtil.unparExpr(expr), aClass.extendsBlock)
    case _ => null
  }

  private implicit val validator = ScalaVariableValidator(file, element, occurrences)

  val reporter: ValidationReporter = new ValidationReporter(project, new DialogConflictsReporter {})

  val canBeInitInDecl: Boolean = element match {
    case expr: ScExpression => canBeInitializedInDeclaration(expr, aClass)
    case _ => throw new IntroduceException
  }

  val possibleNames: ju.Set[String] = element match {
    case expr: ScExpression =>
      import scala.collection.JavaConversions._
      NameSuggester.suggestNames(expr).toSet[String]
    case _ => throw new IntroduceException
  }

  def canBeInitLocally(replaceAll: Boolean): Boolean = ScalaIntroduceFieldHandlerBase.canBeInitInLocalScope(this, replaceAll)
}
