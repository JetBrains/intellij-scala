package org.jetbrains.plugins.scala.lang.resolve

class AnyMethodsOnTypeParamResolveTest extends SimpleResolveTestBase {
  import SimpleResolveTestBase._

  def testSCL16905(): Unit = doResolveTest(
    s"""
       |def foo[F[_], A](fa: F[A]): Unit = {
       |  fa.toSt${REFSRC}ring
       |}
       |""".stripMargin
  )

  def testFirstOrderTypeParam(): Unit = doResolveTest(
    s"""
       |def foo[A](a: A): Unit = {
       |  a.hash${REFSRC}Code()
       |}
       |""".stripMargin
  )
}
