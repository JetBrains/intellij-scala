package org.jetbrains.plugins.scala
package debugger
package breakpoints

import org.jetbrains.plugins.scala.extensions.inReadAction
import org.junit.Assert.{assertTrue, fail}

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.stream.Collectors
import scala.jdk.CollectionConverters._

class LambdaBreakpointsTest_2_11 extends LambdaBreakpointsTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_11
}

class LambdaBreakpointsTest_2_12 extends LambdaBreakpointsTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_12
}

class LambdaBreakpointsTest_2_13 extends LambdaBreakpointsTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13
}

class LambdaBreakpointsTest_3_0 extends LambdaBreakpointsTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_0

  override def testLambdaInClassConstructor(): Unit = breakpointsTest()(9, 4, 4, 4, 4, 4, 4)

  override def testLambdaInObjectConstructor(): Unit = breakpointsTest()(9, 4, 4, 4, 4, 4, 4)

  override def testLambdaInNestedObject(): Unit = breakpointsTest()(15, 8, 8, 8, 8, 8, 8)

  override def testLambdaInNestedClass(): Unit = breakpointsTest()(15, 8, 8, 8, 8, 8, 8)

  override def testLambdaInLocalMethod(): Unit = breakpointsTest()(
    21, 11,
    8, 9, 10, 11,
    8, 9, 10, 11,
    8, 9, 10, 11,
    8, 9, 10, 11,
    8, 9, 10, 11,
  )

  addSourceFile("LambdaInExtension.scala",
    s"""
       |object LambdaInExtension:
       |  extension (n: Int) def blah(): Unit =
       |    (0 until n).foreach { x =>
       |      println(s"blah $$x") $breakpoint
       |    }
       |
       |  def main(args: Array[String]): Unit =
       |    5.blah() $breakpoint
       |""".stripMargin)

  def testLambdaInExtension(): Unit = breakpointsTest()(8, 4, 4, 4, 4, 4, 4)

  addSourceFile("MainAnnotation.scala",
    s"""
       |@main def multipleBreakpoints(): Unit = {
       |  def foo(o: Any): Any = {
       |    o match {
       |      case s: String if s.nonEmpty => "string" $breakpoint
       |      case _ => "not string"
       |    }
       |  }
       |
       |  foo("abc")
       |}
       |""".stripMargin)

  def testMainAnnotation(): Unit = breakpointsTest("multipleBreakpoints")(4)
}

class LambdaBreakpointsTest_3_1 extends LambdaBreakpointsTest_3_0 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_1
}

class LambdaBreakpointsTest_3_2 extends LambdaBreakpointsTest_3_0 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_2
}

class LambdaBreakpointsTest_3_3 extends LambdaBreakpointsTest_3_0 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_3
}

class LambdaBreakpointsTest_3_4 extends LambdaBreakpointsTest_3_0 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_4
}

class LambdaBreakpointsTest_3_RC extends LambdaBreakpointsTest_3_0 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_5_RC

  override def testLambdaInClassConstructor(): Unit = breakpointsTest()(9, 4, 4, 4, 4, 4)

  override def testLambdaInObjectConstructor(): Unit = breakpointsTest()(9, 4, 4, 4, 4, 4)

  override def testLambdaInExtension(): Unit = breakpointsTest()(8, 4, 4, 4, 4, 4)

  override def testLambdaInNestedObject(): Unit = breakpointsTest()(15, 8, 8, 8, 8, 8)

  override def testLambdaInNestedClass(): Unit = breakpointsTest()(15, 8, 8, 8, 8, 8)

  override def testLambdaInLocalMethod(): Unit = breakpointsTest()(
    21,
    8, 9, 10, 11,
    8, 9, 10, 11,
    8, 9, 10, 11,
    8, 9, 10, 11,
    8, 9, 10, 11
  )
}

abstract class LambdaBreakpointsTestBase extends ScalaDebuggerTestCase {

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

