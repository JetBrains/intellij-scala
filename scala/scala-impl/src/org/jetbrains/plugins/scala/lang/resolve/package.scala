package org.jetbrains.plugins.scala.lang

import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction

package object resolve {
  implicit class ScalaResolveResultUtils(private val srr: ScalaResolveResult) extends AnyVal {
    def elementHasParameters: Boolean =
      srr.element match {
        case synthetic: ScSyntheticFunction         => synthetic.paramClauses.nonEmpty
        case fn: ScFunction if !srr.isExtensionCall => fn.parameterClausesWithExtension().nonEmpty
        case m: PsiMethod                           => m.hasParameters
        case _                                      => false
      }

    def elementHasTypeParameters: Boolean =
      srr.element match {
        case synthetic: ScSyntheticFunction         => synthetic.typeParameters.nonEmpty
        case fn: ScFunction if !srr.isExtensionCall => fn.typeParametersWithExtension().nonEmpty
        case m: PsiMethod                           => m.hasTypeParameters
        case _                                      => false
      }
  }
}
