package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
  * @author anton.yalyshev
  * @since 07.09.18.
  */
class ApplyConformanceTest extends ScalaLightCodeInsightFixtureTestAdapter {

  def testSCL13654(): Unit = {
    checkTextHasNoErrors(
      s"""
         |class Id {
         |    def apply(param: Int): Int =
         |      param
         |  }
         |
         |  implicit def id2function(clz: Id): String => String =
         |    str => clz(str.toInt).toString
         |
         |  val id = new Id
         |
         |  id { "1" }
      """.stripMargin)
  }
}
