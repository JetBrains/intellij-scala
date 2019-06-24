package org.jetbrains.plugins.scala
package debugger
package stepInto

import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.settings.DebuggerSettings
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[DebuggerTests]))
class StepIntoTest extends StepIntoTestBase {
  override implicit val version: ScalaVersion = Scala_2_11

  override def testPrivateMethodUsedInLambda(): Unit = {
    runDebugger() {
      waitBreakpointAndStepInto("PrivateMethodUsedInLambda.scala", "PrivateMethodUsedInLambda$$privateMethod", 3)
    }
  }
}

@Category(Array(classOf[DebuggerTests]))
class StepIntoTest_212 extends StepIntoTestBase {
  override implicit val version: ScalaVersion = Scala_2_12

  addFileWithBreakpoints("SamAbstractClass.scala",
    s"""object SamAbstractClass {
       |  def main(args: Array[String]): Unit = {
       |    val test: Parser[String] = (in: String) => {
       |      println("Printing") //should step here
       |      in
       |    }
       |
       |    test.parse("aaa")$bp
       |  }
       |}
       |
       |abstract class Parser[T] {
       |  def parse(s: String): T
       |}
      """.stripMargin.trim()
  )
  def testSamAbstractClass() {
    runDebugger() {
      waitBreakpointAndStepInto("SamAbstractClass.scala", "SamAbstractClass$$$anonfun$main$1", 4)
    }
  }
}

abstract class StepIntoTestBase extends ScalaDebuggerTestCase {
  protected def waitBreakpointAndStepInto(fileName: String, methodName: String, line: Int): Unit = {
    val breakpointCtx = waitForBreakpoint()
    val stepIntoCommand = getDebugProcess.createStepIntoCommand(breakpointCtx, false, null)
    getDebugProcess.getManagerThread.invokeAndWait(stepIntoCommand)

    implicit val stepIntoCtx: SuspendContextImpl = waitForBreakpoint()
    checkLocation(fileName, methodName, line)
  }


  addFileWithBreakpoints("Simple.scala",
    s"""
       |object Simple {
       |  def main(args: Array[String]) {
       |    val x = AAA.foo("123") $bp
       |  }
       |}
       |
       |object AAA {
       |  def foo(s: String) = {
       |    s.substring(1) //should step here
       |  }
       |}
      """.stripMargin.trim()
  )

  def testSimple() {
    addBreakpoint(2, "Simple.scala")
    runDebugger() {
      waitBreakpointAndStepInto("Simple.scala", "foo", 9)
    }
  }

  addFileWithBreakpoints("Constructor.scala",
    s"""
       |object Constructor {
       |  def main(args: Array[String]) {
       |    val x = new ZZZ(1).foo() $bp
       |  }
       |}
      """.stripMargin.trim()
  )
  addSourceFile("ZZZ.scala",
    s"""
       |class ZZZ(z: Int) { //should step here
       |  val x = z
       |
       |  def foo(): Int = z
       |}""".stripMargin.trim()
  )

  def testConstructor() {
    addBreakpoint(2, "Constructor.scala")
    runDebugger("Constructor") {
      waitBreakpointAndStepInto("ZZZ.scala", "<init>", 1)
    }
  }

  addFileWithBreakpoints("Sample.scala",
    s"""
       |object ApplyMethod {
       |  def main(args: Array[String]) {
       |    val x = QQQ(1).foo() $bp
       |  }
       |}
      """.stripMargin.trim()
  )
  addFileWithBreakpoints("QQQ.scala",
    s"""
       |class QQQ(z: Int) {
       |  val x = z
       |
       |  def foo(): Int = z
       |}
       |
       |object QQQ {
       |  def apply(z: Int) = {
       |    new QQQ(z)  //should step here
       |  }
       |}
       |""".stripMargin.trim()
  )

  def testApplyMethod(): Unit = {
    runDebugger() {
      waitBreakpointAndStepInto("QQQ.scala", "apply", 9)
    }
  }

  addFileWithBreakpoints("IntoPackageObject.scala",
    s"""
       |package test
       |
       |object IntoPackageObject {
       |  def main(args: Array[String]) {
       |    foo(1)$bp
       |  }
       |}
       |
      """.stripMargin.trim()
  )
  addSourceFile("test/package.scala",
    s"""
       |package object test {
       |  def foo(i: Int): Unit = {
       |    println("foo!") //should step here
       |  }
       |}
       |""".stripMargin.trim()
  )

