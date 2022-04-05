package org.jetbrains.plugins.scala.codeInspection.unused.positive

import org.jetbrains.plugins.scala.codeInspection.unused.ScalaUnusedDeclarationInspectionTestBase

class Scala2UnusedGlobalDeclarationInspectionTest extends ScalaUnusedDeclarationInspectionTestBase {
  private def addFile(text: String): Unit = myFixture.addFileToProject("Foo.scala", text)

  def test_auxiliary_constructors(): Unit = {
    addFile(
      """
        | object UnusedConstructor {
        |   val foo = new Foo()
        | }
        |"""
        .stripMargin)
    checkTextHasError(
      """
        |  import scala.annotation.unused
        |  @unused class Foo(@unused foo: String, @unused n: Int) {
        |    def this() = this("foo", 0)
        |    def this(str: String) = this(str, 0)
        |  }
        |""".stripMargin, allowAdditionalHighlights = true)
  }

  def test_overloaded_methods(): Unit = {
    addFile(
      """
        | object UnusedConstructor {
        |   val foo = new Foo()
        |   foo.aaa()
        | }
        |"""
        .stripMargin)
    checkTextHasError(
      """
        |  import scala.annotation.unused
        |  @unused class Foo{
        |    def aaa(): Unit = {}
        |    def aaa(@unused str: String): Unit = {}
        |  }
        |""".stripMargin, allowAdditionalHighlights = true)
  }
}