  protected def breakpointsTest(className: String = getTestName(false))(lineNumbers: Int*): Unit = {
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
          resume(ctx)
      }
    }
  }

  addSourceFile("LambdaInClassConstructor.scala",
    s"""
       |object LambdaInClassConstructor {
       |  class C {
       |    (0 until 5).foreach { x =>
       |      println(x) $breakpoint
       |    }
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    println(new C()) $breakpoint
       |  }
       |}
       |""".stripMargin)

  def testLambdaInClassConstructor(): Unit = breakpointsTest()(9, 4, 4, 4, 4, 4)

  addSourceFile("LambdaInObjectConstructor.scala",
    s"""
       |object LambdaInObjectConstructor {
       |  object O {
       |    (0 until 5).foreach { x =>
       |      println(x) $breakpoint
       |    }
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    println(O) $breakpoint
       |  }
       |}
       |""".stripMargin)

  def testLambdaInObjectConstructor(): Unit = breakpointsTest()(9, 4, 4, 4, 4, 4)

  addSourceFile("LambdaInNestedObjectStatic.scala",
    s"""
       |object LambdaInNestedObjectStatic {
       |  class Outer {
       |    object Inner {
       |      def method(n: Int): Unit = {
       |        (0 until n).foreach { x => println(x) } $breakpoint ${lambdaOrdinal(0)}
       |      }
       |    }
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    new Outer().Inner.method(5) $breakpoint
       |  }
       |}
       |""".stripMargin)

  def testLambdaInNestedObjectStatic(): Unit = breakpointsTest()(11, 5, 5, 5, 5, 5)

  addSourceFile("LambdaInNestedClassStatic.scala",
    s"""
       |object LambdaInNestedClassStatic {
       |  object Outer {
       |    class Inner {
       |      def method(n: Int): Unit = {
       |        (0 until n).foreach(println) $breakpoint ${lambdaOrdinal(0)}
       |      }
       |    }
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    new Outer.Inner().method(5) $breakpoint
       |  }
       |}
       |""".stripMargin)

  def testLambdaInNestedClassStatic(): Unit = breakpointsTest()(11, 5, 5, 5, 5, 5)

  addSourceFile("LambdaInNestedObject.scala",
    s"""
       |object LambdaInNestedObject {
       |  class Outer {
       |    val field: Int = 5
       |
       |    object Inner {
       |      def method(): Unit = {
       |        (0 until field).foreach { x =>
       |          println(x) $breakpoint
       |        }
       |      }
       |    }
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    new Outer().Inner.method() $breakpoint
       |  }
       |}
       |""".stripMargin)

  def testLambdaInNestedObject(): Unit = breakpointsTest()(15, 8, 8, 8, 8, 8)

  addSourceFile("LambdaInNestedClass.scala",
    s"""
       |object LambdaInNestedClass {
       |  object Outer {
       |    val field: Int = 5
       |
       |    class Inner {
       |      def method(): Unit = {
       |        (0 until field).foreach { x =>
       |          println(x) $breakpoint
       |        }
       |      }
       |    }
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    new Outer.Inner().method() $breakpoint
       |  }
       |}
       |""".stripMargin)

  def testLambdaInNestedClass(): Unit = breakpointsTest()(15, 8, 8, 8, 8, 8)

  addSourceFile("LambdaInLocalMethod.scala",
    s"""
       |object LambdaInLocalMethod {
       |  case class A(s: String = "s", i: Int = 1)
       |
       |  object Inside {
       |    def create(a: A) = {
       |      def func(a: A, count: Int) = {
       |        (0 until count).map { i =>
       |          val number = i + 1 $breakpoint
       |          val string = i.toString $breakpoint
       |          val insideA = A(string, number) $breakpoint
       |          insideA.s * number $breakpoint
       |        }
       |
       |      }
       |      func(a, 5)
       |      a
       |    }
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    Inside.create(A()) $breakpoint
       |  }
       |}
       |""".stripMargin)

  def testLambdaInLocalMethod(): Unit = breakpointsTest()(
    21,
    8, 9, 10, 11,
    8, 9, 10, 11,
    8, 9, 10, 11,
    8, 9, 10, 11,
    8, 9, 10, 11
  )
}

