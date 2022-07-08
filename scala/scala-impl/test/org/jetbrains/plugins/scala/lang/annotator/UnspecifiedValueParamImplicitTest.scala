package org.jetbrains.plugins.scala.lang.annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

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

  def testSCL10045(): Unit = {
    checkTextHasNoErrors(
      """
        |class Repro {
        |    implicit val i: Int = 0
        |
        |    new ReproDep // Warning: "Unspecified value parameters: i: Int"
        |}
        |
        |class ReproDep(private implicit val i: Int)
      """.stripMargin)
  }

}