  def testIntoPackageObject(): Unit = {
    addBreakpoint(4, "IntoPackageObject.scala")
    runDebugger("test.IntoPackageObject") {
      waitBreakpointAndStepInto("package.scala", "foo", 3)
    }
  }

  addSourceFile("test1/FromPackageObject.scala",
    s"""
       |package test1
       |
       |object FromPackageObject {
       |  def main(args: Array[String]) {
       |    foo(1)
       |  }
       |
       |  def bar() {
       |    println("bar") //should step here
       |  }
       |}
       |
      """.stripMargin.trim()
  )
  addFileWithBreakpoints("test1/package.scala",
    s"""
       |package object test1 {
       |  def foo(i: Int): Unit = {
       |    FromPackageObject.bar() $bp
       |  }
       |}
       |""".stripMargin.trim()
  )

  def testFromPackageObject(): Unit = {
    runDebugger("test1.FromPackageObject") {
      waitBreakpointAndStepInto("FromPackageObject.scala", "bar", 9)
    }
  }

  addFileWithBreakpoints("WithDefaultParam.scala",
    s"""
       |object WithDefaultParam {
       |  def main(args: Array[String]) {
       |    val x = EEE.withDefault(1)  $bp
       |  }
       |}
      """.stripMargin.trim()
  )
  addSourceFile("EEE.scala",
    s"""
       |object EEE {
       |  def withDefault(z: Int, s: String = "default") = {
       |    println("hello")  //should step here
       |  }
       |}""".stripMargin.trim()
  )

  def testWithDefaultParam() {
    runDebugger() {
      waitBreakpointAndStepInto("EEE.scala", "withDefault", 3)
    }
  }

  addFileWithBreakpoints("TraitMethod.scala",
    s"""
       |object TraitMethod extends RRR{
       |  def main(args: Array[String]) {
       |    val x = foo(1)  $bp
       |  }
       |}
      """.stripMargin.trim()
  )
  addFileWithBreakpoints("RRR.scala",
    s"""
       |trait RRR {
       |  def foo(z: Int) = {
       |    println("hello")  //should step here
       |  }
       |}""".stripMargin.trim()
  )

  def testTraitMethod() {
    runDebugger() {
      waitBreakpointAndStepInto("RRR.scala", "foo", 3)
    }
  }

  addFileWithBreakpoints("UnapplyMethod.scala",
    s"""
       |object UnapplyMethod {
       |  def main(args: Array[String]) {
       |    val z = Some(1)
       |    z match {
       |      case TTT(a) => TTT(a)  $bp
       |      case _ =>
       |    }
       |  }
       |}
      """.stripMargin.trim()
  )
  addFileWithBreakpoints("TTT.scala",
    s"""
       |object TTT {
       |  def unapply(z: Option[Int]) = z  //should step here
       |
        |  def apply(i: Int) = Some(i)
       |}""".stripMargin.trim()
  )

  def testUnapplyMethod() {
    runDebugger() {
      waitBreakpointAndStepInto("TTT.scala", "unapply", 2)
    }
  }

  addFileWithBreakpoints("ImplicitConversion.scala",
    s"""
       |import scala.language.implicitConversions
       |
       |object ImplicitConversion {
       |
       |  class A; class B
       |
       |  implicit def a2B(a: A): B = new B  //should step here
       |
       |  def foo(b: B): Unit = {}
       |
       |  def main(args: Array[String]) {
       |    val a = new A
       |    foo(a) $bp
       |  }
       |}
      """.stripMargin.trim()
  )

  def testImplicitConversion() {
    runDebugger() {
      waitBreakpointAndStepInto("ImplicitConversion.scala", "a2B", 7)
    }
  }

  addFileWithBreakpoints("LazyVal.scala",
    s"""
       |object LazyVal {
       |  lazy val lzy = Some(1)  //should step here
       |
        |  def main(args: Array[String]) {
       |    val x = lzy $bp
       |  }
       |}
      """.stripMargin.trim()
  )

  def testLazyVal(): Unit = {
    runDebugger() {
      waitBreakpointAndStepInto("LazyVal.scala", "lzy$lzycompute", 2)
    }
  }

  addFileWithBreakpoints("LazyVal2.scala",
    s"""
       |object LazyVal2 {
       |  lazy val lzy = new AAA
       |
        |  def main(args: Array[String]) {
       |    val x = lzy
       |    val y = lzy.foo() $bp
       |  }
       |
        |  class AAA {
       |    def foo() {} //should step here
       |  }
       |}
      """.stripMargin.trim()
  )

  def testLazyVal2(): Unit = {
    runDebugger() {
      waitBreakpointAndStepInto("LazyVal2.scala", "foo", 10)
    }
  }

