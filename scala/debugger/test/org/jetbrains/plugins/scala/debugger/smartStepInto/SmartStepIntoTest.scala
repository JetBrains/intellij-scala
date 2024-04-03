package org.jetbrains.plugins.scala
package debugger
package smartStepInto

import com.intellij.debugger.actions.SmartStepTarget
import com.intellij.debugger.engine.{ContextUtil, SuspendContextImpl}
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.junit.Assert.{assertTrue, fail}

import java.util.concurrent.ConcurrentLinkedQueue
import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

class SmartStepIntoTest_2_11 extends SmartStepIntoTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_11
}

class SmartStepIntoTest_2_12 extends SmartStepIntoTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_12

  override def testByNameArgument2(): Unit = {
    smartStepIntoTest("ByNameArgument")(
      Target("inTryBlock(String)"),
      Target("u: => String")
    )(
      Breakpoint("ByNameArgument.scala", "main", 13) -> smartStepInto(Target("u: => String")),
      Breakpoint("ByNameArgument.scala", "$anonfun$main$1", 14) -> resume
    )
  }

  override def testMethodValue(): Unit = {
    smartStepIntoTest()(
      Target("update(Function1<Object, Object>)"),
      Target("incr(int, int)"),
      Target("update(Function1<Object, Object>)"),
      Target("id(T)"),
      Target("update(Function1<Object, Object>)"),
      Target("decr(int)")
    )(
      Breakpoint("MethodValue.scala", "main", 4) -> smartStepInto(Target("incr(int, int)")),
      Breakpoint("MethodValue.scala", "incr", 7) -> stepOut,
      Breakpoint("MethodValue.scala", "$anonfun$main$1", 4) -> stepOut,
      Breakpoint("MethodValue.scala", "update", 13) -> stepOut,
      Breakpoint("MethodValue.scala", "main", 4) -> smartStepInto(Target("id(T)")),
      Breakpoint("MethodValue.scala", "id", 9) -> stepOut,
      Breakpoint("MethodValue.scala", "$anonfun$main$2", 4) -> stepOut,
      Breakpoint("MethodValue.scala", "update", 13) -> stepOut,
      Breakpoint("MethodValue.scala", "main", 4) -> smartStepInto(Target("decr(int)")),
      Breakpoint("MethodValue.scala", "decr", 16) -> resume
    )
  }

  override def testInsideLambda(): Unit = {
    smartStepIntoTest()(
      Target("sum(double, double)"),
      Target("multi(double, double)")
    )(
      Breakpoint("InsideLambda.scala", "$anonfun$main$1", 8) -> smartStepInto(Target("multi(double, double)")),
      Breakpoint("InsideLambda.scala", "multi", 2) -> stepOut,
      Breakpoint("InsideLambda.scala", "$anonfun$main$1", 8) -> smartStepInto(Target("sum(double, double)")),
      Breakpoint("InsideLambda.scala", "sum", 4) -> resume
    )
  }
}

class SmartStepIntoTest_2_13 extends SmartStepIntoTest_2_12 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

  addSourceFile("PostfixAndUnapply.scala",
    s"""import scala.language.postfixOps
       |
       |object PostfixAndUnapply {
       |
       |  def main(args: Array[String]): Unit = {
       |    new D(1) match {
       |      case a @ D(1) => a foo $breakpoint
       |    }
       |  }
       |}
       |
       |class D(val i: Int) {
       |  def foo = {}
       |
       |}
       |
       |object D {
       |  def unapply(a: D) = Some(a.i)
       |}""".stripMargin.trim
  )

  override def testPostfixAndUnapply(): Unit = {
    smartStepIntoTest()(
      Target("D.unapply(D)"),
      Target("foo()")
    )(
      Breakpoint("PostfixAndUnapply.scala", "main", 7) -> smartStepInto(Target("D.unapply(D)")),
      Breakpoint("PostfixAndUnapply.scala", "unapply", 18) -> resume,
      Breakpoint("PostfixAndUnapply.scala", "main", 7) -> resume
    )
  }
}

