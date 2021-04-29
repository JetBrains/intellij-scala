package org.jetbrains.plugins.scala.tasty

// TODO Cross-compile a tasty-api module instead of duplicating the classes

trait TastyApi {
  def read(classpath: String, tastyFile: String, rightHandSide: Boolean): Option[TastyFile]
}

case class TastyFile(source: String, text: String, references: Seq[ReferenceData], types: Seq[TypeData])

case class Position(file: String, start: Int,  end: Int)

case class ReferenceData(position: Position, target: Position)

case class TypeData(position: Position, presentation: String)
