package org.jetbrains.sbt.language.utils

import org.jetbrains.plugins.scala.ScalaVersion

final case class SbtScalacOptionInfo(flag: String, description: String, scalaVersions: Set[ScalaVersion]) {
  def quoted: String = s""""$flag""""
}
