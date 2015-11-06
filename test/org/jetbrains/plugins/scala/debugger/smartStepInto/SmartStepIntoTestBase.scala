package org.jetbrains.plugins.scala.debugger.smartStepInto

import com.intellij.debugger.actions.SmartStepTarget
import org.jetbrains.plugins.scala.debugger.{ScalaDebuggerTestCase, ScalaVersion_2_11, ScalaVersion_2_12}
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.junit.Assert

import scala.collection.JavaConverters._

/**
 * @author Nikolay.Tropin
 */
class SmartStepIntoTest extends SmartStepIntoTestBase with ScalaVersion_2_11

class SmartStepIntoTest_212 extends SmartStepIntoTestBase with ScalaVersion_2_12 {
  override def testByNameArgument(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      checkSmartStepTargets("inTryBlock(String)", "u: => String")
      checkSmartStepInto("inTryBlock(String)", "ByNameArgument.scala", "inTryBlock", 5)
    }
    runDebugger() {
      waitForBreakpoint()
      checkSmartStepInto("u: => String", "ByNameArgument.scala", "ByNameArgument$$$anonfun$1", 14)
    }
  }
}

abstract class SmartStepIntoTestBase extends ScalaDebuggerTestCase {

  protected val handler = new ScalaSmartStepIntoHandler
  protected var targets: Seq[SmartStepTarget] = null

  def availableSmartStepTargets(): Seq[SmartStepTarget] = managed {
    inReadAction {
      handler.findSmartStepTargets(currentSourcePosition).asScala
    }
  }

  def checkSmartStepTargets(expected: String*): Unit = {
    targets = availableSmartStepTargets()
    Assert.assertEquals("Wrong set of smart step targets:", expected, targets.map(_.getPresentation))
  }

  def checkSmartStepInto(target: String, source: String, methodName: String, line: Int) = {
    if (targets == null) targets = availableSmartStepTargets()
    val sst = targets.find(_.getPresentation == target)
    Assert.assertTrue(s"Cannot find such target: $target", sst.isDefined)
    doSmartStepInto(sst.get)
    checkLocation(source, methodName, line)
  }

  private def doSmartStepInto(target: SmartStepTarget): Unit = {
    val filter = handler.createMethodFilter(target)
    val stepIntoCommand = getDebugProcess.createStepIntoCommand(suspendContext, false, filter)
    getDebugProcess.getManagerThread.invokeAndWait(stepIntoCommand)
    waitForBreakpoint()
  }

