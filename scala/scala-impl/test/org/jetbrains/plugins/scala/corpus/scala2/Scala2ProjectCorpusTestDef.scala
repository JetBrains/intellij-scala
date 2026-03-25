package org.jetbrains.plugins.scala.corpus
package scala2

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

abstract class Scala2ProjectCorpusTestDef extends ProjectCorpusTestDef {
  implicit final override val scalaVersion: ScalaVersion = LatestScalaVersions.Scala_2_13
}