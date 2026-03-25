package org.jetbrains.plugins.scala.corpus
package scala3

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

abstract class Scala3ProjectCorpusTestDef extends ProjectCorpusTestDef {
  implicit final override val scalaVersion: ScalaVersion = LatestScalaVersions.Scala_3
}