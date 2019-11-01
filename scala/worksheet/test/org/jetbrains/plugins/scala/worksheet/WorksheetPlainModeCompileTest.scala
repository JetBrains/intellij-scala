package org.jetbrains.plugins.scala
package worksheet

import org.junit.experimental.categories.Category

@Category(Array(classOf[WorksheetEvaluationTests]))
class WorksheetPlainModeCompileTest extends WorksheetSourceProcessorTestBase {

  def testSimple1(): Unit = testCompilesInPlainMode {
    """
      | val a = 1
      | val b = 2
    """.stripMargin
  }

  def testSimple2(): Unit = testCompilesInPlainMode {
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

  def testSimple3(): Unit = testCompilesInPlainMode {
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

  def testTemplateDeclarations(): Unit = testCompilesInPlainMode {
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

  def testFunctions(): Unit = testCompilesInPlainMode {
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

  def testImports(): Unit = testCompilesInPlainMode {
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

  def testTypes(): Unit = testCompilesInPlainMode {
    """
      | class A[T] {
      |   def foo(t: T): T = t
      | }
      |
      | type B = A[String]
    """.stripMargin
  }
}
