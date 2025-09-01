package org.jetbrains.plugins.scala.lang.formatter.intellij.tests.scala3

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class Scala3InterleavingTypeParameters extends Scala3FormatterBaseTest {
  override protected def version: ScalaVersion = LatestScalaVersions.Scala_3_7

  def test_base_cases(): Unit = doTextTest(
    """
      |def test[A](a: A)[B](b: B) = null
      |
      |def test[A](a: A)
      |        [B](b: B) = null
      |
      |def test[A](a: A)
      |           (b: B)
      |        [C](c: C)
      |           (d: D) = null
      |
      |def test[A](a: A)
      |           (b: B)
      |        [C <: _](c: C)
      |                (d: D) = null
      |
      |def test[A]
      |        (a: A)
      |        (b: B)
      |        [C]
      |        (c: C)
      |        (d: D) = null
      |
      |def test[A]
      |        (a: A)
      |        (b: B)
      |        [C](c: C)
      |           (d: D) = null
      |
      |""".stripMargin
  )

  def test_one_line(): Unit = doTextTest(
    "def test [ A ] ( a : A ) [ B ] ( b : B ) = null",
    "def test[A](a: A)[B](b: B) = null"
  )

  def test_noNewLineAftertypeParams_subsequentNormalParamsOnSameLine(): Unit = doTextTest(
    """
      |def test[A](a: A)(a2: A)
      | [B]  (b: B)  (b2: B)
      |                    [C]  (c: C)  (c2: C)= null
      |""".stripMargin,
    """
      |def test[A](a: A)(a2: A)
      |        [B](b: B)(b2: B)
      |        [C](c: C)(c2: C) = null
      |""".stripMargin
  )

  def test_noNewLineAftertypeParams_subsequentNormalParamsOnNewLine(): Unit = doTextTest(
    """
      |def test[A](a: A)
      |(a2: A)
      | [BB]  (b: B)
      | (b2: B)
      |                    [CCC]  (c: C)
      |                 (c2: C)= null
      |""".stripMargin,
    """
      |def test[A](a: A)
      |           (a2: A)
      |        [BB](b: B)
      |            (b2: B)
      |        [CCC](c: C)
      |             (c2: C) = null
      |""".stripMargin
  )

  def test_newLineAftertypeParams_subsequentNormalParamsOnNewLine(): Unit = doTextTest(
    """
      |def test[A]
      |  (a: A)
      |(a2: A)
      | [B]
      |  (b: B)
      | (b2: B)
      |                    [C]
      |                      (c: C)
      |                 (c2: C)= null
      |""".stripMargin,
    """
      |def test[A]
      |        (a: A)
      |        (a2: A)
      |        [B]
      |        (b: B)
      |        (b2: B)
      |        [C]
      |        (c: C)
      |        (c2: C) = null
      |""".stripMargin
  )

  def test_newLineAftertypeParams_subsequentNormalParamsOnSameLine(): Unit = doTextTest(
    """
      |def test[A]
      |  (a: A) (a2: A)
      | [B]
      |  (b: B) (b2: B)
      |                    [C]
      |                      (c: C) (c2: C)= null
      |""".stripMargin,
    """
      |def test[A]
      |        (a: A)(a2: A)
      |        [B]
      |        (b: B)(b2: B)
      |        [C]
      |        (c: C)(c2: C) = null
      |""".stripMargin
  )
}
