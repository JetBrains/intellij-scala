package org.jetbrains.plugins.scala.lang.typeInference

/**
 * @author Nikolay.Tropin
 */
class SingletonTypeTest extends TypeInferenceTestBase {
  def testSCL9053(): Unit = {
    val text =
      s"""class Base
         |class Sub extends Base
         |
         |class B[B <: Base](val a: B)
         |
         |class Test {
         |  val a: Sub = null
         |  val b: B[a.type] = ${START}new B(a)$END
         |}
         |//B[Test.this.a.type]""".stripMargin
    doTest(text)
  }
}
