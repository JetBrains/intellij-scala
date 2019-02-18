package org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.exceptions

case class RangePosition(start: Int,
                         startLine: Int,
                         startCharacter: Int,
                         end: Int,
                         endLine: Int,
                         endCharacter: Int)
