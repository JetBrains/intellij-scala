package org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.utils

case object BuildInfo {
  /** The value is "dynamic". */
  val name: String = "dynamic"
  /** The value is "2.0.0-RC4+34-ce4e2199+20190210-1617-SNAPSHOT". */
  val version: String = "2.0.0-RC4+34-ce4e2199+20190210-1617-SNAPSHOT"
  /** The value is "4.0.0". */
  val scalameta: String = "4.0.0"
  /** The value is "2.0.0-RC4+34-ce4e2199+20190210-1617-SNAPSHOT". */
  val nightly: String = "2.0.0-RC4+34-ce4e2199+20190210-1617-SNAPSHOT"
  /** The value is "2.0.0-RC4". */
  val stable: String = "2.0.0-RC4"
  /** The value is "2.12.8". */
  val scala: String = "2.12.8"
  /** The value is "2.11.12". */
  val scala211: String = "2.11.12"
  /** The value is "1.0.3". */
  val coursier: String = "1.0.3"
  /** The value is "ce4e2199157b4d4432335731427c84977fa871fc". */
  val commit: String = "ce4e2199157b4d4432335731427c84977fa871fc"
  /** The value is "1549804620575". */
  val timestamp: String = "1549804620575"
  /** The value is "2.12.8". */
  val scalaVersion: String = "2.12.8"
  /** The value is "1.2.6". */
  val sbtVersion: String = "1.2.6"
  override val toString: String = {
    "name: %s, version: %s, scalameta: %s, nightly: %s, stable: %s, scala: %s, scala211: %s, coursier: %s, commit: %s, timestamp: %s, scalaVersion: %s, sbtVersion: %s" format (
      name, version, scalameta, nightly, stable, scala, scala211, coursier, commit, timestamp, scalaVersion, sbtVersion
    )
  }
}