class SmartStepIntoTest_3 extends SmartStepIntoTest_2_13 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  override def testInfixAndApply(): Unit = {
    smartStepIntoTest()(
      Target("add(C)"),
      Target("C.apply(int)")
    )(
      Breakpoint("InfixAndApply.scala", "main", 5) -> smartStepInto(Target("C.apply(int)")),
      Breakpoint("InfixAndApply.scala", "apply", 17) -> stepOut,
      Breakpoint("InfixAndApply.scala", "main", 5) -> smartStepInto(Target("add(C)")),
      Breakpoint("InfixAndApply.scala", "add", 11) -> resume
    )
  }

  override def testAnonymousClassFromTrait1(): Unit = {
    smartStepIntoTest("AnonymousClassFromTrait")(
      Target("execute(Processor)"),
      Target("new Processor()"),
      Target("new Processor.execute()")
    )(
      Breakpoint("AnonymousClassFromTrait.scala", "main", 6) -> smartStepInto(Target("new Processor()")),
      Breakpoint("AnonymousClassFromTrait.scala", "main", 12) -> resume
    )
  }

  override def testAnonymousClassFromTrait2(): Unit = {
    smartStepIntoTest("AnonymousClassFromTrait")(
      Target("execute(Processor)"),
      Target("new Processor()"),
      Target("new Processor.execute()")
    )(
      Breakpoint("AnonymousClassFromTrait.scala", "main", 6) -> smartStepInto(Target("new Processor.execute()")),
      Breakpoint("AnonymousClassFromTrait.scala", "main", 12) -> resume
    )
  }

  override def testAnonymousClassFromClass1(): Unit = {
    smartStepIntoTest("AnonymousClassFromClass")(
      Target("execute(ProcessorClass)"),
      Target("new ProcessorClass()"),
      Target("new ProcessorClass.execute()")
    )(
      Breakpoint("AnonymousClassFromClass.scala", "main", 6) -> smartStepInto(Target("new ProcessorClass()")),
      Breakpoint("AnonymousClassFromClass.scala", "main", 12) -> resume
    )
  }

  override def testAnonymousClassFromClass2(): Unit = {
    smartStepIntoTest("AnonymousClassFromClass")(
      Target("execute(ProcessorClass)"),
      Target("new ProcessorClass()"),
      Target("new ProcessorClass.execute()")
    )(
      Breakpoint("AnonymousClassFromClass.scala", "main", 6) -> smartStepInto(Target("new ProcessorClass.execute()")),
      Breakpoint("AnonymousClassFromClass.scala", "main", 12) -> resume
    )
  }

  override def testByNameArgument1(): Unit = {
    smartStepIntoTest("ByNameArgument")(
      Target("inTryBlock(String)"),
      Target("u: => String")
    )(
      Breakpoint("ByNameArgument.scala", "main", 13) -> smartStepInto(Target("inTryBlock(String)")),
      Breakpoint("ByNameArgument.scala", "main", 16) -> resume
    )
  }

  override def testByNameArgument2(): Unit = {
    smartStepIntoTest("ByNameArgument")(
      Target("inTryBlock(String)"),
      Target("u: => String")
    )(
      Breakpoint("ByNameArgument.scala", "main", 13) -> smartStepInto(Target("u: => String")),
      Breakpoint("ByNameArgument.scala", "main", 16) -> resume
    )
  }

  override def testMethodValue(): Unit = {
    smartStepIntoTest()(
      Target("update(Function1<Object, Object>)"),
      Target("incr(int, int)"),
      Target("update(Function1<Object, Object>)"),
      Target("id(T)"),
      Target("update(Function1<Object, Object>)"),
      Target("decr(int)")
    )(
      Breakpoint("MethodValue.scala", "main", 4) -> smartStepInto(Target("incr(int, int)")),
      Breakpoint("MethodValue.scala", "incr", 7) -> stepOut,
      Breakpoint("MethodValue.scala", "main$$anonfun$1", 4) -> stepOut,
      Breakpoint("MethodValue.scala", "update", 13) -> stepOut,
      Breakpoint("MethodValue.scala", "main", 4) -> smartStepInto(Target("id(T)")),
      Breakpoint("MethodValue.scala", "id", 9) -> stepOut,
      Breakpoint("MethodValue.scala", "main$$anonfun$2", 4) -> stepOut,
      Breakpoint("MethodValue.scala", "update", 13) -> stepOut,
      Breakpoint("MethodValue.scala", "main", 4) -> smartStepInto(Target("decr(int)")),
      Breakpoint("MethodValue.scala", "decr", 16) -> resume
    )
  }

  override def testInsideLambda(): Unit = {
    smartStepIntoTest()(
      Target("sum(double, double)"),
      Target("multi(double, double)")
    )(
      Breakpoint("InsideLambda.scala", "$anonfun$1", 8) -> smartStepInto(Target("multi(double, double)")),
      Breakpoint("InsideLambda.scala", "multi", 2) -> stepOut,
      Breakpoint("InsideLambda.scala", "$anonfun$1", 8) -> smartStepInto(Target("sum(double, double)")),
      Breakpoint("InsideLambda.scala", "sum", 4) -> resume
    )
  }
}

