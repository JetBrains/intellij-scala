package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.MultiScalaModulesInsightFixtureTestCase

class Scala2Scala3InteropUnapplyTest extends MultiScalaModulesInsightFixtureTestCase.Scala2DependingOnScala3LTS {
  def test_unapply(): Unit = doSimpleHighlightingTest(
    """
      |case class TestCaseClass(value: Int)
      |
      |""".stripMargin,
    """
      |object Blub {
      |  TestCaseClass(1) match {
      |    case TestCaseClass(i) =>
      |      val x: Int = i
      |  }
      |}
      |""".stripMargin
  )

  def test_generic_unapply(): Unit = doSimpleHighlightingTest(
    """
      |case class TestCaseClass[T](value: T)
      |
      |""".stripMargin,
    """
      |object Blub {
      |  TestCaseClass(1) match {
      |    case TestCaseClass(i) =>
      |      val x: Int = i
      |  }
      |}
      |""".stripMargin
  )

  def test_unapplySeq(): Unit = doSimpleHighlightingTest(
    """
      |case class TestCaseClass(value: Boolean, values: Int*)
      |""".stripMargin,
    """
      |object Blub {
      |  TestCaseClass(true, Seq(1)) match {
      |    case TestCaseClass(a, b) =>
      |      val x: Boolean = a
      |      val y: Int = b
      |  }
      |}
      |""".stripMargin
  )

  def test_generic_unapplySeq(): Unit = doSimpleHighlightingTest(
    """
      |case class TestCaseClass[A, B](value: A, values: B*)
      |
      |""".stripMargin,
    """
      |object Blub {
      |  TestCaseClass(true, 1) match {
      |    case TestCaseClass(a, b) =>
      |      val x: Boolean = a
      |      val y: Int = b
      |  }
      |}
      |""".stripMargin
  )
}

