package org.jetbrains.plugins.scala.lang.parser.parsing.types

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * [[FunLikeType]] ::= ([[MonoFunOrExistentialOrMatchType]] | [[PolyFunType]])
 */
object FunLikeType {
  def parse(star: Boolean, isPattern: Boolean = false)(implicit builder: ScalaPsiBuilder): Boolean =
    MonoFunOrExistentialOrMatchType.parse(star, isPattern) || PolyFunType.parse(star, isPattern)
}