class SmartStepIntoTest_3_RC extends SmartStepIntoTest_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_RC
}

abstract class SmartStepIntoTestBase extends ScalaDebuggerTestCase {

  protected case class Target(target: String)

  protected case class Breakpoint(file: String, method: String, line: Int)

  private val expectedTargetsQueue: ConcurrentLinkedQueue[Target] = new ConcurrentLinkedQueue()

  private val expectedActionsQueue: ConcurrentLinkedQueue[(Breakpoint, SuspendContextImpl => Unit)] =
    new ConcurrentLinkedQueue()

  private val handler: ScalaSmartStepIntoHandler = new ScalaSmartStepIntoHandler

  override protected def tearDown(): Unit = {
    try {
      if (!expectedTargetsQueue.isEmpty) {
        fail(s"The debugger did not check all expected smart step into targets.")
      }
      if (!expectedActionsQueue.isEmpty) {
        fail(s"The debugger did not execute all expected actions on breakpoints. Remaining: ${drain(expectedActionsQueue)}")
      }
    } finally {
      super.tearDown()
    }
  }

  protected def smartStepIntoTest(mainClass: String = getTestName(false))
                                 (targets: Target*)
                                 (actions: (Breakpoint, SuspendContextImpl => Unit)*): Unit = {
    assertTrue("The test should check at least 1 target", targets.nonEmpty)
    assertTrue("The test should stop on at least 1 breakpoint", actions.nonEmpty)
    expectedTargetsQueue.addAll(targets.asJava)
    expectedActionsQueue.addAll(actions.asJava)

    createLocalProcess(mainClass)

    onBreakpoint { implicit ctx =>
      val availableTargets = availableSmartStepIntoTargets()
      val actual = inReadAction(availableTargets.map(_.getPresentation))
      val expected = drain(expectedTargetsQueue)
      assertEquals(expected.map(_.target), actual)
    }

    onEveryBreakpoint { ctx =>
      val loc = ctx.getFrameProxy.getStackFrame.location()
      val debugProcess = getDebugProcess
      val positionManager = ScalaPositionManager.instance(debugProcess).getOrElse(new ScalaPositionManager(debugProcess))
      val srcPos = inReadAction(positionManager.getSourcePosition(loc))
      val actual = Breakpoint(loc.sourceName(), loc.method().name(), srcPos.getLine + 1)

      Option(expectedActionsQueue.poll()) match {
        case None =>
          fail(s"The debugger stopped on $actual, but there were no expected breakpoints left")
        case Some((expected, cont)) =>
          assertEquals(expected, actual)
          cont(ctx)
      }
    }
  }

  protected def smartStepInto(target: Target)(context: SuspendContextImpl): Unit = {
    val availableTargets = availableSmartStepIntoTargets()(context)
    val smartStepIntoTarget = inReadAction(availableTargets.find(_.getPresentation == target.target))
    assertTrue(s"Cannot find smart step into target $target", smartStepIntoTarget.isDefined)
    smartStepInto(smartStepIntoTarget.get)(context)
  }

  private def availableSmartStepIntoTargets()(implicit context: SuspendContextImpl): Seq[SmartStepTarget] = inReadAction {
    val sourcePosition = ContextUtil.getSourcePosition(context)
    handler.findSmartStepTargets(sourcePosition).asScala.toSeq
  }

