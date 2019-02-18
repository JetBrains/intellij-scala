package org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.exceptions

import java.nio.file.Path

case class PositionExceptionImpl(file: Option[Path],
                                 code: String,
                                 shortMessage: String,
                                 longMessage: String,
                                 pos: RangePosition,
                                 cause: Throwable) extends RuntimeException {
  def start: Int = pos.start
  def end: Int = pos.end
  def startLine: Int = pos.startLine
  def startCharacter: Int = pos.startCharacter
  def endLine: Int = pos.endLine
  def endCharacter: Int = pos.endCharacter
}
