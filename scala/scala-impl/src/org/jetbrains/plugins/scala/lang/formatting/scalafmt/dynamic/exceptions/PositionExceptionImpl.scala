package org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.exceptions

case class PositionExceptionImpl(
  file: Option[String],
  code: String,
  shortMessage: String,
  longMessage: String,
  pos: RangePosition,
  cause: Throwable
) extends RuntimeException(cause) {

  def start: Int = pos.start
  def end: Int = pos.end
  def startLine: Int = pos.startLine
  def startCharacter: Int = pos.startCharacter
  def endLine: Int = pos.endLine
  def endCharacter: Int = pos.endCharacter
}
