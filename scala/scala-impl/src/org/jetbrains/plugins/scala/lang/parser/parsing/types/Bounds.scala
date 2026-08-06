package org.jetbrains.plugins.scala.lang.parser.parsing.types

import org.jetbrains.plugins.scala.lang.parser.ErrMsg
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

object Bounds {
  val UPPER      : String = "<:"
  val LOWER      : String = ">:"
  val VIEW       : String = "<%" // deprecated in 2.13

  def apply(bound: String)(implicit builder: ScalaPsiBuilder): Boolean =
    if (builder.getTokenText == bound) {
      builder.advanceLexer()
      if (!Type()) {
        builder.error(ErrMsg("wrong.type"))
      }
      true
    } else false

  def parseSubtypeBounds()(implicit builder: ScalaPsiBuilder): Unit = {
    Bounds(Bounds.LOWER)
    Bounds(Bounds.UPPER)
  }
}