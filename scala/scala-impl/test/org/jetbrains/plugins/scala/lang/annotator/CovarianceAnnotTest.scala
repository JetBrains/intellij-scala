package org.jetbrains.plugins.scala
package lang.annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * Created by Anton Yalyshev on 16/05/16.
  */
@Category(Array(classOf[LanguageTests]))
class CovarianceAnnotTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL10263(): Unit = {
    checkTextHasNoErrors(
      """
        |trait Foo[+A] {
        |  trait Bar
        |}
        |
        |trait Baz[+A] {
        |  val x: Foo[A]#Bar
        |}
      """.stripMargin
    )
  }

  def testSCL14032(): Unit = {
    val text =
      """
        |class Queue[+T] private (private[this] var leading: List[T],
        |                         private[this] var trailing: List[T])
      """.stripMargin
    checkTextHasNoErrors(text)
  }
}