package org.jetbrains.plugins.scala.compiler

object MissingScalaSdk {
  final val MessagePrefix = "Scala SDK missing"

  final val SkippedScalaSourcesMessagePrefix = "Skipped Scala sources without a Scala SDK in module "

  def skippedModuleMessage(moduleName: String): String =
    s"$SkippedScalaSourcesMessagePrefix [$moduleName]"
}
