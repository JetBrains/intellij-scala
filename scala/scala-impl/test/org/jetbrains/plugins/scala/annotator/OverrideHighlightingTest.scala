package org.jetbrains.plugins.scala.annotator

class OverrideHighlightingTest extends ScalaHighlightingTestBase {
  def testScl13051(): Unit = {
    val code =
      s"""
         |trait Base {
         |  def foo: Int = 42
         |}
         |
         |class AClass extends Base {
         |  override def foo: String = "42"
         |}
       """.stripMargin
    assertMatches(errorsFromScalaCode(code)) {
      case Error(_, "Overriding type String does not conform to base type Int") :: Nil =>
    }
  }

  def testScl13051_1(): Unit = {
    val code =
      s"""
         |trait T1 {
         |  val foo: T1
         |}
         |trait T2 extends T1 {
         |  override val foo: T2
         |}
       """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }

  def testScl13051_2(): Unit = {
    val code =
      s"""
         |trait Base {
         |  def foo(x: Int): Int = 42
         |  def foo(x: String): String = "42"
         |}
         |class AClass extends Base {
         |  override def foo(x: Int): Int = 42
         |}
       """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }

  def testSCL13051_3(): Unit = {
    val code =
      s"""
         |trait Base[c] {
         |  def foo: c
         |}
         |
         |class AClass[a] extends Base[a]{
         |  override val foo: a = ???
         |}
       """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }
}
