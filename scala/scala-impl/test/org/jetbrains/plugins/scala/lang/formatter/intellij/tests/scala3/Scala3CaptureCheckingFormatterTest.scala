package org.jetbrains.plugins.scala.lang.formatter.intellij.tests.scala3

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class Scala3CaptureCheckingFormatterTest extends Scala3FormatterBaseTest {
  override protected def version: ScalaVersion = LatestScalaVersions.Scala_3_8

  def test_capture_type(): Unit =
    doTextTest(
      """
        |x: A  ^
        |x: A  ^  { }
        |x: left  ^  right
        |x: left  ^  (right)
        |x: left  ^  1
        |x: left  ^  "literal"
        |x: (left  ^  )  ^  right
        |x: arg  ^  ->  ret
        |x: arg  ^  ?->  ret
        |""".stripMargin,
      """
        |x: A^
        |x: A^{}
        |x: left ^ right
        |x: left ^ (right)
        |x: left ^ 1
        |x: left ^ "literal"
        |x: (left^) ^ right
        |x: arg^ -> ret
        |x: arg^ ?-> ret
        |""".stripMargin
    )

  def test_pure_function(): Unit =
    doTextTest(
      """
        |x: A->B  ->  C
        |x: A?->B  ?-> C
        |x: A   ->  { a }  B
        |x: A  ?->  { a }  B
        |""".stripMargin,
      """
        |x: A -> B -> C
        |x: A ?-> B ?-> C
        |x: A ->{a} B
        |x: A ?->{a} B
        |""".stripMargin
    )
}
