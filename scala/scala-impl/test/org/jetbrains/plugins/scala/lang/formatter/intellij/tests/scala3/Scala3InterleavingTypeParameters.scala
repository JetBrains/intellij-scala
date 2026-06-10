package org.jetbrains.plugins.scala.lang.formatter.intellij.tests.scala3

class Scala3InterleavingTypeParameters extends Scala3FormatterBaseTest {

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

  def test_method_call_one_line(): Unit = doTextTest(
    "test [ A ] ( a ) [ B ] ( b )",
    "test[A](a)[B](b)"
  )

  def test_method_call_multiline(): Unit = doTextTest(
    """
      |test[A](a)
      | [B]  (b)
      |                    [C]  (c)
      |""".stripMargin,
    """
      |test[A](a)
      |  [B](b)
      |  [C](c)
      |""".stripMargin
  )
}
