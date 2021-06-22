package org.jetbrains.plugins.scala.codeInsight.intention.types

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaBundle, ScalaVersion}
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

abstract class ConvertImplicitBoundsToImplicitParameterTestBase extends ScalaIntentionTestBase {

  override def familyName: String = ScalaBundle.message("family.name.convert.implicit.bounds")

  def doMyTest(text: String, expected: String): Unit =
    doTest(text.replaceFirst(raw"\[", "[" + CARET), expected)

  def test_one_bound(): Unit = doMyTest(
    "def test[T: A]: Int = 0",
    "def test[T](implicit a: A[T]): Int = 0"
  )

  def test_one_bound_with_empty_clause(): Unit = doMyTest(
    "def test[T: A](): Unit = ()",
    "def test[T]()(implicit a: A[T]): Unit = ()"
  )

  def test_two_bound(): Unit = doMyTest(
    "def test[T1: A, T2: A](): Unit = ()",
    "def test[T1, T2]()(implicit aT1: A[T1], aT2: A[T2]): Unit = ()"
  )

  def test_class(): Unit = doMyTest(
    "class Test[T: A]",
    "class Test[T](implicit a: A[T])"
  )
}

class ConvertImplicitBoundsToImplicitParameterTest_Scala2 extends ConvertImplicitBoundsToImplicitParameterTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version <= LatestScalaVersions.Scala_2_13

  def test_already_existing_clause(): Unit = doMyTest(
    "def test[T: A, TT: B](implicit x: X): Unit = ()",
    "def test[T, TT](implicit x: X, a: A[T], b: B[TT]): Unit = ()"
  )
}

class ConvertImplicitBoundsToImplicitParameterTest_Scala3 extends ConvertImplicitBoundsToImplicitParameterTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version > LatestScalaVersions.Scala_2_13

  override def doMyTest(text: String, expected: String): Unit =
    super.doMyTest(text.replace("implicit", "using"), expected.replace("implicit", "using"))

  def test_already_existing_clause(): Unit = doMyTest(
    "def test[T: A, TT: B](using x: X): Unit = ()",
    "def test[T, TT](using x: X)(using a: A[T], b: B[TT]): Unit = ()"
  )
}