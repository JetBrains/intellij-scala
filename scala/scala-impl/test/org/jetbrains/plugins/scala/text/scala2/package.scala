package org.jetbrains.plugins.scala.text

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

package object scala2 {
  private[scala2] implicit val scalaVersion: ScalaVersion = LatestScalaVersions.Scala_2_13
}