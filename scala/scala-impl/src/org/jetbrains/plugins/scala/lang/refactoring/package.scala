package org.jetbrains.plugins.scala.lang

import org.jetbrains.plugins.scala.lang.psi.types.{PresentationTypeUpdaters, ScType, TypePresentationContext}

package object refactoring {
  implicit class ScTypePresentationExt(val tpe: ScType) extends AnyVal {
    def simplifyForPresentation: ScType                         = tpe.recursiveUpdateImpl(PresentationTypeUpdaters.cleanUp)
    def codeText(implicit ctx: TypePresentationContext): String = tpe.simplifyForPresentation.presentableText
    def canonicalCodeText: String                               = tpe.simplifyForPresentation.canonicalText
  }
}
