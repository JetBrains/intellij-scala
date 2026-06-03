package org.jetbrains.plugins.scala
package lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class SyntheticUnapplyTupleTest extends ScalaLightCodeInsightFixtureTestCase {
  def testSCL16790(): Unit = checkTextHasNoErrors(
    """
      |case class SomeTestClass(param: (String, String))
      |val t = SomeTestClass("love" -> "life")
      |t match {
      |  case SomeTestClass((param1, param2)) =>
      |  case SomeTestClass(Tuple2(param1, param2)) =>
      |  case SomeTestClass(pair) =>
      |}
      |""".stripMargin
  )

  def testSCL16790Neg(): Unit = checkHasErrorAroundCaret(
    s"""
       |case class SomeTestClass(param: (String, String))
       |val t = SomeTestClass("love" -> "life")
       |t match {
       |  case SomeTestClass$CARET(t1, t2) =>
       |}
       |""".stripMargin
  )
}
