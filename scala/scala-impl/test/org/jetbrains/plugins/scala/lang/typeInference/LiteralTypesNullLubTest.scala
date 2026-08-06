package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.LatestScalaVersions.Scala_2_13
import org.jetbrains.plugins.scala.ScalaVersion

class LiteralTypesNullLubTest extends TypeInferenceTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= Scala_2_13

  def testSCL18572(): Unit = doTest(
    s"""
      |${START}if (true) null else "123"$END
      |//"123"
      |""".stripMargin
  )

  def testSCL18572_AnyVal(): Unit = doTest(
    s"""
       |${START}if (true) null else 2d$END
       |//Any
       |""".stripMargin
  )
}
