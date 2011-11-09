package org.jetbrains.plugins.scala.debugger.evaluateExpression

/**
 * User: Alefas
 * Date: 17.10.11
 */

class ScalaMethodEvaluationTest extends ScalaDebuggerTestCase {
  def testChangingFunction() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  var i = 1
      |  def foo = {
      |    i = i + 1
      |    i
      |  }
      |  def main(args: Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 7)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("foo", "2")
      evalEquals("foo", "3")
    }
  }

  def testSimpleFunction() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def foo() = 2
      |  def main(args: Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 3)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("foo", "2")
    }
  }
  
  def testApplyCall() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  class A {
      |    def apply(x: Int) = x + 1
      |  }
      |  def main(args : Array[String]) {
      |    val a = new A()
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 6)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("a(-1)", "0")
    }
  }
  
  def testCurriedFunction() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def foo(x: Int)(y: Int) = x * 2 + y
      |  def main(args: Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 3)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("foo(1)(2)", "4")
    }
  }
  
  def testArrayApplyFunction() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args : Array[String]) {
      |    val s = Array.ofDim[String](2, 2)
      |    s(1)(1) = "test"
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 4)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("s(1)(1)", "test")
    }
  }

  def testImplicitConversion() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args : Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 2)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("\"test\".dropRight(2)", "te")
      evalEquals("\"3\" -> \"3\"", "(3,3)")
    }
  }

  def testArrayLengthFunction() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args : Array[String]) {
      |    val s = Array(1, 2, 3)
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 3)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("s.length", "3")
      evalEquals("s.length()", "3")
    }
  }

  def testSimpleFunctionFromInner() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def foo() = 2
      |  def main(args: Array[String]) {
      |    val x = 1
      |    val r = () => {
      |      x
      |      "stop here"
      |    }
      |    r()
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 6)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("foo", "2")
    }
  }

  def testLibraryFunction() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 2)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalStartsWith("scala.collection.mutable.ArrayBuffer.newBuilder", "ArrayBuffer()")
    }
  }

  def testSubstringFunction() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 2)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalStartsWith("\"test\".substring(0, 2)", "te")
      evalStartsWith("\"test\".substring(2)", "st")
    }
  }

  def testNonStaticFunction() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def foo() = 2
      |  val x = 1
      |  def main(args: Array[String]) {
      |    def moo() {}
      |    class A {
      |      val x = 1
      |      def goo() = 2
      |      def foo() {
      |        val r = () => {
      |          moo()
      |          x
      |          "stop here"
      |        }
      |        r()
      |      }
      |    }
      |
      |    new A().foo()
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 12)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalStartsWith("goo", "2")
    }
  }

  def testDefaultAndNamedParameters() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def foo(x: Int, y: Int = 1, z: Int)(h: Int = x + y, m: Int) = x + y + z + h + m
      |  def main(args: Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 3)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("foo(1, z = 1)(m = 1)", "6")
      evalEquals("foo(1, 2, 1)(m = 1)", "8")
      evalEquals("foo(1, 2, 1)(1, m = 1)", "6")
    }
  }

  def testRepeatedParameters() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def foo(x: String*) = x.foldLeft("")(_ + _)
      |  def main(args: Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 3)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("foo(\"a\", \"b\", \"c\")", "abc")
      evalEquals("foo(\"a\")", "a")
      evalEquals("foo()", "")
    }
  }

  def testImplicitParameters() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def moo(x: Int)(implicit s: String) = x + s.length()
      |  def foo(x: Int)(implicit y: Int) = {
      |    implicit val s = "test"
      |    "stop here"
      |    x + y
      |  }
      |  def main(args: Array[String]) {
      |    implicit val x = 1
      |    foo(1)
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 4)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("foo(1)", "2")
      evalEquals("foo(1)(2)", "3")
      evalEquals("moo(1)", "5")
      evalEquals("moo(1)(\"a\")", "2")
    }
  }
}