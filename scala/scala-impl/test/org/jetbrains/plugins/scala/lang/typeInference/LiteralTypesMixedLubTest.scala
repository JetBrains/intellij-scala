package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class LiteralTypesMixedLubTest extends TypeInferenceTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_13

  def testLiteralAndUnit(): Unit = doTest(
    s"""${START}if (true) 1 else ()$END
       |//AnyVal""".stripMargin
  )

  def testUnitAndLiteral(): Unit = doTest(
    s"""${START}if (true) () else 1$END
       |//AnyVal""".stripMargin
  )

  def testLiteralAndWiderPrimitive(): Unit = doTest(
    s"""def n: Long = 2L
       |${START}if (true) 1 else n$END
       |//Long""".stripMargin
  )

  def testIdenticalLiterals(): Unit = doTest(
    s"""${START}if (true) 1 else 1$END
       |//1""".stripMargin
  )
}
