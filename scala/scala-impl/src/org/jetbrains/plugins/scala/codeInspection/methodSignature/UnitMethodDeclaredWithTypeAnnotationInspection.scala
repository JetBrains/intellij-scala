package org.jetbrains.plugins.scala
package codeInspection
package methodSignature

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDeclaration

/**
 * Pavel Fatin
 */
final class UnitMethodDeclaredWithTypeAnnotationInspection extends AbstractInspection("Redundant Unit result type annotation") {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case f: ScFunctionDeclaration if f.hasUnitResultType =>
      f.returnTypeElement.foreach { e =>
        holder.registerProblem(e, getDisplayName, new quickfix.RemoveTypeAnnotation(f))
      }
  }
}