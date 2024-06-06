package org.jetbrains.plugins.scala.lang

import org.jetbrains.plugins.scala.lang.psi.types.{PresentationTypeUpdaters, ScType, TypePresentationContext}

package object refactoring {
  implicit class ScTypePresentationExt(private val tpe: ScType) extends AnyVal {
    def simplifyForPresentation: ScType                         = tpe.recursiveUpdate(PresentationTypeUpdaters.cleanUp)
    def codeText(implicit ctx: TypePresentationContext): String = tpe.simplifyForPresentation.presentableText
    def canonicalCodeText: String                               = canonicalCodeText(TypePresentationContext.emptyContext)
    def canonicalCodeText(ctx: TypePresentationContext): String = tpe.simplifyForPresentation.canonicalText(ctx)
  }
}
