package org.jetbrains.plugins.scala.lang

import org.jetbrains.plugins.scala.lang.psi.types.{Context, PresentationTypeUpdaters, ScType, TypePresentationContext}

package object refactoring {
  implicit class ScTypePresentationExt(private val tpe: ScType) extends AnyVal {
    def simplifyForPresentation: ScType                         = tpe.recursiveUpdate(PresentationTypeUpdaters.cleanUp)
    def codeText(implicit ctx: TypePresentationContext, context: Context): String = tpe.simplifyForPresentation.presentableText
    def canonicalCodeText(implicit context: Context, ctx: TypePresentationContext): String = tpe.simplifyForPresentation.canonicalText(ctx)
    def canonicalCodeText(ctx: TypePresentationContext)(implicit context: Context): String = tpe.simplifyForPresentation.canonicalText(ctx)
  }
}
