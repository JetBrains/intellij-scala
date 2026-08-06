package org.jetbrains.plugins.scala.textAnalysis.grazie

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.project.ScalaFeatures

class ScalaTextExtractorTest_3 extends ScalaTextExtractorTest_CommonTests {

  // Scala 3 does not support Unicode escape sequences and treatts it as a text
  override protected val CommonStringInnerContent_WithEscapes1_Extracted_AsText = "example \\n text \\t \\r with \\u0024 escapes \\uuuuu0024 haha"

  override def scalaFeatures: ScalaFeatures = ScalaFeatures.onlyByVersion(ScalaVersion.Latest.Scala_3)
}