  addFileWithBreakpoints("SimpleGetters.scala",
    s"""object SimpleGetters {
       |  val z = 0
       |
      |  def main(args: Array[String]) {
       |    val x = new SimpleGetters
       |    x.getA $bp
       |    sum(x.z, x.gB)
       |  }
       |
      |  def sum(i1: Int, i2: Int) = i1 + i2
       |}
       |
      |class SimpleGetters {
       |  val a = 0
       |  var b = 1
       |
      |  def getA = a
       |  def gB = this.b
       |  def z = SimpleGetters.z
       |}
    """.stripMargin.trim)

  def testSimpleGetters(): Unit = {
    DebuggerSettings.getInstance().SKIP_GETTERS = true
    runDebugger() {
      waitBreakpointAndStepInto("SimpleGetters.scala", "main", 7)
      waitBreakpointAndStepInto("SimpleGetters.scala", "sum", 10)
      DebuggerSettings.getInstance().SKIP_GETTERS = false
    }
  }

  addFileWithBreakpoints("CustomizedPatternMatching.scala",
    s"""object CustomizedPatternMatching {
       |  def main(args: Array[String]) {
       |    val b = new B()
       |    foo(b)$bp
       |  }
       |
      |  def foo(b: B): Unit = {
       |    b.b() match {
       |      case "a" =>
       |      case "b" =>
       |      case _ =>
       |    }
       |  }
       |
      |  class B {
       |    def b() = "b"
       |  }
       |}
    """.stripMargin.trim)

  def testCustomizedPatternMatching(): Unit = {
    runDebugger() {
      waitBreakpointAndStepInto("CustomizedPatternMatching.scala", "foo", 8)
      waitBreakpointAndStepInto("CustomizedPatternMatching.scala", "b", 16)
    }
  }

  addFileWithBreakpoints("PrivateMethodUsedInLambda.scala",
    s"""object PrivateMethodUsedInLambda {
       |  private def privateMethod(i: Int) = {
       |    "hello!" //should step here
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    val s = for (x <- Seq(1, 2)) {
       |      privateMethod(x)$bp
       |    }
       |  }
       |}""".stripMargin)

  def testPrivateMethodUsedInLambda(): Unit = {
    runDebugger() {
      waitBreakpointAndStepInto("PrivateMethodUsedInLambda.scala", "privateMethod", 3)
    }
  }

  addFileWithBreakpoints("Specialization.scala",
    s"""class FunctionA extends Function[Int, Int] {
       |  override def apply(v1: Int): Int = {
       |    "stop"
       |    v1
       |  }
       |}
       |
       |class FunctionB extends Function[String, Int] {
       |  override def apply(v1: String): Int = {
       |    "stop"
       |    v1.length
       |  }
       |}
       |
       |object Specialization {
       |  def main(args: Array[String]): Unit = {
       |    println("Hello, world!")
       |
       |    val a = new FunctionA
       |    val b = new FunctionB
       |    a(1)$bp
       |    b("2")$bp
       |  }
       |}
      """.stripMargin)

  def testSpecialization(): Unit = {
    runDebugger() {
      waitBreakpointAndStepInto("Specialization.scala", "apply$mcII$sp", 3)
      resume()
      waitBreakpointAndStepInto("Specialization.scala", "apply", 10)
    }
  }

  addFileWithBreakpoints("CustomTraitForwarder.scala",
    s"""|object CustomTraitForwarder {
        |
        |  def main(args: Array[String]): Unit = {
        |    val inh = new Inheritor
        |    inh.foo(1)$bp
        |  }
        |}
        |
        |class Inheritor extends BaseTrait1 with BaseTrait2 {
        |  override def foo(x: Int): Int = super[BaseTrait1].foo(x)
        |}
        |
        |trait BaseTrait1 {
        |  def foo(x: Int): Int = {
        |    x
        |  }
        |}
        |
        |trait BaseTrait2 {
        |  def foo(x: Int): Int = {
        |    x
        |  }
        |}
    """.stripMargin)

  def testCustomTraitForwarder(): Unit = {
    val debuggerSettings = DebuggerSettings.getInstance()
    val old = debuggerSettings.SKIP_SYNTHETIC_METHODS
    debuggerSettings.SKIP_SYNTHETIC_METHODS = true
    runDebugger() {
      try {
        waitBreakpointAndStepInto("CustomTraitForwarder.scala", "foo", 10)
      } finally {
        debuggerSettings.SKIP_SYNTHETIC_METHODS = old
      }
    }
  }
}
