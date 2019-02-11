package org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.exceptions

case class RangePosition(
    startLine: Int,
    startCharacter: Int,
    endLine: Int,
    endCharacter: Int
)
