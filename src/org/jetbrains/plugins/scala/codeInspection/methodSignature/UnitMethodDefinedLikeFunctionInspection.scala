package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix.RemoveTypeAnnotationAndEqualSign
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

/**
 * Pavel Fatin
 */

class UnitMethodDefinedLikeFunctionInspection extends AbstractMethodSignatureInspection(
  "ScalaUnitMethodDefinedLikeFunction", "Method with Unit result type defined like function") {

  def actionFor(holder: ProblemsHolder) = {
    case f: ScFunctionDefinition if f.hasUnitResultType =>
      f.returnTypeElement.foreach { e =>
        holder.registerProblem(e, getDisplayName, new RemoveTypeAnnotationAndEqualSign(f))
      }
  }
}