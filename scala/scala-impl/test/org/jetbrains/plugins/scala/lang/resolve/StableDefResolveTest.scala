package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.lang.resolve.SimpleResolveTestBase.{REFSRC, REFTGT}

class StableDefResolveTest extends SimpleResolveTestBase {
  def testSCL24547(): Unit = {
    doResolveTest(
      s"""
         |class ImportTest {
         |  object ImportMe {
         |    println("Hello!")
         |  }
         |}
         |
         |abstract class Parent(val test: ImportTest)
         |
         |class Child(${REFTGT}test: ImportTest) extends Parent(test) {
         |  import ${REFSRC}test.ImportMe // Cannot resolve symbol ImportMe
         |}
         |""".stripMargin)
  }

  def testResolveShadowedParam(): Unit = {
    doResolveTest(
      s"""
         |class ImportTest {
         |  object ${REFTGT}ImportMe {
         |    println("Hello!")
         |  }
         |}
         |
         |abstract class Parent(val test: ImportTest)
         |
         |class Child(test: ImportTest) extends Parent(test) {
         |  import test.${REFSRC}ImportMe // Cannot resolve symbol ImportMe
         |}
         |""".stripMargin)
  }

  def testSCL9645(): Unit = {
    doResolveTest(
      s"""
         |class Base[+T](final val value: T)
         |
         |class Derived[+T](value: T) extends Base(value)
         |
         |abstract class Container[+E] {
         |  def element: E
         |}
         |
         |object Test {
         |  val c: Container[Derived[Int]] = ???
         |  c.element.value ${REFSRC}* 2
         |}
       """.stripMargin)
  }
}
