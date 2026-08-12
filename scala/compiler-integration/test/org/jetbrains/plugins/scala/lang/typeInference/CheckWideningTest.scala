package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.util.GeneratedParameterizedTestFactory.TestData
import org.jetbrains.plugins.scala.{CheckTestDataTestBase, LatestScalaVersions, ScalaVersion}

sealed abstract class CheckWideningTestBase(testData: Seq[TestData], minScalaVersion: ScalaVersion) extends CheckTestDataTestBase(testData, minScalaVersion)
final class CheckWideningTest_Scala2 extends CheckWideningTestBase(WideningTest.testDataInScala2, LatestScalaVersions.Scala_2_13)
final class CheckWideningTest_Scala3_LTS extends CheckWideningTestBase(WideningTest.testDataInScala3, LatestScalaVersions.Scala_3_LTS)
final class CheckWideningTest_Scala3_Next extends CheckWideningTestBase(WideningTest.testDataInScala3, LatestScalaVersions.Scala_3_Next_RC)

