package org.jetbrains.plugins.scala
package debugger
package stepOver

import org.jetbrains.plugins.scala.extensions.inReadAction
import org.junit.Assert.{assertTrue, fail}

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.stream.Collectors
import scala.jdk.CollectionConverters._

class StepOverTest_2_11 extends StepOverTest {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_11
}

class StepOverTest_2_12 extends StepOverTest {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_12

  override def testSkipStoreResult(): Unit = {
    stepOverTest()(2, 3, 4, 5, 6, 9, 11)
  }

  override def testPartialFun(): Unit = {
    stepOverTest()(4, 5, 6, 4, 3, 7, 8, 9, 4, 3, 7, 3, 3, 11)
  }

  override def testMultilineExpr(): Unit = {
    stepOverTest()(2, 4, 3, 4, 6, 7, 8, 9)
  }

  override def testCaseClausesReturn(): Unit = {
    stepOverTest()(6, 7, 9, 11, 12, 2)
  }

  override def testComplexPattern(): Unit = {
    stepOverTest()(2, 3, 4, 7, 10, 11, 12, 3, 14)
  }

  override def testSimple(): Unit = {
    stepOverTest()(2, 3, 4, 5, 6, 7, 8)
  }

  override def testNestedMatch(): Unit = {
    stepOverTest()(2, 3, 4, 5, 8, 9, 10, 12, 14)
  }

  override def testAccessorInDelayedInit(): Unit = {
    // TODO: Explore why enabling the Kotlin compiler makes the debugger stop at line 0 (0-based index) once,
    //       and disabling the Kotlin compiler makes the debugger stop at the same line twice.
    stepOverTest()(1, 2, 3, 4, 0)
  }
}

class StepOverTest_2_13 extends StepOverTest_2_12 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

  override def testPartialFun(): Unit = {
    stepOverTest()(4, 5, 6, 4, 7, 8, 9, 4, 7, 3, 11)
  }

  override def testCaseClausesReturn(): Unit = {
    stepOverTest()(6, 11, 6, 12, 6, 2)
  }
}

class StepOverTest_3 extends StepOverTest_2_13 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  override def testSimple(): Unit = {
    stepOverTest()(2, 3, 4, 5, 6, 8)
  }

  override def testMultilineExpr(): Unit = {
    stepOverTest()(2, 3, 4, 6, 8, 9)
  }

  override def testSkipStoreResult(): Unit = {
    stepOverTest()(2, 3, 4, 5, 6, 9, 11)
  }

  override def testPartialFun(): Unit = {
    stepOverTest()(4, 5, 6, 4, 7, 8, 9, 4, 7, 9, 9, 11)
  }

  override def testNestedMatch(): Unit = {
    stepOverTest()(2, 3, 4, 5, 8, 9, 10, 12, 14)
  }

  override def testCaseClausesReturn(): Unit = {
    stepOverTest()(6, 7, 12, 2)
  }

  override def testComplexPattern(): Unit = {
    stepOverTest()(2, 3, 4, 7, 10, 11, 12, 14)
  }

  override def testAccessorInDelayedInit(): Unit = {
    stepOverTest()(1, 2, 3, 4)
  }
}

class StepOverTest_3_RC extends StepOverTest_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_RC
}

abstract class StepOverTest extends ScalaDebuggerTestCase {

  private val expectedLineQueue: ConcurrentLinkedQueue[Int] = new ConcurrentLinkedQueue()

  override protected def tearDown(): Unit = {
    try {
      if (!expectedLineQueue.isEmpty) {
        val remaining =
          expectedLineQueue.stream().collect(Collectors.toList[Int]).asScala.toList
        fail(s"The debugger did not stop on all expected lines. Remaining: $remaining")
      }
    } finally {
      super.tearDown()
    }
  }

  protected def stepOverTest(className: String = getTestName(false))(lineNumbers: Int*): Unit = {
    assertTrue("The test should stop on at least 1 breakpoint", lineNumbers.nonEmpty)
    expectedLineQueue.addAll(lineNumbers.asJava)

    createLocalProcess(className)

    val debugProcess = getDebugProcess
    val positionManager = ScalaPositionManager.instance(debugProcess).getOrElse(new ScalaPositionManager(debugProcess))

    onEveryBreakpoint { implicit ctx =>
      val loc = ctx.getFrameProxy.location()
      val srcPos = inReadAction(positionManager.getSourcePosition(loc))
      val actual = srcPos.getLine
      Option(expectedLineQueue.poll()) match {
        case None =>
          fail(s"The debugger stopped on line $actual, but there were no more expected lines")
        case Some(expected) =>
          assertEquals(expected, actual)
          stepOver(ctx)
      }
    }
  }

  addSourceFile("Simple.scala",
    s"""
       |object Simple {
       |  def main (args: Array[String]): Unit = {
       |    println() $breakpoint
       |    List(1) match {
       |      case Seq(2) =>
       |      case Seq(3) =>
       |      case IndexedSeq(5) =>
       |      case IndexedSeq(6) =>
       |      case Seq(1) =>
       |      case Seq(7) =>
       |      case Seq(8) =>
       |    }
       |  }
       |}
    """.stripMargin.trim)

  def testSimple(): Unit = {
    stepOverTest()(2, 3, 4, 5, 6, 8, 1)
  }

