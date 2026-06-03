package org.jetbrains.plugins.scala.textAnalysis.grazie

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.project.ScalaFeatures

class ScalaTextExtractorTest_213 extends ScalaTextExtractorTest_CommonTests {
  override def scalaFeatures: ScalaFeatures = ScalaFeatures.onlyByVersion(ScalaVersion.Latest.Scala_2_13)
}
