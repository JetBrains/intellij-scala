package org.jetbrains.plugins.scala.codeInspection.dataFlow

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.AbstractInspection
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScParameterImpl


/**
  * Created by ssdmitriev on 04.02.16.
  */
class UnusedFunctionParameterInspection
  extends AbstractInspection("UnusedFunctionParameter", "function parameter not used") {
  override def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case expr: ScFunctionDefinition =>
      expr.parameters.foreach(parameter =>
        if (findUnusedParameter(expr, parameter)) {
          val message = "function parameter not used"
          holder.registerProblem(parameter, message)
        })
  }

  def findUnusedParameter(f: ScFunctionDefinition, parameter: ScParameter): Boolean = {
    var unusedParameter = true
    val visitor = new ScalaRecursiveElementVisitor() {
      override def visitReferenceExpression(ref: ScReferenceExpression): Unit = {
        if (ref.resolve().getText == parameter.getText) {
          ref.resolve() match {
            case p: ScParameterImpl =>
              unusedParameter = false
            case _ =>
          }
        }
      }
    }
    f.body.foreach(_.accept(visitor))
    unusedParameter
  }
}
