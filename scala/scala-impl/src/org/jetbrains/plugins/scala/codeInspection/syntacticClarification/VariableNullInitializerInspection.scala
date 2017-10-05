package org.jetbrains.plugins.scala.codeInspection
package syntacticClarification

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.syntacticClarification.VariableNullInitializerInspection._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api._

/**
  * Created by a.tsukanov on 26.05.2016.
  */
class VariableNullInitializerInspection extends AbstractInspection(inspectionId, inspectionName) {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case definition: ScVariableDefinition if !definition.isLocal =>
      definition.expr.filter {
        _.isValid
      }.filter {
        isNull
      }.foreach { expression =>
        def registerProblem() = holder.registerProblem(expression,
          inspectionName,
          new NullToUnderscoreQuickFix(definition))

        definition.declaredType.filter {
          isApplicable
        }.foreach { _ =>
          registerProblem()
        }
      }
  }
}

object VariableNullInitializerInspection {
  val inspectionName = InspectionBundle.message("convert.null.initializer.to.underscore")
  val inspectionId = "ScalaConvertNullInitializerToUnderscore"

  private def isApplicable(`type`: ScType): Boolean = {
    `type` match {
      case t if t.isUnit => true
      case _: ValType => false
      case _ => true
    }
  }

  def isNull(expr: ScExpression): Boolean =
    Option(expr).flatMap { expression =>
      Option(expression.getFirstChild)
    }.flatMap { element =>
      Option(element.getNode)
    }.map {
      _.getElementType
    }.exists {
      case ScalaTokenTypes.kNULL => true
      case _ => false
    }
}

class NullToUnderscoreQuickFix(definition: ScVariableDefinition)
  extends AbstractFixOnPsiElement(inspectionName, definition) {

  override def doApplyFix(project: Project): Unit = Option(getElement).flatMap {
    _.expr
  }.filter {
    isNull
  }.foreach { definition =>
    definition.replace(createExpressionFromText("_")(definition.getManager))
  }
}
