package org.jetbrains.jps.incremental.scala.remote

sealed trait SourceScope

object SourceScope {
  case object Production extends SourceScope
  case object Test extends SourceScope

  def fromString(s: String): SourceScope = s match {
    case "Production" => Production
    case "Test" => Test
  }
}
