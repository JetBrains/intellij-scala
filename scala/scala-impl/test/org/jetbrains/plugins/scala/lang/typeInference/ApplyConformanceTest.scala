package org.jetbrains.plugins.scala
package lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * @author anton.yalyshev
  * @since 07.09.18.
  */
@Category(Array(classOf[TypecheckerTests]))
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

  def testSCL11912(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object test {
         |  final case class Kleisli[F[_], A, B](run: A => F[B])
         |  val f = Kleisli { (x: Int) => Some(x + 1) }
         |}
      """.stripMargin)
  }
}
