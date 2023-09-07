package org.jetbrains.plugins.scala.base

sealed trait SdkConfiguration

object SdkConfiguration {
  case object FullJdk extends SdkConfiguration

  final case class IncludedModules(modules: Seq[String]) extends SdkConfiguration
}