  addSourceFile("MultilineExpr.scala",
    s"""
       |object MultilineExpr {
       |  def main (args: Array[String]): Unit = {
       |    println() $breakpoint
       |    Seq(2, 3)
       |      .map(_ - 1)
       |    match {
       |      case IndexedSeq(1, 2) =>
       |      case IndexedSeq(2, 3) =>
       |      case Seq(2) =>
       |      case Seq(1, _) =>
       |      case Seq(3) =>
       |    }
       |  }
       |}
    """.stripMargin.trim)

  def testMultilineExpr(): Unit = {
    stepOverTest()(2, 3, 4, 6, 8, 9, 1)
  }

  addSourceFile("SkipStoreResult.scala",
    s"""
       |object SkipStoreResult {
       |  def main (args: Array[String]): Unit = {
       |    println() $breakpoint
       |    val z = Seq(1, 2) match {
       |      case Seq(1, _) =>
       |        foo()
       |        fee()
       |      case _ =>
       |        fee()
       |        foo()
       |    }
       |    println(z)
       |  }
       |
       |  def foo() = "foo"
       |  def fee() = "fee"
       |}
    """.stripMargin.trim)

  def testSkipStoreResult(): Unit = {
    stepOverTest()(2, 3, 4, 5, 6, 11)
  }

  addSourceFile("PartialFun.scala",
    s"""
       |object PartialFun {
       |  def main (args: Array[String]): Unit = {
       |    ""
       |    val z = Seq(Some(1), Some(2), Some(3)) collect {
       |      case Some(1) => $breakpoint
       |        foo()
       |        fee()
       |      case Some(2) =>
       |        fee()
       |        foo()
       |    }
       |    println(z)
       |  }
       |
       |  def foo() = "foo"
       |  def fee() = "fee"
       |}
    """.stripMargin.trim)

  def testPartialFun(): Unit = {
    stepOverTest()(4, 5, 6, 3, 4, 7, 8, 9, 3, 4, 7, 3, 11)
  }

  addSourceFile("ComplexPattern.scala",
    s"""
       |object ComplexPattern {
       |  def main (args: Array[String]): Unit = {
       |    println() $breakpoint
       |    val z = Seq(left(1), left(2)) match {
       |      case Seq(Right("1")) =>
       |        foo()
       |        fee()
       |      case Left(Seq(Some(x))) and Left(Seq(None)) =>
       |        fee()
       |        foo()
       |      case Left(Seq(_)) and Left(Seq(Some(2))) =>
       |        fee()
       |        foo()
       |    }
       |    println(z)
       |  }
       |
       |  def foo() = "foo"
       |  def fee() = "fee"
       |  def left(i: Int): Either[Seq[Option[Int]], String] = Left(Seq(Some(i)))
       |
       |  object and {
       |    def unapply(s: Seq[_]): Option[(Any, Any)] = {
       |      s match {
       |        case Seq(x, y) => Some((x, y))
       |        case _ => None
       |      }
       |    }
       |  }
       |}
       |
    """.stripMargin.trim)

  def testComplexPattern(): Unit = {
    stepOverTest()(2, 3, 4, 7, 10, 11, 12, 14)
  }

  addSourceFile("NestedMatch.scala",
    s"""
       |object NestedMatch {
       |  def main (args: Array[String]): Unit = {
       |    println() $breakpoint
       |    val z = Seq(left(1), left(2)) match {
       |      case Seq(Left(Seq(Some(1))), x) => x match {
       |        case Left(Seq(None)) =>
       |          fee()
       |          foo()
       |        case Left(Seq(Some(2))) =>
       |          fee()
       |          foo()
       |      }
       |      case _ =>
       |    }
       |    println(z)
       |  }
       |
       |  def foo() = "foo"
       |  def fee() = "fee"
       |  def left(i: Int): Either[Seq[Option[Int]], String] = Left(Seq(Some(i)))
       |}
    """.stripMargin.trim)

  def testNestedMatch(): Unit = {
    stepOverTest()(2, 3, 4, 5, 8, 9, 10, 14)
  }

  addSourceFile("CaseClausesReturn.scala",
    s"""
       |object CaseClausesReturn {
       |  def main(args: Array[String]): Unit = {
       |    foo()
       |  }
       |
       |  def foo() = {
       |    "aaa" match { $breakpoint
       |      case "qwe" =>
       |        println(1)
       |      case "wer" =>
       |        println(2)
       |      case "aaa" =>
       |        println(3)
       |    }
       |  }
       |}
       | """.stripMargin.trim)

  def testCaseClausesReturn(): Unit = {
    stepOverTest()(6, 7, 9, 11, 12, 2)
  }

  addSourceFile("AccessorInDelayedInit.scala",
    s"""
       |object AccessorInDelayedInit extends App {
       |  var x = 5 $breakpoint
       |  x = x + 10
       |  x
       |  println(x)
       |}
  """.stripMargin.trim)

  def testAccessorInDelayedInit(): Unit = {
    // TODO: Explore why enabling the Kotlin compiler makes the debugger not stop at line 0 (0-based index),
    //       and disabling the Kotlin compiler makes the debugger stop at the same line once.
    stepOverTest()(1, 2, 3, 4)
  }
}
