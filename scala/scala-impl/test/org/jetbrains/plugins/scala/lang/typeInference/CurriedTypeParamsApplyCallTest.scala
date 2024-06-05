package org.jetbrains.plugins.scala.lang.typeInference

class CurriedTypeParamsApplyCallTest extends TypeInferenceTestBase {
  def testSCL22917_1(): Unit = doTest(
    s"""
      |object A {
      |  def m[A] = new C[A]
      |  class C[A] {
      |    def apply[B](f: A => (B)) = f
      |  }
      |
      |  ${START}m[Int](_.toString -> false)$END
      |}
      |//Int => (String, Boolean)
      |""".stripMargin
  )

  def testSCL22917_2(): Unit = doTest(
    s"""
       |object A {
       |  def m[A] = new C[A]
       |  class C[A] {
       |    def apply[B, C](f: A => (B, C)) = f
       |  }
       |  ${START}m[Int](_.toString -> false)$END
       |}
       |//Int => (String, Boolean)
       |""".stripMargin
  )
}
