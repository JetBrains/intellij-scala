package org.jetbrains.plugins.scala.debugger.stepInto

import com.intellij.debugger.settings.DebuggerSettings
import org.jetbrains.plugins.scala.debugger.{ScalaDebuggerTestCase, ScalaVersion_2_11, ScalaVersion_2_12}

/**
 * @author Nikolay.Tropin
 */

class StepIntoTest extends StepIntoTestBase with ScalaVersion_2_11
class StepIntoTest_212 extends StepIntoTestBase with ScalaVersion_2_12

abstract class StepIntoTestBase extends ScalaDebuggerTestCase {
  def doStepInto(): Unit = {
    val stepIntoCommand = getDebugProcess.createStepIntoCommand(suspendContext, false, null)
    getDebugProcess.getManagerThread.invokeAndWait(stepIntoCommand)
    waitForBreakpoint()
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
      waitForBreakpoint()
      doStepInto()
      checkLocation("Simple.scala", "foo", 9)
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
      waitForBreakpoint()
      doStepInto()
      checkLocation("ZZZ.scala", "<init>", 1)
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
      waitForBreakpoint()
      doStepInto()
      checkLocation("QQQ.scala", "apply", 9)
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
      waitForBreakpoint()
      doStepInto()
      checkLocation("package.scala", "foo", 3)
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
      waitForBreakpoint()
      doStepInto()
      checkLocation("FromPackageObject.scala", "bar", 9)
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
      waitForBreakpoint()
      doStepInto()
      checkLocation("EEE.scala", "withDefault", 3)
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
      waitForBreakpoint()
      doStepInto()
      checkLocation("RRR.scala", "foo", 3)
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
      waitForBreakpoint()
      doStepInto()
      checkLocation("TTT.scala", "unapply", 2)
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
      waitForBreakpoint()
      doStepInto()
      checkLocation("ImplicitConversion.scala", "a2B", 7)
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
      waitForBreakpoint()
      doStepInto()
      checkLocation("LazyVal.scala", "lzy$lzycompute", 2)
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
      waitForBreakpoint()
      doStepInto()
      checkLocation("LazyVal2.scala", "foo", 10)
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
      waitForBreakpoint()
      doStepInto()
      checkLocation("SimpleGetters.scala", "main", 7)

      doStepInto()
      checkLocation("SimpleGetters.scala", "sum", 10)
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
      waitForBreakpoint()
      doStepInto()
      checkLocation("CustomizedPatternMatching.scala", "foo", 8)

      doStepInto()
      checkLocation("CustomizedPatternMatching.scala", "b", 16)
    }
  }


}