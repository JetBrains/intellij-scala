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

  // SCL-23811
  def test_name_duplication_error(): Unit = assertErrorsText(
    """val _ = (a = 1, a = 2)
      |val (b = _, b = _) = (b = 1)
      |type X = (c: Int, c: Int)
      |
      |""".stripMargin,
    """
      |Error(a,Duplicate name in named tuple: a)
      |Error(b,Duplicated name extractor: b)
      |Error(c,Duplicate name in named tuple: c)
      |""".stripMargin
  )

  def test_invalid_named_tuple_names(): Unit = assertErrorsText(
    """
      |val illformed1 = (_2 = 1)
      |val illformed2 = (_80 = 1)
      |type Illformed1 = (_1: Int)
      |type Illformed2 = (_80: Int)
      |""".stripMargin,
    """
      |Error(_2,_2 cannot be used as the name of a tuple element because it is a regular tuple selector)
      |Error(_80,_80 cannot be used as the name of a tuple element because it is a regular tuple selector)
      |Error(_1,_1 cannot be used as the name of a tuple element because it is a regular tuple selector)
      |Error(_80,_80 cannot be used as the name of a tuple element because it is a regular tuple selector)
      |""".stripMargin
  )
}
