package org.jetbrains.plugins.scala
package lang.refactoring.introduceField

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.refactoring.introduceField.ScalaIntroduceFieldHandlerBase._
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.{IntroduceException, getOccurrenceRanges}
import org.jetbrains.plugins.scala.lang.refactoring.util.{DialogConflictsReporter, ScalaVariableValidator, ValidationReporter}

import java.{util => ju}
import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters._

class IntroduceFieldContext[T <: PsiElement](val project: Project,
                                             val editor: Editor,
                                             val file: PsiFile,
                                             val element: T,
                                             val types: ArraySeq[ScType],
                                             val aClass: ScTemplateDefinition) {

  val occurrences: Seq[TextRange] = element match {
    case expr: ScExpression => getOccurrenceRanges(expr, aClass.extendsBlock)
    case _ => null
  }

  private val validator: ScalaVariableValidator = ScalaVariableValidator(file, element, occurrences)

  val reporter: ValidationReporter = new ValidationReporter(project, new DialogConflictsReporter {}, validator)

  val canBeInitInDecl: Boolean = element match {
    case expr: ScExpression => canBeInitializedInDeclaration(expr, aClass)
    case _ => throw new IntroduceException
  }

  val possibleNames: ju.Set[String] = element match {
    case expr: ScExpression =>
      NameSuggester.suggestNames(expr, ScalaVariableValidator(file, element, occurrences), types).toSet[String].asJava
    case _ => throw new IntroduceException
  }

  def canBeInitLocally(replaceAll: Boolean): Boolean = ScalaIntroduceFieldHandlerBase.canBeInitInLocalScope(this, replaceAll)
}
