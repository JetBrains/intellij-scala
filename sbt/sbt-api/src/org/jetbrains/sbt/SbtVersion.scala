package org.jetbrains.sbt

import org.jetbrains.plugins.scala.project.Version

final class SbtVersion(val value: Version) extends MinorVersionGenerator[Version] {
  override def minor: String = value.presentation
  override def generateNewVersion(version: String): Option[Version] = Some(Version(version))
}

object SbtVersion {
  def apply(value: String): SbtVersion = new SbtVersion(Version(value))

  implicit val sbtVersionOrdering: Ordering[SbtVersion] = Ordering.by(_.value)

  val allSbt1: Seq[SbtVersion] = Seq(
    SbtVersion("1.0.4"),
    SbtVersion("1.1.6"),
    SbtVersion("1.2.8"),
    SbtVersion("1.3.13"),
    SbtVersion("1.4.9"),
    SbtVersion("1.5.8"),
    SbtVersion("1.6.2"),
    SbtVersion("1.7.3"),
    SbtVersion("1.8.3"),
    SbtVersion("1.9.9"),
  )
}