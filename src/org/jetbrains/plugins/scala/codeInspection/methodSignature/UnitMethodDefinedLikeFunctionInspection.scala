package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix.RemoveTypeAnnotationAndEqualSign
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

/**
 * Pavel Fatin
 */

class UnitMethodDefinedLikeFunctionInspection extends AbstractMethodSignatureInspection(
  "ScalaUnitMethodDefinedLikeFunction", "Method with Unit result type defined like function") {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case f: ScFunctionDefinition if f.hasUnitResultType =>
      f.returnTypeElement.foreach { e =>
        holder.registerProblem(e, getDisplayName, new RemoveTypeAnnotationAndEqualSign(f))
      }
  }
}