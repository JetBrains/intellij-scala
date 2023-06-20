package org.jetbrains.sbt.project.module

sealed abstract class SourceModule (val kind: String)
object SourceModule {
  final case object Production extends SourceModule(kind = "main")
  final case object Test extends SourceModule(kind = "test")

  val externalModuleType = "sourceModule"
}