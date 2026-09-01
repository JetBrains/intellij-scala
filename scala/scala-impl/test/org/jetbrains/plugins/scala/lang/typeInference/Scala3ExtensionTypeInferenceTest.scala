package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase

class Scala3ExtensionTypeInferenceTestLatest extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion) = version == ScalaVersion.Latest.Scala_3_Next_RC

  def testSCL25899(): Unit = {
    checkTextHasNoErrors(
      """
        |object A {
        |  1.isWhole
        |  2 max 3
        |}
        """.stripMargin
    )
  }
}
