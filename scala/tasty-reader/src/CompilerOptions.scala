package org.jetbrains.plugins.scala.tasty.reader

case class CompilerOptions(kindProjector: Boolean)

object CompilerOptions {
  val Default = CompilerOptions(kindProjector = false)
}
