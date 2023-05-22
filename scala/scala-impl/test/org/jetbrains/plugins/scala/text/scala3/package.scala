package org.jetbrains.plugins.scala.text

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

package object scala3 {
  private[scala3] implicit val scalaVersion: ScalaVersion = LatestScalaVersions.Scala_3
}