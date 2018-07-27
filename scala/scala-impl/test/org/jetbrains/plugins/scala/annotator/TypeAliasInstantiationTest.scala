package org.jetbrains.plugins.scala.annotator

class TypeAliasInstantiationTest extends ScalaHighlightingTestBase {
  def testSCL13431_1(): Unit = {
    val code =
      """
        |object Test {
        |  class A[X, Y]()
        |  type T[X] = A[X, Int]
        |  new T[Long]()
        |}
      """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }

  def testSCL13431_2(): Unit = {
    val code =
      """
        |class Test[+A, +B](a: A, b: B)
        |
        |object Test {
        |  type T[A] = Test[A, Int]
        |
        |  new T[Int](1, 2)
        |}
    """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }

  def testSCL6041(): Unit = {
    val code =
      """
        |object TypeAliases {
        |  class A[A, B]
        |  type Z[T] = A[T, T]
        |  val z: Z[Int] = new Z[Int]
        |}
    """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }
}
