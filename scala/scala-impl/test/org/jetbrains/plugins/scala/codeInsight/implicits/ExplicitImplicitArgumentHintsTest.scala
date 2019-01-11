package org.jetbrains.plugins.scala.codeInsight.implicits

class ExplicitImplicitArgumentHintsTest extends ImplicitHintsTestBase {
  import ImplicitHintsTestBase.{HintEnd => E, HintStart => S}

  def testSimpleImplicitArgument(): Unit = doTest(
    s"""
       |class A
       |object A {
       |  def fun()(implicit a: A): Unit = ???
       |}
       |
       |A.fun()$S.explicitly$E(new A)
     """.stripMargin
  )
}
