package org.jetbrains.plugins.scala.annotator

class ApplicationTestHeavy extends ScalaHighlightingTestBase {

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
