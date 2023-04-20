package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

class BoundsTest extends TypeInferenceTestBase {

  override protected def shouldPass: Boolean = false

  override def folderPath: String = super.folderPath + "bugs5/"

  def testSCL5215(): Unit = doTest()

  def testSCL7085(): Unit = doTest()

  def testSCL5186(): Unit ={
    doTest(
      s"""object Test {
         |  def foo[V <: U, U](v: V, u: U) = u
         |  def bar[V, U >: V](v: V, u: U) = u
         |
         |  def zz(x: Any) = 1
         |  def zz(x: String) = "text"
         |
         |  def test(a: Any, s: String) {
         |    val z = ${START}foo(a, s)$END //plugin infers String (as a result), compiler infers Any
         |    val h = zz(z)
         |    val u: Int = h
         |  }
         |}
         |//Any
         |""".stripMargin)
  }
}
