package org.jetbrains.plugins.scala
package codeInspection
package methodSignature

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

/**
 * Pavel Fatin
 */
final class UnitMethodDefinedLikeFunctionInspection extends AbstractInspection("Method with Unit result type defined like function") {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case f: ScFunctionDefinition if f.hasUnitResultType =>
      f.returnTypeElement.foreach { e =>
        holder.registerProblem(e, getDisplayName, new quickfix.RemoveTypeAnnotationAndEqualSign(f))
      }
  }
}