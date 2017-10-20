package org.jetbrains.plugins.scala.codeInspection.hashCodeUsesVar

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi._
import com.siyeh.ig.psiutils.MethodUtils
import org.jetbrains.plugins.scala.codeInspection.AbstractInspection
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariable

/**
  * Daniyar Itegulov
  * 2016-02-08
  */
class HashCodeUsesVarInspection extends AbstractInspection {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case hashCodeMethod: PsiMethod if MethodUtils.isHashCode(hashCodeMethod) =>
      hashCodeMethod.accept(new ScalaRecursiveElementVisitor {
        override def visitReferenceExpression(exp: ScReferenceExpression): Unit = {
          super.visitReferenceExpression(exp)
          exp.resolve() match {
            case field: ScReferencePattern =>
              field.nameContext match {
                case variable: ScVariable if !variable.isLocal =>
                  holder.registerProblem(exp, "Non-value field is accessed in 'hashCode()'")
                case _ =>
              }
            case _ =>
          }
        }
      })
  }
}
