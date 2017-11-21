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
      val maybeDeclaredType = definition.declaredType
        .filter(isApplicable)

      val maybeExpression = definition.expr
        .filter(_.isValid)
        .filter(isNull)

      maybeExpression.zip(maybeDeclaredType).foreach {
        case (expression, _) =>
          holder.registerProblem(expression, inspectionName, new NullToUnderscoreQuickFix(definition))
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

  override protected def doApplyFix(element: ScVariableDefinition)
                                   (implicit project: Project): Unit = {
    element.expr
      .filter(isNull)
      .foreach(_.replace(createExpressionFromText("_")))
  }
}