  private def smartStepInto(target: SmartStepTarget)(implicit context: SuspendContextImpl): Unit = {
    val filter = inReadAction(handler.createMethodFilter(target))
    val debugProcess = getDebugProcess
    val command = debugProcess.createStepIntoCommand(context, false, filter)
    debugProcess.getManagerThread.schedule(command)
  }

  private def drain[A](queue: ConcurrentLinkedQueue[A]): List[A] = {
    @tailrec
    def loop(acc: List[A]): List[A] =
      if (queue.isEmpty) acc.reverse
      else loop(queue.poll() :: acc)

    loop(List.empty)
  }

  addSourceFile("ChainedMethodsAndConstructor.scala",
    s"""
       |object ChainedMethodsAndConstructor {
       |  def main(args: Array[String]): Unit = {
       |    val s = new A(11).id1().id2.asString $breakpoint
       |  }
       |}
      """.stripMargin.trim
  )
  addSourceFile("A.scala",
    s"""
       |class A(i: Int) {
       |
       |  val a = i
       |
       |  def id1() = {
       |    val x: A = this
       |    x
       |  }
       |
       |  def id2 = {
       |    val x: A = this
       |    x
       |  }
       |
       |  def asString = "A"
       |}
       |""".stripMargin.trim
  )

  def testChainedMethodsAndConstructor(): Unit = {
    smartStepIntoTest()(
      Target("new A(int)"),
      Target("id1()"),
      Target("id2()"),
      Target("asString()")
    )(
      Breakpoint("ChainedMethodsAndConstructor.scala", "main", 3) -> smartStepInto(Target("new A(int)")),
      Breakpoint("A.scala", "<init>", 1) -> stepOut,
      Breakpoint("ChainedMethodsAndConstructor.scala", "main", 3) -> smartStepInto(Target("id1()")),
      Breakpoint("A.scala", "id1", 6) -> stepOut,
      Breakpoint("ChainedMethodsAndConstructor.scala", "main", 3) -> smartStepInto(Target("id2()")),
      Breakpoint("A.scala", "id2", 11) -> stepOut,
      Breakpoint("ChainedMethodsAndConstructor.scala", "main", 3) -> smartStepInto(Target("asString()")),
      Breakpoint("A.scala", "asString", 15) -> resume
    )
  }

  addSourceFile("InnerClassAndConstructor.scala",
    s"""
       |object InnerClassAndConstructor {
       |  def main(args: Array[String]): Unit = {
       |    val s = new A(10).id1().asString $breakpoint
       |  }
       |
       |  class A(i: Int) {
       |    def id1() = {
       |      val x: A = this
       |      x
       |    }
       |
       |    def asString = "A"
       |  }
       |}
      """.stripMargin.trim
  )

  def testInnerClassAndConstructor(): Unit = {
    smartStepIntoTest()(
      Target("new A(int)"),
      Target("id1()"),
      Target("asString()")
    )(
      Breakpoint("InnerClassAndConstructor.scala", "main", 3) -> smartStepInto(Target("new A(int)")),
      Breakpoint("InnerClassAndConstructor.scala", "<init>", 6) -> stepOut,
      Breakpoint("InnerClassAndConstructor.scala", "main", 3) -> smartStepInto(Target("id1()")),
      Breakpoint("InnerClassAndConstructor.scala", "id1", 8) -> resume
    )
  }

  addSourceFile("InArguments.scala",
    s"""package test
       |
       |object InArguments {
       |  def foo(a: B, a1: B) = {}
       |
       |  def main(args: Array[String]): Unit = {
       |    val a = new B(2)
       |    foo(new B(1), a.id()) $breakpoint
       |  }
       |}
       |
       |class B(i: Int) {
       |  def id() = {
       |    val x: B = this
       |    x
       |  }
       |}""".stripMargin.trim
  )

  def testInArguments(): Unit = {
    smartStepIntoTest("test.InArguments")(
      Target("foo(B, B)"),
      Target("new B(int)"),
      Target("id()")
    )(
      Breakpoint("InArguments.scala", "main", 8) -> smartStepInto(Target("new B(int)")),
      Breakpoint("InArguments.scala", "<init>", 12) -> stepOut,
      Breakpoint("InArguments.scala", "main", 8) -> smartStepInto(Target("id()")),
      Breakpoint("InArguments.scala", "id", 14) -> resume
    )
  }

