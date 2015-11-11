package org.jetbrains.plugins.scala.debugger.evaluateExpression

import org.jetbrains.plugins.scala.debugger.{ScalaDebuggerTestCase, ScalaVersion_2_11, ScalaVersion_2_12}

/**
 * User: Alefas
 * Date: 20.10.11
 */

class ScalaThisAndSuperEvaluationTest extends ScalaThisAndSuperEvaluationTestBaseClass with ScalaVersion_2_11
class ScalaThisAndSuperEvaluationTest_212 extends ScalaThisAndSuperEvaluationTestBaseClass with ScalaVersion_2_12

abstract class ScalaThisAndSuperEvaluationTestBaseClass extends ScalaDebuggerTestCase {
  addFileWithBreakpoints("TraitThis.scala",
    s"""
       |object TraitThis {
       |  trait Z {
       |    def foo {
       |      ""$bp
       |    }
       |  }
       |  def main(args: Array[String]) {
       |    new Z {}.foo
       |  }
       |}
      """.stripMargin.trim()
  )
  def testTraitThis() {
    runDebugger() {
      waitForBreakpoint()
      evalStartsWith("this", "TraitThis$$anon")
    }
  }

  addFileWithBreakpoints("Base.scala",
    s"""
       |class BaseClass {
       |  def foo = 1
       |}
       |
       |trait BaseTrait {
       |  def foo = 1
       |}
      """.stripMargin.trim()
  )
  addFileWithBreakpoints("SuperInvocation.scala",
    s"""
       |object SuperInvocation extends BaseClass {
       |  def main(args: Array[String]) {
       |    ""$bp
       |  }
       |}
      """.stripMargin.trim()
  )
  def testSuperInvocation() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("foo", "1")
    }
  }

  addFileWithBreakpoints("InvocationFromInner.scala",
    s"""
       |object InvocationFromInner extends BaseClass {
       |  trait Z {
       |    def goo {
       |      ""$bp
       |    }
       |  }
       |  def main(args: Array[String]) {
       |    new Z {}.goo
       |  }
       |}
      """.stripMargin.trim()
  )
  def testInvocationFromInner() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("foo", "1")
    }
  }

  addFileWithBreakpoints("ThisInvocationFromInner.scala",
    s"""
       |object ThisInvocationFromInner extends BaseClass {
       |  trait Z {
       |    def foo {
       |      ""$bp
       |    }
       |  }
       |  def main(args: Array[String]) {
       |    new Z {}.foo
       |  }
       |}
      """.stripMargin.trim()
  )
  def testThisInvocationFromInner() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("ThisInvocationFromInner.this.foo", "1")
    }
  }

  addFileWithBreakpoints("ThisInvocationFromInnerClass.scala",
    s"""
       |class ThisInvocationFromInnerClass extends BaseClass {
       |  trait Z {
       |    def foo {
       |      ""$bp
       |    }
       |  }
       |  def boo(args: Array[String]) {
       |    new Z {}.foo
       |  }
       |}
       |object ThisInvocationFromInnerClass {
       |  def main(args: Array[String]) {
       |    val sample = new ThisInvocationFromInnerClass
       |    sample.boo(args)
       |  }
       |}
      """.stripMargin.trim()
  )
  def testThisInvocationFromInnerClass() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("ThisInvocationFromInnerClass.this.foo", "1")
    }
  }

  addFileWithBreakpoints("SuperInvocationFromInner.scala",
    s"""
       |object SuperInvocationFromInner extends BaseClass {
       |  trait Z {
       |    def foo {
       |      ""$bp
       |    }
       |  }
       |  def main(args: Array[String]) {
       |    new Z {}.foo
       |  }
       |}
      """.stripMargin.trim()
  )
  def testSuperInvocationFromInner() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("SuperInvocationFromInner.super.foo", "1")
    }
  }

  addFileWithBreakpoints("SuperTraitInvocationFromInner.scala",
    s"""
       |class SuperTraitInvocationFromInner extends BaseTrait {
       |  trait Z {
       |    def foo {
       |      ""$bp
       |    }
       |  }
       |  def boo(args: Array[String]) {
       |    new Z {}.foo
       |  }
       |}
       |object SuperTraitInvocationFromInner {
       |  def main(args: Array[String]){
       |    new SuperTraitInvocationFromInner().boo(args)
       |  }
       |}
      """.stripMargin.trim()
  )
  def testSuperTraitInvocationFromInner() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("SuperTraitInvocationFromInner.super.foo", "1")
    }
  }

  addFileWithBreakpoints("SuperTraitInvocation.scala",
    s"""
       |object SuperTraitInvocation extends BaseTrait {
       |  def main(args: Array[String]) {
       |    ""$bp
       |  }
       |}
      """.stripMargin.trim()
  )
  def testSuperTraitInvocation() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("foo", "1")
    }
  }

  addFileWithBreakpoints("Sample.scala",
    s"""
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
       |      ""$bp
       |    }
       |  }
       |  def moo {new F{}.foo}
       |}
       |object OuterSuperInnerTraitInvocation {
       |  def main(args: Array[String]) {
       |    new E {}.moo
       |  }
       |}
      """.stripMargin.trim()
  )
  def testOuterSuperInnerTraitInvocation() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("E.super.ioi", "2")
    }
  }

  addFileWithBreakpoints("InnerOuterEtc.scala",
    s"""
       |object InnerOuterEtc {
       |  class Outer extends BaseClass {
       |    trait Z {
       |      def goo {
       |        ""$bp
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
  def testInnerOuterEtc() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("foo", "1")
    }
  }
}