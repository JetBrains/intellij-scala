package org.jetbrains.plugins.scala
package worksheet

import org.junit.experimental.categories.Category

/**
  * User: Dmitry.Naydanov
  * Date: 12.07.16.
  */
@Category(Array(classOf[SlowTests]))
class WorksheetCompileTest extends WorksheetProcessorTestBase {

  override implicit val version: ScalaVersion = Scala_2_11

  def testSimple1(): Unit = doTest {
    """
      | val a = 1
      | val b = 2
    """.stripMargin
  }

  def testSimple2(): Unit = doTest {
    """
      | val s = "Boo"
      | var b = 2
      |
      | class A {
      |   def foo = 1
      | }
      |
      | b = new A().foo
    """.stripMargin
  }

  def testSimple3(): Unit = doTest {
    """
      | val c = true
      |
      | if (c) {
      |   for (_ <- 1 to 10) println("boo!")
      | }
      |
      | val a = 123
      |
      | a match {
      |   case 1 => 
      |   case _ => 
      | }
    """.stripMargin
  }

  def testTemplateDeclarations(): Unit = doTest {
    """
      | trait A {
      |   
      | }
      |
      | trait B
      |
      | abstract class C extends A
      |  
      | case class D(i: Int, s: String) extends C with B
      |
      | object E extends B
    """.stripMargin
  }

  def testFunctions(): Unit = doTest {
    """
      | def foo() = 123
      |
      | def boo(i: Int) {
      |   for (_ <- 1 to i) println("boo!")
      | }
      |
      | def bar(s: String): Unit = println(s)
      |
      | def concat(s1: String, s2: String, s3: String) = s1 + s2 + s3
      |
      | val a: Int = foo()
      | boo(a)
      | bar("boo")
      | val s: String = concat("b", "o", "o")
    """.stripMargin
  }

  def testImports(): Unit = doTest {
    """
      | import java.util._
      | import java.lang.Math
      |
      | class A {
      |   import scala.collection.mutable._
      |   
      |   def foo = HashMap[String, String]()
      | }
      |
      | def bar() {
      |   import java.io.File
      |   val f = new File("")
      | }
    """.stripMargin
  }

  def testTypes(): Unit = doTest {
    """
      | class A[T] {
      |   def foo(t: T): T = t
      | }
      |
      | type B = A[String]
    """.stripMargin
  }
}
