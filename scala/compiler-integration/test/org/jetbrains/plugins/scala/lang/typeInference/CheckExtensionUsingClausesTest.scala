package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.{CheckTestDataTestBase, LatestScalaVersions, ScalaVersion}

sealed abstract class CheckExtensionUsingClausesTestBase(scalaVersion: ScalaVersion)
  extends CheckTestDataTestBase(ExtensionUsingClausesTest.testData, scalaVersion) {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == scalaVersion
}

final class CheckExtensionUsingClausesTest_3_3 extends CheckExtensionUsingClausesTestBase(LatestScalaVersions.Scala_3_3)
final class CheckExtensionUsingClausesTest_3_9 extends CheckExtensionUsingClausesTestBase(LatestScalaVersions.Scala_3_9)
