package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class ImplicitConversionArgumentsWeakConformanceTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL17570(): Unit = checkTextHasNoErrors(
    s"""
       |object A {
       | val l: Long = 1
       | val l2: java.lang.Long = 1
       |}
       |""".stripMargin
  )
}
