package org.jetbrains.plugins.scala.lang.typeInference

class UnnecessaryRefinementsTest extends TypeInferenceTestBase {
  def testSCL17678(): Unit = doTest(
    s"""
      |trait Foo { def m: Unit }
      |${START}new Foo {
      |  override def m = ()
      |}$END
      |//Foo
      |  """.stripMargin
  )

  def testUnaccessible(): Unit = doTest(
    s"""
      |trait Foo { def m: Unit }
      |${START}new Foo {
      |  override def m = ()
      |  protected def bar: Int = 123
      |  private type T = Int
      |  private[this] val (a, b) = (1, "2")
      |}$END
      |//Foo
      |""".stripMargin
  )

  def testShouldInferRefinement(): Unit = doTest(
    s"""
       |trait Foo { def m: Unit }
       |${START}new Foo {
       |  override def m = ()
       |  def bar: Int = 123
       |  type T = Int
       |}$END
       |/*Foo with Object { def bar: Int; type T = Int }*/
       |""".stripMargin
  )

  def testSCL18191(): Unit = doTest(
    s"""
       |trait Monoid[A] {
       |  def op(left: A, right: A): A
       |  def zero: A
       |}
       |${START}new Monoid[List[Int]] {
       |  override def op(left: List[Int], right: List[Int]): List[Int] = left ++ right
       |  override def zero: List[Int] = Nil
       |
       |}$END
       |//Monoid[List[Int]]
       |""".stripMargin
  )
}
