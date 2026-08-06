package org.jetbrains.plugins.scala
package refactoring.extractTrait

class ExtractTraitTestTypeParameters extends ExtractTraitTestBase {

  def testSimpleParameterizedClass(): Unit = {
    val text =
      """
        |class Parameterized[T] {<caret>
        |  def foo(p: T) {}
        |}
      """.stripMargin

    val result =
      """
        |class Parameterized[T] extends ExtractedTrait[T]
        |
        |trait ExtractedTrait[T] {
        |
        |  def foo(p: T) {}
        |}
      """.stripMargin

    checkResult(text, result, onlyDeclarations = false)

    val result2 =
      """
        |class Parameterized[T] extends ExtractedTrait[T] {
        |  override def foo(p: T) {}
        |}
        |
        |trait ExtractedTrait[T] {
        |
        |  def foo(p: T)
        |}
      """.stripMargin


    checkResult(text, result2, onlyDeclarations = true)
  }

  def testTypeParameterWithBound(): Unit = {
    val text =
      """
        |class Parameterized[T <: List[Int]] {<caret>
        |  def foo(t: T) {
        |    t.isEmpty
        |  }
        |}
      """.stripMargin

    val result =
      """
        |class Parameterized[T <: List[Int]] extends ExtractedTrait[T]
        |
        |trait ExtractedTrait[T <: List[Int]] {
        |
        |  def foo(t: T) {
        |    t.isEmpty
        |  }
        |}
      """.stripMargin

    checkResult(text, result, onlyDeclarations = false)
  }

  def testChainedParameter(): Unit = {
    val text =
      """
        |class Parameterized[S, T <: List[S]] {<caret>
        |  def foo(t: T) {
        |    t.isEmpty
        |  }
        |}
      """.stripMargin

    val result =
      """
        |class Parameterized[S, T <: List[S]] extends ExtractedTrait[S, T]
        |
        |trait ExtractedTrait[S, T <: List[S]] {
        |
        |  def foo(t: T) {
        |    t.isEmpty
        |  }
        |}
      """.stripMargin

    checkResult(text, result, onlyDeclarations = false)
  }

}
