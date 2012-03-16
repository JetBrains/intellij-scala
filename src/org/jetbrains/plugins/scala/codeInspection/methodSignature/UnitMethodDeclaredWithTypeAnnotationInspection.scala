package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDeclaration
import quickfix.RemoveTypeAnnotation

/**
 * Pavel Fatin
 */

class UnitMethodDeclaredWithTypeAnnotationInspection extends AbstractMethodSignatureInspection(
  "ScalaUnitMethodDeclaredWithTypeAnnotation", "Redundant Unit result type annotation") {

  def actionFor(holder: ProblemsHolder) = {
    case f: ScFunctionDeclaration if f.hasUnitResultType =>
      f.returnTypeElement.foreach { e =>
        holder.registerProblem(e, getDisplayName, new RemoveTypeAnnotation(f))
      }
  }
}