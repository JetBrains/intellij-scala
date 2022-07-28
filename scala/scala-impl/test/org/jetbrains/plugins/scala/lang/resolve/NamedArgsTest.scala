package org.jetbrains.plugins.scala.lang.resolve

class NamedArgsTest extends SimpleResolveTestBase {
  import SimpleResolveTestBase._

  def testSCL9144(): Unit = {
    doResolveTest(
      s"""
        |class AB(val a: Int, val b: Int) {
        |  def withAB(x: Int) = ???
        |  def withAB(${REFTGT}a: Int = a, b: Int = b) = ???
        |  def withA(a: Int) = withAB(${REFSRC}a = a)
        |  def withB(b: Int) = withAB(b = b)
        |}
      """.stripMargin)
  }

  def testSCL14008(): Unit = {
    doResolveTest(
      s"""
        |class Type
        |  object Type {
        |    implicit def bool2Type(bool: Boolean): Type = new Type
        |  }
        |  class ToTargetType
        |  object ToTargetType {
        |    implicit def toType(toTargetType: ToTargetType): TargetType = new TargetType
        |  }
        |  class TargetType
        |  object Test {
        |    def apply(a: Int = 1, b: String = "2", ${REFTGT}c: Boolean = false): ToTargetType = new ToTargetType
        |    def apply(two: Type*): TargetType = new TargetType
        |  }
        |  object bar {
        |    val wasBroken: TargetType = Test.apply(${REFSRC}c = true)
        |  }
      """.stripMargin)
  }

  def testSCL17892(): Unit = doResolveTest(
    s"""
       |object A { def apply(${REFTGT}g: Int): Int = 123 }
       |def foo(f: Int, g: Int): A.type = A
       |foo(f = 123, g = 123)(${REFSRC}g = 123)
       |""".stripMargin
  )

  def testSCL17892_2(): Unit = doResolveTest(
    s"""
       |object A { def apply(g: Int)(g${REFTGT}g: Int): Int = 123 }
       |def foo(f: Int, g: Int): A.type = A
       |foo(f = 123, g = 123)(g = 123)(g${REFSRC}g = 123)
       |""".stripMargin
  )

  def testSCL18052(): Unit = doResolveTest(
    s"""
       |def compose[A,B,C](f: B => C, g: A => B): A => C = ???
       |val addOne: Int => Int = _ + 1
       |val toStr: Int => String = s => s.toString
       |compose(toStr, addOne)(v${REFSRC}1 = 42)
       |""".stripMargin
  )
}
