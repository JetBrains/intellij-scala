package org.jetbrains.plugins.scala.codeInspection.hashCodeUsesVar

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.psi._
import com.siyeh.ig.psiutils.MethodUtils
import org.jetbrains.plugins.scala.codeInspection.{PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariable

class HashCodeUsesVarInspection extends LocalInspectionTool {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case hashCodeMethod: PsiMethod if MethodUtils.isHashCode(hashCodeMethod) =>
      hashCodeMethod.accept(new ScalaRecursiveElementVisitor {
        override def visitReferenceExpression(exp: ScReferenceExpression): Unit = {
          super.visitReferenceExpression(exp)
          exp.resolve() match {
            case field: ScReferencePattern =>
              field.nameContext match {
                case variable: ScVariable if variable.isDefinedInClass =>
                  holder.registerProblem(exp, ScalaInspectionBundle.message("displayname.non.value.field.is.accessed.in.hashcode"))
                case _ =>
              }
            case _ =>
          }
        }
      })
    case _ =>
  }
}
