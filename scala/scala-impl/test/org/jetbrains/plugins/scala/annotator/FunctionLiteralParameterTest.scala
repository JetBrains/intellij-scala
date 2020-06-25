package org.jetbrains.plugins.scala
package annotator

class FunctionLiteralParameterTest extends ScalaHighlightingTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_2_13

  def testFunctionLiteralParameterTypeMismath(): Unit = {
    val code =
      """
        |object Test {
        |  trait Bar
        |  def foo(fn: String => Bar): Unit = {
        |    fn(123)
        |    fn(12, 45)
        |  }
        |}
        |""".stripMargin

    assertMessages(errorsFromScalaCode(code))(
      Error("123", "Type mismatch, expected: String, actual: Int"),
      Error(", 4", "Too many arguments for method apply(T1)")
    )
  }

  def testRepeatedParamsNonMethod(): Unit = {
    val code =
      """
        |class Varargs {
        |  def foo(g: (String*) => String) = ???
        |  def bar[A, B, C]: (A*, B, C*) => A = ???
        |  def ok(x: String*) = ???
        |}
        |""".stripMargin

    assertMessages(errorsFromScalaCode(code))(
      Error("String", "Repeated parameters are only allowed in method signatures. Use `Seq` instead"),
      Error("A", "Repeated parameters are only allowed in method signatures. Use `Seq` instead"),
      Error("C", "Repeated parameters are only allowed in method signatures. Use `Seq` instead")
    )
  }
}