  addSourceFile("InfixAndApply.scala",
    s"""
       |object InfixAndApply {
       |
       |  def main(args: Array[String]): Unit = {
       |    val a = new C(2)
       |    a add C(1) $breakpoint
       |  }
       |}
       |
       |class C(i: Int) {
       |
       |  def add(a: C) = {
       |    "adding"
       |  }
       |}
       |
       |object C {
       |  def apply(i: Int) = new C(i)
       |}""".stripMargin.trim
  )

  def testInfixAndApply(): Unit = {
    smartStepIntoTest()(
      Target("add(C)"),
      Target("C.apply(int)")
    )(
      Breakpoint("InfixAndApply.scala", "main", 5) -> smartStepInto(Target("C.apply(int)")),
      Breakpoint("InfixAndApply.scala", "apply", 17) -> stepOut,
      Breakpoint("InfixAndApply.scala", "main", 5) -> smartStepInto(Target("add(C)")),
      Breakpoint("InfixAndApply.scala", "add", 12) -> resume
    )
  }

  addSourceFile("PostfixAndUnapply.scala",
    s"""
       |object PostfixAndUnapply {
       |
       |  def main(args: Array[String]): Unit = {
       |    new D(1) match {
       |      case a @ D(1) => a foo $breakpoint
       |    }
       |  }
       |}
       |
       |class D(val i: Int) {
       |  def foo() = {}
       |
       |}
       |
       |object D {
       |  def unapply(a: D) = Some(a.i)
       |}""".stripMargin.trim
  )

  def testPostfixAndUnapply(): Unit = {
    smartStepIntoTest()(
      Target("D.unapply(D)"),
      Target("foo()")
    )(
      Breakpoint("PostfixAndUnapply.scala", "main", 5) -> smartStepInto(Target("D.unapply(D)")),
      Breakpoint("PostfixAndUnapply.scala", "unapply", 16) -> resume,
      Breakpoint("PostfixAndUnapply.scala", "main", 5) -> resume
    )
  }

  addSourceFile("AnonymousClassFromTrait.scala",
    s"""
       |object AnonymousClassFromTrait {
       |
       |  def execute(processor: Processor) = processor.execute()
       |
       |  def main(args: Array[String]): Unit = {
       |     execute(new Processor { $breakpoint
       |       val z = 1
       |
       |       override def execute(): Unit = {
       |         "aaa"
       |       }
       |     })
       |  }
       |}
       |
       |trait Processor {
       |  def execute(): Unit
       |}""".stripMargin.trim
  )

  def testAnonymousClassFromTrait1(): Unit = {
    smartStepIntoTest("AnonymousClassFromTrait")(
      Target("execute(Processor)"),
      Target("new Processor()"),
      Target("new Processor.execute()")
    )(
      Breakpoint("AnonymousClassFromTrait.scala", "main", 6) -> smartStepInto(Target("new Processor()")),
      Breakpoint("AnonymousClassFromTrait.scala", "<init>", 6) -> resume
    )
  }

  def testAnonymousClassFromTrait2(): Unit = {
    smartStepIntoTest("AnonymousClassFromTrait")(
      Target("execute(Processor)"),
      Target("new Processor()"),
      Target("new Processor.execute()")
    )(
      Breakpoint("AnonymousClassFromTrait.scala", "main", 6) -> smartStepInto(Target("new Processor.execute()")),
      Breakpoint("AnonymousClassFromTrait.scala", "execute", 10) -> resume
    )
  }

  addSourceFile("AnonymousClassFromClass.scala",
    s"""
       |object AnonymousClassFromClass {
       |
       |  def execute(processor: ProcessorClass) = processor.execute()
       |
       |  def main(args: Array[String]): Unit = {
       |     execute(new ProcessorClass("aa") { $breakpoint
       |       val z = 1
       |
       |       override def execute(): Unit = {
       |         "aaa"
       |       }
       |     })
       |  }
       |}
       |
       |class ProcessorClass(s: String) {
       |  def execute(): Unit = {}
       |}""".stripMargin.trim
  )

