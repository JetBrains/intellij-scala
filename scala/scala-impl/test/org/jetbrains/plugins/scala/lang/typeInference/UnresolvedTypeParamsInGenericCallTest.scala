package org.jetbrains.plugins.scala.lang.typeInference

class UnresolvedTypeParamsInGenericCallTest extends TypeInferenceTestBase {
  def testSCL17426(): Unit = doTest(
    s"""
       |trait Foo[+A, B]
       |implicit class FooOps[A, B](foo: Foo[A, B]) {
       |  def foo[C]: Foo[A, C] = ???
       |}
       |val f: Foo[Nothing, Int] = ???
       |${START}f.foo[String]$END
       |//Foo[Nothing, String]
       |""".stripMargin
  )
}
