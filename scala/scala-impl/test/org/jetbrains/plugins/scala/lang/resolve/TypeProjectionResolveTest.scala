package org.jetbrains.plugins.scala.lang.resolve

class TypeProjectionResolveTest extends SimpleResolveTestBase {
  import SimpleResolveTestBase._

  def testSCL21585(): Unit = doResolveTest(
    s"""
       |trait M {
       |  type A
       |}
       |
       |trait B {
       |  type T
       |  def t: T
       |}
       |
       |class Repro {
       |  type M_A[M1 <: M] = M1#A
       |  // good code red
       |  (value: ({ type T = Repro }) with M_A[M { type A = B }]) => value.t.o${REFSRC}k
       |  def ok = ""
       |}
       |""".stripMargin
  )
}
