package org.jetbrains.plugins.scala.debugger.evaluateExpression

/**
 * User: Alefas
 * Date: 20.10.11
 */

class ScalaThisAndSuperEvaluationTest extends ScalaDebuggerTestCase {
  def testTraitThis() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  trait Z {
      |    def foo {
      |      "stop here"
      |    }
      |  }
      |  def main(args: Array[String]) {
      |    new Z {}.foo
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 3)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalStartsWith("this", "Sample$$anon")
    }
  }

  def testSuperInvocation() {
    myFixture.addFileToProject("A.scala",
      """
      |class A {
      |  def foo = 1
      |}
      """.stripMargin.trim()
    )
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample extends A {
      |  def main(args: Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 2)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("foo", "1")
    }
  }

  def testInvocationFromInner() {
    myFixture.addFileToProject("A.scala",
      """
      |class A {
      |  def foo = 1
      |}
      """.stripMargin.trim()
    )
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample extends A {
      |  trait Z {
      |    def goo {
      |      "stop here"
      |    }
      |  }
      |  def main(args: Array[String]) {
      |    new Z {}.goo
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 3)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("foo", "1")
    }
  }

  def testThisInvocationFromInner() {
    myFixture.addFileToProject("A.scala",
      """
      |class A {
      |  def foo = 1
      |}
      """.stripMargin.trim()
    )
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample extends A {
      |  trait Z {
      |    def foo {
      |      "stop here"
      |    }
      |  }
      |  def main(args: Array[String]) {
      |    new Z {}.foo
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 3)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("Sample.this.foo", "1")
    }
  }

  def testThisInvocationFromInnerClass() {
    myFixture.addFileToProject("A.scala",
      """
      |class A {
      |  def foo = 1
      |}
      """.stripMargin.trim()
    )
    myFixture.addFileToProject("Sample.scala",
      """
      |class Simple extends A {
      |  trait Z {
      |    def foo {
      |      "stop here"
      |    }
      |  }
      |  def main(args: Array[String]) {
      |    new Z {}.foo
      |  }
      |}
      |object Sample {
      |  def main(args: Array[String]) {
      |    val sample = new Simple
      |    sample.main(args)
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 3)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("Simple.this.foo", "1")
    }
  }

  def testSuperInvocationFromInner() {
    myFixture.addFileToProject("A.scala",
      """
      |class A {
      |  def foo = 1
      |}
      """.stripMargin.trim()
    )
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample extends A {
      |  trait Z {
      |    def foo {
      |      "stop here"
      |    }
      |  }
      |  def main(args: Array[String]) {
      |    new Z {}.foo
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 3)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("Sample.super.foo", "1")
    }
  }

  def testSuperTraitInvocationFromInner() {
    myFixture.addFileToProject("A.scala",
      """
      |trait A {
      |  def foo = 1
      |}
      """.stripMargin.trim()
    )
    myFixture.addFileToProject("Sample.scala",
      """
      |class Simple extends A {
      |  trait Z {
      |    def foo {
      |      "stop here"
      |    }
      |  }
      |  def main(args: Array[String]) {
      |    new Z {}.foo
      |  }
      |}
      |object Sample {
      |  def main(args: Array[String]){
      |    new Simple().main(args)
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 3)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("Simple.super.foo", "1")
    }
  }

  def testSuperTraitInvocation() {
    myFixture.addFileToProject("A.scala",
      """
      |class A {
      |  def foo = 1
      |}
      """.stripMargin.trim()
    )
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample extends A {
      |  def main(args: Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 2)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("foo", "1")
    }
  }

  def testOuterSuperInnerTraitInvocation() {
    myFixture.addFileToProject("A.scala",
      """
      |class A {
      |  def foo = 1
      |}
      """.stripMargin.trim()
    )
    myFixture.addFileToProject("Sample.scala",
      """
      |trait IOI {
      |  def ioi = 2
      |}
      |trait E extends IOI {
      |  trait FF {
      |    def ioi = 1
      |  }
      |
      |  trait F extends FF {
      |    def foo = {
      |      E.super.ioi
      |      "stop here"
      |    }
      |  }
      |  def moo {new F{}.foo}
      |}
      |object Sample extends A {
      |  def main(args: Array[String]) {
      |    new E {}.moo
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 11)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("E.super.ioi", "1")
    }
  }

  def testInnerOuterEtc() {
    myFixture.addFileToProject("A.scala",
      """
      |class A {
      |  def foo = 1
      |}
      """.stripMargin.trim()
    )
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  class Outer extends A {
      |    trait Z {
      |      def goo {
      |        "stop here"
      |      }
      |    }
      |
      |    def goo {
      |      new Z {}.goo
      |    }
      |  }
      |  def main(args: Array[String]) {
      |    new Outer().goo
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 4)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("foo", "1")
    }
  }
}