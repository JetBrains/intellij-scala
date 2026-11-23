package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.{CheckTestDataTestBase, LatestScalaVersions, ScalaVersion}

sealed abstract class CheckConstructorClauseOmissionTestBase(scalaVersion: ScalaVersion)
  extends CheckTestDataTestBase(ConstructorClauseOmissionTest.testData, scalaVersion) {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == scalaVersion
}

final class CheckConstructorClauseOmissionTest_3_3 extends CheckConstructorClauseOmissionTestBase(LatestScalaVersions.Scala_3_3)
final class CheckConstructorClauseOmissionTest_3_9 extends CheckConstructorClauseOmissionTestBase(LatestScalaVersions.Scala_3_9)