  addFileWithBreakpoints("ChainedMethodsAndConstructor.scala",
    s"""
       |object ChainedMethodsAndConstructor {
       |  def main(args: Array[String]) {
       |    val s = new A(11).id1().id2.asString  $bp
       |  }
       |}
      """.stripMargin.trim()
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
       |""".stripMargin.trim()
  )
  def testChainedMethodsAndConstructor() {
    runDebugger() {
      waitForBreakpoint()
      checkSmartStepTargets("new A(int)", "id1()", "id2()", "asString()")
      checkSmartStepInto("new A(int)", "A.scala", "<init>", 1)
    }
    runDebugger() {
      waitForBreakpoint()
      checkSmartStepInto("id1()", "A.scala", "id1", 6)
    }
    runDebugger() {
      waitForBreakpoint()
      checkSmartStepInto("id2()", "A.scala", "id2", 11)
    }
  }

  addFileWithBreakpoints("InnerClassAndConstructor.scala",
    s"""
       |object InnerClassAndConstructor {
       |  def main(args: Array[String]) {
       |    val s = new A(10).id1().asString $bp
       |  }
       |
       |  class A(i: Int) {
       |
       |    def id1() = {
       |      val x: A = this
       |      x
       |    }
       |
       |    def asString = "A"
       |  }
       |}
      """.stripMargin.trim()
  )
  def testInnerClassAndConstructor(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      checkSmartStepTargets("new A(int)", "id1()", "asString()")
      checkSmartStepInto("new A(int)", "InnerClassAndConstructor.scala", "<init>", 6)
    }
    runDebugger() {
      waitForBreakpoint()
      checkSmartStepInto("id1()", "InnerClassAndConstructor.scala", "id1", 9)
    }
  }

  addFileWithBreakpoints("InArguments.scala",
    s"""
       |object InArguments {
       |
       |  def foo(a: B, a1: B) = {}
       |
       |  def main(args: Array[String]) {
       |    val a = new B(2)
       |    foo(new B(1), a.id()) $bp
       |  }
       |}
       |
       |class B(i: Int) {
       |
       |  def id() = {
       |    val x: B = this
       |    x
       |  }
       |
       |  def asString = "B"
       |}""".stripMargin.trim()
  )
  def testInArguments(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      checkSmartStepTargets("foo(B, B)", "new B(int)", "id()")
      checkSmartStepInto("new B(int)", "InArguments.scala", "<init>", 11)
    }
    runDebugger() {
      waitForBreakpoint()
      checkSmartStepInto("id()", "InArguments.scala", "id", 14)
    }
  }

  addFileWithBreakpoints("InfixAndApply.scala",
    s"""
       |object InfixAndApply {
       |
       |  def main(args: Array[String]) {
       |    val a = new C(2)
       |    a add C(1) $bp
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
       |}""".stripMargin.trim()
  )
  def testInfixAndApply(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      checkSmartStepTargets("add(C)", "C.apply(int)")
      checkSmartStepInto("add(C)", "InfixAndApply.scala", "add", 12)
    }
    runDebugger() {
      waitForBreakpoint()
      checkSmartStepInto("C.apply(int)", "InfixAndApply.scala", "apply", 17)
    }
  }

  addFileWithBreakpoints("PostfixAndUnapply.scala",
    s"""
       |object PostfixAndUnapply {
       |
       |  def main(args: Array[String]) {
       |    new D(1) match {
       |      case a @ D(1) => a foo $bp
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
       |}""".stripMargin.trim()
  )
  def testPostfixAndUnapply(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      checkSmartStepTargets("D.unapply(D)", "foo()")
      checkSmartStepInto("D.unapply(D)", "PostfixAndUnapply.scala", "unapply", 16)
    }
    //    runDebugger("Sample") {  //should work after cleaning up match statements
    //      waitForBreakpoint()
    //      checkSmartStepInto("foo()", "Sample.scala", "foo", 11)
    //    }
  }

  addFileWithBreakpoints("AnonymousClassFromTrait.scala",
    s"""
       |object AnonymousClassFromTrait {
       |
       |  def execute(processor: Processor) = processor.execute()
       |
       |  def main(args: Array[String]) {
       |     execute(new Processor { $bp
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
       |  def execute()
       |}""".stripMargin.trim()
  )
  def testAnonymousClassFromTrait(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      checkSmartStepTargets("execute(Processor)", "new Processor()", "new Processor.execute()")
      checkSmartStepInto("new Processor()", "AnonymousClassFromTrait.scala", "<init>", 6)
    }
    runDebugger() {
      waitForBreakpoint()
      checkSmartStepInto("new Processor.execute()", "AnonymousClassFromTrait.scala", "execute", 10)
    }
  }

  addFileWithBreakpoints("AnonymousClassFromClass.scala",
    s"""
       |object AnonymousClassFromClass {
       |
       |  def execute(processor: ProcessorClass) = processor.execute()
       |
       |  def main(args: Array[String]) {
       |     execute(new ProcessorClass("aa") { $bp
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
       |}""".stripMargin.trim()
  )
  def testAnonymousClassFromClass(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      checkSmartStepTargets("execute(ProcessorClass)", "new ProcessorClass()", "new ProcessorClass.execute()")
      checkSmartStepInto("new ProcessorClass()", "AnonymousClassFromClass.scala", "<init>", 6)
    }
    runDebugger() {
      waitForBreakpoint()
      checkSmartStepInto("new ProcessorClass.execute()", "AnonymousClassFromClass.scala", "execute", 10)
    }
  }

  addFileWithBreakpoints("ByNameArgument.scala",
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
       |  def main(args: Array[String]) {
       |    inTryBlock { $bp
       |      val s = "a"
       |      s + "aaa"
       |    }
       |  }
       |}""".stripMargin.trim()
  )
  def testByNameArgument(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      checkSmartStepTargets("inTryBlock(String)", "u: => String")
      checkSmartStepInto("inTryBlock(String)", "ByNameArgument.scala", "inTryBlock", 5)
    }
    runDebugger() {
      waitForBreakpoint()
      checkSmartStepInto("u: => String", "ByNameArgument.scala", "apply", 14)
    }
  }

  addFileWithBreakpoints("LocalFunction.scala",
    s"""
       |object LocalFunction {
       |
       |  def main(args: Array[String]) {
       |    def foo(s: String): Unit = {
       |      println(s)
       |    }
       |
       |    foo("aaa") $bp
       |  }
       |}""".stripMargin.trim()
  )
  def testLocalFunction(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      checkSmartStepTargets("foo(String)")
      checkSmartStepInto("foo(String)", "LocalFunction.scala", "foo$1", 5)
    }
  }

  addFileWithBreakpoints("ImplicitConversion.scala",
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
       |  def main(args: Array[String]) {
       |    inc("1") $bp
       |  }
       |}""".stripMargin.trim()
  )
  def testImplicitConversion(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      checkSmartStepTargets("inc(int)", "implicit string2Int(String)")
      checkSmartStepInto("implicit string2Int(String)", "ImplicitConversion.scala", "string2Int", 5)
    }
  }

  addFileWithBreakpoints("ImplicitClass.scala",
    s"""
       |import scala.language.implicitConversions
       |
       |object ImplicitClass {
       |
       |  implicit class ObjectExt[T](v: T) {
       |    def toOption: Option[T] = Option(v)
       |  }
       |
       |  def main(args: Array[String]) {
       |    "aaa".charAt(1).toOption $bp
       |  }
       |}""".stripMargin.trim()
  )
  def testImplicitClass(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      checkSmartStepTargets("charAt(int)", "implicit toOption()")
      checkSmartStepInto("implicit toOption()", "ImplicitClass.scala", "toOption", 6)
    }
  }

  addFileWithBreakpoints("ImplicitValueClass.scala",
    s"""
       |import scala.language.implicitConversions
       |
       |object ImplicitValueClass {
       |
       |  implicit class ObjectExt[T](val v: T) extends AnyVal {
       |    def toOption: Option[T] = Option(v)
       |  }
       |
       |  def main(args: Array[String]) {
       |    "aaa".charAt(1).toOption $bp
       |  }
       |}""".stripMargin.trim()
  )
  def testImplicitValueClass(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      checkSmartStepTargets("charAt(int)", "implicit toOption()")
      checkSmartStepInto("implicit toOption()", "ImplicitValueClass.scala", "toOption$extension", 6)
    }
  }

  addFileWithBreakpoints("MethodValue.scala",
    s"""
       |object MethodValue {
       |  def main(args: Array[String]): Unit = {
       |    val a = new A(Seq(1, 2, 3))
       |    a.update(incr(2) _).update(MethodValue.id[Int](_)).update(a.decr) $bp
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
    runDebugger() {
      waitForBreakpoint()
      checkSmartStepTargets("update(Function1<Object, Object>)", "incr(int, int)", "update(Function1<Object, Object>)", "id(T)", "update(Function1<Object, Object>)", "decr(int)")
      checkSmartStepInto("id(T)", "MethodValue.scala", "id", 9)
    }
    runDebugger() {
      waitForBreakpoint()
      checkSmartStepInto("decr(int)", "MethodValue.scala", "decr", 16)
    }
    runDebugger() {
      waitForBreakpoint()
      checkSmartStepInto("incr(int, int)", "MethodValue.scala", "incr", 7)
    }
  }

}