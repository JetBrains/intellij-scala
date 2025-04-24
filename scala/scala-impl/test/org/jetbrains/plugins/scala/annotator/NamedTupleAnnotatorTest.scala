package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.ScalaVersion

class NamedTupleAnnotatorTest extends ScalaHighlightingTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_3_6

  // SCL-23479
  def test_named_tuple_type_element(): Unit = assertNoErrors(
    """
      |type S = Seq[(w: Double, s: String)]
      |""".stripMargin
  )

  def test_type_error_after_NTtoT_conversion(): Unit = assertErrorsText(
    """
      |val tup: (Int, Int) = (a = true, b = 1)
      |""".stripMargin,
    "Error((a = true, b = 1),Expression of type (Boolean, Int) doesn't conform to expected type (Int, Int))"
  )

  def test_size_error_after_NTtoT_conversion(): Unit = assertErrorsText(
    """
      |val tup: (Int, Int, Int) = (a = 1, b = 2)
      |""".stripMargin,
    "Error((a = 1, b = 2),Expression of type (Int, Int) doesn't conform to expected type (Int, Int, Int))"
  )

  def test_implicit_conversion_after_NTtoT_conversion(): Unit = assertNoErrors(
    s"""implicit def tupToTup(tup: (Boolean, Int)): (Int, Int) = tup)
       |
       |// has tuple conversion and implicit conversion!
       |val tup: (Int, Int) = (a = true, b = 1)
       |""".stripMargin
  )
}