  def testAnonymousClassFromClass1(): Unit = {
    smartStepIntoTest("AnonymousClassFromClass")(
      Target("execute(ProcessorClass)"),
      Target("new ProcessorClass()"),
      Target("new ProcessorClass.execute()")
    )(
      Breakpoint("AnonymousClassFromClass.scala", "main", 6) -> smartStepInto(Target("new ProcessorClass()")),
      Breakpoint("AnonymousClassFromClass.scala", "<init>", 6) -> resume
    )
  }

  def testAnonymousClassFromClass2(): Unit = {
    smartStepIntoTest("AnonymousClassFromClass")(
      Target("execute(ProcessorClass)"),
      Target("new ProcessorClass()"),
      Target("new ProcessorClass.execute()")
    )(
      Breakpoint("AnonymousClassFromClass.scala", "main", 6) -> smartStepInto(Target("new ProcessorClass.execute()")),
      Breakpoint("AnonymousClassFromClass.scala", "execute", 10) -> resume
    )
  }

  addSourceFile("ByNameArgument.scala",
    s"""
       |object ByNameArgument {
       |
       |  def inTryBlock(u: => String): Unit = {
       |    try {
       |      u
       |    }
       |    catch {
       |      case t: Throwable =>
       |    }
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    inTryBlock { $breakpoint
       |      val s = "a"
       |      s + "aaa"
       |    }
       |  }
       |}""".stripMargin.trim
  )

  def testByNameArgument1(): Unit = {
    smartStepIntoTest("ByNameArgument")(
      Target("inTryBlock(String)"),
      Target("u: => String")
    )(
      Breakpoint("ByNameArgument.scala", "main", 13) -> smartStepInto(Target("inTryBlock(String)")),
      Breakpoint("ByNameArgument.scala", "inTryBlock", 5) -> resume
    )
  }

  def testByNameArgument2(): Unit = {
    smartStepIntoTest("ByNameArgument")(
      Target("inTryBlock(String)"),
      Target("u: => String")
    )(
      Breakpoint("ByNameArgument.scala", "main", 13) -> smartStepInto(Target("u: => String")),
      Breakpoint("ByNameArgument.scala", "apply", 14) -> resume
    )
  }

  addSourceFile("LocalFunction.scala",
    s"""
       |object LocalFunction {
       |
       |  def main(args: Array[String]): Unit = {
       |    def foo(s: String): Unit = {
       |      println(s)
       |    }
       |
       |    foo("aaa") $breakpoint
       |  }
       |}""".stripMargin.trim
  )

  def testLocalFunction(): Unit = {
    smartStepIntoTest()(
      Target("foo(String)")
    )(
      Breakpoint("LocalFunction.scala", "main", 8) -> smartStepInto(Target("foo(String)")),
      Breakpoint("LocalFunction.scala", "foo$1", 5) -> resume
    )
  }

  addSourceFile("ImplicitConversion.scala",
    s"""
       |import scala.language.implicitConversions
       |
       |object ImplicitConversion {
       |
       |  implicit def string2Int(s: String): Int = Integer.valueOf(s)
       |
       |  def inc(i: Int): Int = {
       |    i + 1
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    inc("1") $breakpoint
       |  }
       |}""".stripMargin.trim
  )

  def testImplicitConversion(): Unit = {
    smartStepIntoTest()(
      Target("inc(int)"),
      Target("implicit string2Int(String)")
    )(
      Breakpoint("ImplicitConversion.scala", "main", 12) -> smartStepInto(Target("implicit string2Int(String)")),
      Breakpoint("ImplicitConversion.scala", "string2Int", 5) -> resume
    )
  }

  addSourceFile("ImplicitClass.scala",
    s"""
       |import scala.language.implicitConversions
       |
       |object ImplicitClass {
       |
       |  implicit class ObjectExt[T](v: T) {
       |    def toOption: Option[T] = Option(v)
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    "aaa".charAt(1).toOption $breakpoint
       |  }
       |}""".stripMargin.trim
  )

