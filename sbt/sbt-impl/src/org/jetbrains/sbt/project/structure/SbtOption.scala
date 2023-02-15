package org.jetbrains.sbt.project.structure

trait SbtOption {
  def value: String
}
object SbtOption {
  trait JvmOption extends SbtOption
  case class JvmOptionGlobal(override val value: String) extends JvmOption
  case class JvmOptionShellOnly(override val value: String) extends JvmOption
  case class SbtLauncherOption(override val value: String) extends SbtOption
}