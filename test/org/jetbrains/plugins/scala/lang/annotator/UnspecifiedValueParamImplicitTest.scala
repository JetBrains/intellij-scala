package org.jetbrains.plugins.scala.lang.annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
  * Nikolay.Tropin
  * 25-Sep-17
  */
class UnspecifiedValueParamImplicitTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL10845(): Unit = {
    checkTextHasNoErrors(
      """
        |object Utils {
        |
        |  implicit class FooPimps(f: Foo) {
        |    def implicitMethod(): Unit = println(s"implicit foo method")
        |  }
        |
        |}
        |
        |class Foo {
        |
        |  import Utils._
        |
        |  implicitMethod() // <- wrongly reports "Cannot resolve reference implicitMethod with such signature". "sbt run" works fine
        |
        |  def implicitMethod(x: String): Unit = ()
        |}
        |
        |object Main extends App {
        |  new Foo
        |}
      """.stripMargin)
  }

  def testSCL12375(): Unit = {
    checkTextHasNoErrors(
      """
        |class MyRootClass {
        |  def apply(a: Int): Int = 0
        |}
        |
        |object MyRootClass {
        |  implicit class MyClassWithApply(val c: MyRootClass) {
        |    def apply(): Int = 0
        |  }
        |}
        |
        |object MyObject extends MyRootClass
        |
        |object Test {
        |  MyObject()
        |}
      """.stripMargin)
  }


}
