package org.jetbrains.plugins.scala.annotator

class ForComprehensionHighlightingTest extends ScalaHighlightingTestBase {

  def test_guard_type(): Unit = {
    val code =
      """
        |for {x <- Seq(1) if x } {}
        |for {y <- Seq(true) if y } {}
      """.stripMargin

    assertMatches(errorsFromScalaCode(code)){
      case Error("x", "Expression of type Int doesn't conform to expected type Boolean") :: Nil =>
    }
  }

  def test_guard_with_custom_type(): Unit = {
    val code =
      """
        |class A[T] {
        |  def withFilter(f: T => Int): A[T] = ???
        |  def foreach(f: T => Unit): Unit = ???
        |}
        |for {x <- new A[Boolean] if x } {}
        |for {y <- new A[Int] if y } {}
      """.stripMargin

    assertMatches(errorsFromScalaCode(code)){
      case Error("x", "Expression of type Boolean doesn't conform to expected type Int") :: Nil =>
    }
  }

  def test_SCL6498(): Unit = {
    val code =
      """
        |for (i <- 1 to 5 if i) 1
      """.stripMargin

    assertMatches(errorsFromScalaCode(code)){
      case Error("i", "Expression of type Int doesn't conform to expected type Boolean") :: Nil =>
    }
  }
}
