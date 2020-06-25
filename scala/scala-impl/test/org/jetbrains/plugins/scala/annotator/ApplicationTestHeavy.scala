package org.jetbrains.plugins.scala.annotator
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class ApplicationTestHeavy extends ScalaHighlightingTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version <= LatestScalaVersions.Scala_2_13

  // SCL-15610
  def test_missing_argument_clause(): Unit = {
    val code =
      """
        |def test(i: Int)(j: Int): Unit = ()
        |test(0)
        |""".stripMargin

    assertMessages(errorsFromScalaCode(code))(
      Error(")", "Missing argument list (j: Int) for Method test(Int)(Int)"),
    )
  }

  def test_missing_argument_clause_on_apply(): Unit = {
    val code =
      """
        |object Test {
        |  def apply(i: Int)(j: Int): Unit = ()
        |}
        |Test(3)
        |""".stripMargin

    assertMessages(errorsFromScalaCode(code))(
      Error(")", "Missing argument list (j: Int) for Method apply(Int)(Int)"),
    )
  }

  def test_missing_argument_clause_on_infix_call(): Unit = {
    val code =
      """
        |object Test {
        |  def blub(i: Int)(j: Int): Unit = ()
        |}
        |Test blub 3
        |""".stripMargin

    assertMessages(errorsFromScalaCode(code))(
      Error("3", "Missing argument list (j: Int) for Method blub(Int)(Int)"),
    )
  }

  def test_missing_argument_clause_eta_expansion(): Unit = {
    val code =
      """
        |def accept(f: Int => Unit): Unit = ()
        |def test(i: Int)(j: Int): Unit = ()
        |accept(test(0))
        |""".stripMargin
    assertMessagesSorted(errorsFromScalaCode(code))(
      // no error
    )
  }

  def test_wrong_type_with_eta_expansion(): Unit = {
    val code =
      """object O {
        |    def accept(f: Boolean): Unit = ()
        |    def test(i: Int)(j: Int): Unit = ()
        |    accept(test(3))
        |}
        |""".stripMargin
    assertMessagesSorted(errorsFromScalaCode(code))(
      Error(")", "Missing argument list (j: Int) for Method test(Int)(Int)"),
    )
  }

  // produced by SCL-16431
  def test_wrong_function_type_with_eta_expansion(): Unit = {
    val code =
      """object O {
        |    def accept(f: Boolean => Unit): Unit = ()
        |    def test(i: Int)(j: Int): Unit = ()
        |    accept(test(3))
        |}
        |""".stripMargin
    assertMessagesSorted(errorsFromScalaCode(code))(
      Error("test(3)", "Type mismatch, expected: Boolean => Unit, actual: Int => Unit"),
    )
  }
}
