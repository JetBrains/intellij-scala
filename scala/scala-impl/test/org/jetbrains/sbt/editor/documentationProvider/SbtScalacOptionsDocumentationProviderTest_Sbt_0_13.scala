package org.jetbrains.sbt.editor.documentationProvider

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.sbt.MockSbt_0_13

class SbtScalacOptionsDocumentationProviderTest_Sbt_0_13 extends SbtScalacOptionsDocumentationProviderTestBase
  with SbtScalacOptionsDocumentationProviderCommonTests
  with MockSbt_0_13 {
  override protected def supportedIn(version: ScalaVersion) = true

  override protected def defaultVersionOverride = Some(LatestScalaVersions.Scala_2_12)
}
