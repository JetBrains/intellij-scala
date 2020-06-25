package org.jetbrains.plugins.scala.lang.psi.types.api.presentation

import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}

case class ScTypeText(tp: ScType)(implicit tpc: TypePresentationContext) {
  val canonicalText: String = tp.canonicalText
  val presentableText: String = tp.presentableText
}