  def testImplicitClass(): Unit = {
    smartStepIntoTest()(
      Target("charAt(int)"),
      Target("implicit toOption()")
    )(
      Breakpoint("ImplicitClass.scala", "main", 10) -> smartStepInto(Target("implicit toOption()")),
      Breakpoint("ImplicitClass.scala", "toOption", 6) -> resume
    )
  }

  addSourceFile("ImplicitValueClass.scala",
    s"""
       |import scala.language.implicitConversions
       |
       |object ImplicitValueClass {
       |
       |  implicit class ObjectExt[T](val v: T) extends AnyVal {
       |    def toOption: Option[T] = Option(v)
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    "aaa".charAt(1).toOption $breakpoint
       |  }
       |}""".stripMargin.trim
  )

  def testImplicitValueClass(): Unit = {
    smartStepIntoTest()(
      Target("charAt(int)"),
      Target("implicit toOption$extension(T)")
    )(
      Breakpoint("ImplicitValueClass.scala", "main", 10) -> smartStepInto(Target("implicit toOption$extension(T)")),
      Breakpoint("ImplicitValueClass.scala", "toOption$extension", 6) -> resume
    )
  }

  addSourceFile("MethodValue.scala",
    s"""
       |object MethodValue {
       |  def main(args: Array[String]): Unit = {
       |    val a = new A(Seq(1, 2, 3))
       |    a.update(incr(2) _).update(MethodValue.id[Int](_)).update(a.decr) $breakpoint ${lambdaOrdinal(-1)}
       |  }
       |
       |  def incr(i: Int)(j: Int): Int = i + j
       |
       |  def id[T](t: T) = t
       |
       |  class A(var seq: Seq[Int]) {
       |    def update(f: Int => Int) = {
       |      seq = seq.map(f)
       |      this
       |    }
       |    def decr(i: Int) = i - 1
       |  }
       |}
       |""".stripMargin.trim
  )

  def testMethodValue(): Unit = {
    smartStepIntoTest()(
      Target("update(Function1<Object, Object>)"),
      Target("incr(int, int)"),
      Target("update(Function1<Object, Object>)"),
      Target("id(T)"),
      Target("update(Function1<Object, Object>)"),
      Target("decr(int)")
    )(
      Breakpoint("MethodValue.scala", "main", 4) -> smartStepInto(Target("incr(int, int)")),
      Breakpoint("MethodValue.scala", "incr", 7) -> stepOut,
      Breakpoint("MethodValue.scala", "apply$mcII$sp", 4) -> stepOut,
      Breakpoint("MethodValue.scala", "update", 13) -> stepOut,
      Breakpoint("MethodValue.scala", "main", 4) -> smartStepInto(Target("id(T)")),
      Breakpoint("MethodValue.scala", "id", 9) -> stepOut,
      Breakpoint("MethodValue.scala", "apply$mcII$sp", 4) -> stepOut,
      Breakpoint("MethodValue.scala", "update", 13) -> stepOut,
      Breakpoint("MethodValue.scala", "main", 4) -> smartStepInto(Target("decr(int)")),
      Breakpoint("MethodValue.scala", "decr", 16) -> resume
    )
  }

  addSourceFile("InsideLambda.scala",
    s"""
       |object InsideLambda {
       |  def multi(x: Double, y: Double): Double = x * y
       |
       |  def sum(x: Double, y: Double): Double = x + y
       |
       |  def main(args: Array[String]): Unit = {
       |    val celsiusDegrees = List(0)
       |    val fahrenheitDegrees = celsiusDegrees.map(i => sum(multi(i, 1.8), 32)) $breakpoint ${lambdaOrdinal(0)}
       |    println(fahrenheitDegrees)
       |  }
       |}
       |""".stripMargin.trim)

  def testInsideLambda(): Unit = {
    smartStepIntoTest()(
      Target("sum(double, double)"),
      Target("multi(double, double)")
    )(
      Breakpoint("InsideLambda.scala", "apply$mcDI$sp", 8) -> smartStepInto(Target("multi(double, double)")),
      Breakpoint("InsideLambda.scala", "multi", 2) -> stepOut,
      Breakpoint("InsideLambda.scala", "apply$mcDI$sp", 8) -> smartStepInto(Target("sum(double, double)")),
      Breakpoint("InsideLambda.scala", "sum", 4) -> resume
    )
  }
}
