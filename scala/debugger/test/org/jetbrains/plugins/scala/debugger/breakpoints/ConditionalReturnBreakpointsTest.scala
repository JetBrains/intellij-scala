package org.jetbrains.plugins.scala.debugger.breakpoints

import org.jetbrains.plugins.scala.ScalaVersion

/**
 * Tests for the conditional (early) return breakpoint variant (SCL-21626).
 *
 * Each test suspends fewer times than the enclosing method is called, which is the whole point of the
 * variant: [[BreakpointsTestBase]] fails both on an unexpected stop and, in `tearDown`, on a missing one.
 */
abstract class ConditionalReturnBreakpointsTestBase extends BreakpointsTestBase {

  addSourceFile("MethodEarlyReturn.scala",
    s"""object MethodEarlyReturn {
       |  def check(x: Int): Int = {
       |    if (x < 0) return -1 $breakpoint $conditionalReturn
       |    x * 2
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    println(check(-5))
       |    println(check(7))
       |  }
       |}
       |""".stripMargin)

  def testMethodEarlyReturn(): Unit = {
    breakpointsTest()((2, "check"))
  }

  //`check` is deliberately not the first member: on Scala 3 the module's `<clinit>` is attributed to the
  //line of the first member, and any breakpoint there suspends during object initialization instead.
  addSourceFile("SingleLineMethod.scala",
    s"""object SingleLineMethod {
       |  def main(args: Array[String]): Unit = {
       |    println(check(-5))
       |    println(check(7))
       |  }
       |
       |  def check(x: Int): Int = { if (x < 0) return -1; x * 2 } $breakpoint $conditionalReturn
       |}
       |""".stripMargin)

  def testSingleLineMethod(): Unit = {
    //both returns are attributed to this line; the trailing one is dropped as implicitly generated
    breakpointsTest()((6, "check"))
  }

  addSourceFile("EarlyReturnInLocalFunction.scala",
    s"""object EarlyReturnInLocalFunction {
       |  def check(x: Int): Int = {
       |    def inner(y: Int): Int = {
       |      if (y < 0) return -1 $breakpoint $conditionalReturn
       |      y * 2
       |    }
       |    inner(x)
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    println(check(-5))
       |    println(check(7))
       |  }
       |}
       |""".stripMargin)

  def testEarlyReturnInLocalFunction(): Unit = {
    //a local def is lifted to a real method, so the return is a real return opcode
    breakpointsTest()((3, "inner$1"))
  }

  addSourceFile("EarlyReturnInCaseClause.scala",
    s"""object EarlyReturnInCaseClause {
       |  def check(x: Int): Int = {
       |    x match {
       |      case 0 => return -1 $breakpoint $conditionalReturn
       |      case _ =>
       |    }
       |    x * 2
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    println(check(0))
       |    println(check(7))
       |  }
       |}
       |""".stripMargin)

  def testEarlyReturnInCaseClause(): Unit = {
    //a match is compiled inline into the enclosing method
    breakpointsTest()((3, "check"))
  }

  addSourceFile("EarlyReturnInTryCatch.scala",
    s"""object EarlyReturnInTryCatch {
       |  def check(x: Int): Int = {
       |    try {
       |      if (x < 0) return -1 $breakpoint $conditionalReturn
       |      x * 2
       |    } catch {
       |      case _: Throwable => 0
       |    }
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    println(check(-5))
       |    println(check(7))
       |  }
       |}
       |""".stripMargin)

  def testEarlyReturnInTryCatch(): Unit = {
    //without a `finally` the return instruction stays on this line (unlike ReturnInTryFinally, which
    //is rejected outright, see ScalaLineBreakpointType.isReturnFromMethod)
    breakpointsTest()((3, "check"))
  }
}

class ConditionalReturnBreakpointsTest_2_11 extends ConditionalReturnBreakpointsTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_11
}

class ConditionalReturnBreakpointsTest_2_12 extends ConditionalReturnBreakpointsTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_12
}

class ConditionalReturnBreakpointsTest_2_13 extends ConditionalReturnBreakpointsTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13
}

class ConditionalReturnBreakpointsTest_3 extends ConditionalReturnBreakpointsTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  /**
   * Not supported on Scala 3, and not because of the conditional return variant: a plain line breakpoint on a
   * `case` clause whose body is a bare `return` does not suspend either, even though the return instruction is
   * attributed to the clause's line. The variant is still offered, see `ExactBreakpointsTestBase`.
   */
  override def testEarlyReturnInCaseClause(): Unit = {}
}

class ConditionalReturnBreakpointsTest_3_RC extends ConditionalReturnBreakpointsTest_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_LTS_RC
}

class ConditionalReturnBreakpointsTest_3_Next_RC extends ConditionalReturnBreakpointsTest_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_Next_RC
}
