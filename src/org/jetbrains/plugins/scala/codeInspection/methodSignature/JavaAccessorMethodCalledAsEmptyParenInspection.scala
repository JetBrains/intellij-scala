package org.jetbrains.plugins.scala
package codeInspection.methodSignature

import com.intellij.codeInspection._
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.codeInspection.ProblemsHolderExt
import org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix.RemoveCallParentheses
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.{ScTypeExt, api}
import org.jetbrains.plugins.scala.lang.resolve.processor.CollectMethodsProcessor

/**
 * Pavel Fatin
 */

class JavaAccessorMethodCalledAsEmptyParenInspection extends AbstractMethodSignatureInspection(
  "ScalaJavaAccessorMethodCalledAsEmptyParen", "Java accessor method called as empty-paren") {

  def actionFor(holder: ProblemsHolder) = {
    case e: ScReferenceExpression => e.getParent match {
      case call: ScMethodCall =>
        call.getParent match {
          case callParent: ScMethodCall => // do nothing
          case _ => if (call.argumentExpressions.isEmpty) {
            implicit val typeSystem = holder.typeSystem
            e.resolve() match {
              case _: ScalaPsiElement => // do nothing
              case (m: PsiMethod) if m.isAccessor && !isOverloadedMethod(e) && hasSameType(call, e) =>
                holder.registerProblem(e.nameId, getDisplayName, new RemoveCallParentheses(call))
              case _ =>
            }
          }
        }
      case _ =>
    }
  }

  private def isOverloadedMethod(ref: ScReferenceExpression)
                                (implicit typeSystem: TypeSystem) = {
    val processor = new CollectMethodsProcessor(ref, ref.refName)
    ref.bind().flatMap(_.fromType).forall(processor.processType(_, ref))
    processor.candidatesS.size > 1
  }

  private def hasSameType(call: ScMethodCall, ref: ScReferenceExpression)
                         (implicit typeSystem: api.TypeSystem) = {
    val callType = call.getType().toOption
    val refType = ref.getType().toOption
    (callType, refType) match {
      case (Some(t1), Some(t2)) => t1.equiv(t2)
      case _ => false
    }
  }
}
