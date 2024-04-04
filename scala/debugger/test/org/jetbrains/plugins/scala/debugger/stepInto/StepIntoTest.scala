package org.jetbrains.plugins.scala
package debugger
package stepInto

import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.settings.DebuggerSettings
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.junit.Assert.{assertTrue, fail}

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.stream.Collectors
import scala.jdk.CollectionConverters._

class StepIntoTest_2_11 extends StepIntoTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_11

  override def testPrivateMethodUsedInLambda(): Unit = {
    stepIntoTest()(
      Breakpoint("PrivateMethodUsedInLambda.scala", "apply$mcVI$sp", 8) -> stepInto,
      Breakpoint("PrivateMethodUsedInLambda.scala", "PrivateMethodUsedInLambda$$privateMethod", 3) -> resume,
      Breakpoint("PrivateMethodUsedInLambda.scala", "apply$mcVI$sp", 8) -> stepInto,
      Breakpoint("PrivateMethodUsedInLambda.scala", "PrivateMethodUsedInLambda$$privateMethod", 3) -> resume
    )
  }

  override def testUnapplyMethod(): Unit = {
    stepIntoTest()(
      Breakpoint("UnapplyMethod.scala", "main", 5) -> stepInto,
      Breakpoint("TTT.scala", "unapply", 2) -> resume,
      Breakpoint("UnapplyMethod.scala", "main", 5) -> resume
    )
  }
}

class StepIntoTest_2_12 extends StepIntoTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_12

  addSourceFile("SamAbstractClass.scala",
    s"""object SamAbstractClass {
       |  def main(args: Array[String]): Unit = {
       |    val test: Parser[String] = (in: String) => {
       |      println("Printing") //should step here
       |      in
       |    }
       |
       |    test.parse("aaa") $breakpoint
       |  }
       |}
       |
       |abstract class Parser[T] {
       |  def parse(s: String): T
       |}
      """.stripMargin.trim
  )

  def testSamAbstractClass(): Unit = {
    stepIntoTest()(
      Breakpoint("SamAbstractClass.scala", "main", 8) -> stepInto,
      Breakpoint("SamAbstractClass.scala", "SamAbstractClass$$$anonfun$main$1", 4) -> resume
    )
  }
}

class StepIntoTest_2_13 extends StepIntoTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13
}

class StepIntoTest_3 extends StepIntoTest_2_12 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  override def testSamAbstractClass(): Unit = {
    stepIntoTest()(
      Breakpoint("SamAbstractClass.scala", "main", 8) -> stepInto,
      Breakpoint("SamAbstractClass.scala", "SamAbstractClass$$$_$_$$anonfun$1", 4) -> resume
    )
  }

  override def testLazyVal(): Unit = {
    stepIntoTest()(
      Breakpoint("LazyVal.scala", "main", 5) -> stepInto,
      Breakpoint("LazyVal.scala", "main", 6) -> resume
    )
  }

  override def testPrivateMethodUsedInLambda(): Unit = {
    stepIntoTest()(
      Breakpoint("PrivateMethodUsedInLambda.scala", "$anonfun$1", 8) -> stepInto,
      Breakpoint("PrivateMethodUsedInLambda.scala", "privateMethod", 3) -> resume,
      Breakpoint("PrivateMethodUsedInLambda.scala", "$anonfun$1", 8) -> stepInto,
      Breakpoint("PrivateMethodUsedInLambda.scala", "privateMethod", 3) -> resume
    )
  }
}

class StepIntoTest_3_RC extends StepIntoTest_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_RC

  // TODO: Revisit lazy vals in Scala 3.4+
  override def testLazyVal(): Unit = {}
}

abstract class StepIntoTestBase extends ScalaDebuggerTestCase {

  protected case class Breakpoint(file: String, method: String, line: Int)

  private val expectedTargetsQueue: ConcurrentLinkedQueue[(Breakpoint, SuspendContextImpl => Unit)] =
    new ConcurrentLinkedQueue()

  override protected def tearDown(): Unit = {
    try {
      if (!expectedTargetsQueue.isEmpty) {
        val remaining =
          expectedTargetsQueue.stream()
            .collect(Collectors.toList[(Breakpoint, SuspendContextImpl => Unit)])
            .asScala.map(_._1)
        fail(s"The debugger did not stop on all expected breakpoints. Remaining: $remaining")
      }
    } finally {
      super.tearDown()
    }
  }

  protected def stepIntoTest(mainClass: String = getTestName(false))
                            (targets: (Breakpoint, SuspendContextImpl => Unit)*): Unit = {
    assertTrue("The test should stop on at least 1 breakpoint", targets.nonEmpty)
    expectedTargetsQueue.addAll(targets.asJava)

    createLocalProcess(mainClass)

    onEveryBreakpoint { ctx =>
      val loc = ctx.getFrameProxy.getStackFrame.location()
      val debugProcess = getDebugProcess
      val positionManager = ScalaPositionManager.instance(debugProcess).getOrElse(new ScalaPositionManager(debugProcess))
      val srcPos = inReadAction(positionManager.getSourcePosition(loc))
      val actual = Breakpoint(loc.sourceName(), loc.method().name(), srcPos.getLine + 1)
      Option(expectedTargetsQueue.poll()) match {
        case None =>
          fail(s"The debugger stopped on $actual, but there were no expected breakpoints left")
        case Some((expected, cont)) =>
          assertEquals(expected, actual)
          cont(ctx)
      }
    }
  }

  addSourceFile("Simple.scala",
    s"""object Simple {
       |  def main(args: Array[String]): Unit = {
       |    val x = AAA.foo("123") $breakpoint
       |  }
       |}
       |
       |object AAA {
       |  def foo(s: String) = {
       |    s.substring(1) //should step here
       |  }
       |}
      """.stripMargin.trim
  )

  def testSimple(): Unit = {
    stepIntoTest()(
      Breakpoint("Simple.scala", "main", 3) -> stepInto,
      Breakpoint("Simple.scala", "foo", 9) -> resume
    )
  }

  addSourceFile("Constructor.scala",
    s"""
       |object Constructor {
       |  def main(args: Array[String]): Unit = {
       |    val x = new ZZZ(1).foo() $breakpoint
       |  }
       |}
      """.stripMargin.trim
  )
  addSourceFile("ZZZ.scala",
    s"""
       |class ZZZ(z: Int) { //should step here
       |  val x = z
       |
       |  def foo(): Int = z
       |}""".stripMargin.trim
  )

  def testConstructor(): Unit = {
    stepIntoTest()(
      Breakpoint("Constructor.scala", "main", 3) -> stepInto,
      Breakpoint("ZZZ.scala", "<init>", 1) -> resume
    )
  }

  addSourceFile("MyApplyMethod.scala",
    s"""object MyApplyMethod {
       |  def main(args: Array[String]): Unit = {
       |    val x = QQQ(1).foo() $breakpoint
       |  }
       |}
      """.stripMargin.trim
  )
  addSourceFile("QQQ.scala",
    s"""class QQQ(z: Int) {
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
       |""".stripMargin.trim
  )

  def testApplyMethod(): Unit = {
    stepIntoTest("MyApplyMethod")(
      Breakpoint("MyApplyMethod.scala", "main", 3) -> stepInto,
      Breakpoint("QQQ.scala", "apply", 9) -> resume
    )
  }

  addSourceFile("IntoPackageObject.scala",
    s"""
       |package test
       |
       |object IntoPackageObject {
       |  def main(args: Array[String]): Unit = {
       |    foo(1) $breakpoint
       |  }
       |}
       |
      """.stripMargin.trim
  )
  addSourceFile("test/package.scala",
    s"""
       |package object test {
       |  def foo(i: Int): Unit = {
       |    println("foo!") //should step here
       |  }
       |}
       |""".stripMargin.trim
  )

  def testIntoPackageObject(): Unit = {
    stepIntoTest("test.IntoPackageObject")(
      Breakpoint("IntoPackageObject.scala", "main", 5) -> stepInto,
      Breakpoint("package.scala", "foo", 3) -> resume
    )
  }

  addSourceFile("test1/FromPackageObject.scala",
    s"""
       |package test1
       |
       |object FromPackageObject {
       |  def main(args: Array[String]): Unit = {
       |    foo(1) $breakpoint
       |  }
       |
       |  def bar(): Unit = {
       |    println("bar") //should step here
       |  }
       |}
       |
      """.stripMargin.trim
  )
  addSourceFile("test1/package.scala",
    s"""
       |package object test1 {
       |  def foo(i: Int): Unit = {
       |    FromPackageObject.bar() $breakpoint
       |  }
       |}
       |""".stripMargin.trim
  )

  def testFromPackageObject(): Unit = {
    stepIntoTest("test1.FromPackageObject")(
      Breakpoint("FromPackageObject.scala", "main", 5) -> stepInto,
      Breakpoint("package.scala", "foo", 3) -> stepInto,
      Breakpoint("FromPackageObject.scala", "bar", 9) -> resume
    )
  }

  addSourceFile("WithDefaultParam.scala",
    s"""
       |object WithDefaultParam {
       |  def main(args: Array[String]): Unit = {
       |    val x = EEE.withDefault(1) $breakpoint
       |  }
       |}
      """.stripMargin.trim
  )
  addSourceFile("EEE.scala",
    s"""
       |object EEE {
       |  def withDefault(z: Int, s: String = "default") = {
       |    println("hello") //should step here
       |  }
       |}""".stripMargin.trim
  )

  def testWithDefaultParam(): Unit = {
    stepIntoTest()(
      Breakpoint("WithDefaultParam.scala", "main", 3) -> stepInto,
      Breakpoint("EEE.scala", "withDefault", 3) -> resume
    )
  }

  addSourceFile("TraitMethod.scala",
    s"""
       |object TraitMethod extends RRR{
       |  def main(args: Array[String]): Unit = {
       |    val x = foo(1) $breakpoint
       |  }
       |}
      """.stripMargin.trim
  )
  addSourceFile("RRR.scala",
    s"""
       |trait RRR {
       |  def foo(z: Int) = {
       |    println("hello") //should step here
       |  }
       |}""".stripMargin.trim
  )

  def testTraitMethod(): Unit = {
    stepIntoTest()(
      Breakpoint("TraitMethod.scala", "main", 3) -> stepInto,
      Breakpoint("RRR.scala", "foo", 3) -> resume
    )
  }

  addSourceFile("UnapplyMethod.scala",
    s"""
       |object UnapplyMethod {
       |  def main(args: Array[String]): Unit = {
       |    val z = Some(1)
       |    z match {
       |      case TTT(a) => $breakpoint
       |        TTT(a)
       |      case _ =>
       |    }
       |  }
       |}
      """.stripMargin.trim
  )
  addSourceFile("TTT.scala",
    s"""
       |object TTT {
       |  def unapply(z: Option[Int]) = z  //should step here
       |
       |  def apply(i: Int) = Some(i)
       |}""".stripMargin.trim
  )

  def testUnapplyMethod(): Unit = {
    stepIntoTest()(
      Breakpoint("UnapplyMethod.scala", "main", 5) -> stepInto,
      Breakpoint("TTT.scala", "unapply", 2) -> resume
    )
  }

  addSourceFile("ImplicitConversion.scala",
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
       |  def main(args: Array[String]): Unit = {
       |    val a = new A
       |    foo(a) $breakpoint
       |  }
       |}
      """.stripMargin.trim
  )

  def testImplicitConversion(): Unit = {
    stepIntoTest()(
      Breakpoint("ImplicitConversion.scala", "main", 13) -> stepInto,
      Breakpoint("ImplicitConversion.scala", "a2B", 7) -> resume
    )
  }

  addSourceFile("LazyVal.scala",
    s"""
       |object LazyVal {
       |  lazy val lzy = Some(1)  //should step here
       |
       |  def main(args: Array[String]): Unit = {
       |    val x = lzy $breakpoint
       |  }
       |}
      """.stripMargin.trim
  )

  def testLazyVal(): Unit = {
    stepIntoTest()(
      Breakpoint("LazyVal.scala", "main", 5) -> stepInto,
      Breakpoint("LazyVal.scala", "lzy$lzycompute", 2) -> resume
    )
  }

  addSourceFile("LazyVal2.scala",
    s"""
       |object LazyVal2 {
       |  lazy val lzy = new AAA
       |
       |  def main(args: Array[String]): Unit = {
       |    val x = lzy
       |    val y = lzy.foo $breakpoint
       |  }
       |
       |  class AAA {
       |    def foo: Int = 5 //should step here
       |  }
       |}
      """.stripMargin.trim
  )

  def testLazyVal2(): Unit = {
    stepIntoTest()(
      Breakpoint("LazyVal2.scala", "main", 6) -> stepInto,
      Breakpoint("LazyVal2.scala", "foo", 10) -> resume
    )
  }

  addSourceFile("SimpleGetters.scala",
    s"""object SimpleGetters {
       |  val z = 0
       |
       |  def main(args: Array[String]): Unit = {
       |    val x = new SimpleGetters
       |    x.getA $breakpoint
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
    stepIntoTest()(
      Breakpoint("SimpleGetters.scala", "main", 6) -> stepInto,
      Breakpoint("SimpleGetters.scala", "main", 7) -> stepInto,
      Breakpoint("SimpleGetters.scala", "sum", 10) -> { ctx =>
        resume(ctx)
        DebuggerSettings.getInstance().SKIP_GETTERS = false
      }
    )
  }

  addSourceFile("CustomizedPatternMatching.scala",
    s"""object CustomizedPatternMatching {
       |  def main(args: Array[String]): Unit = {
       |    val b = new B()
       |    foo(b) $breakpoint
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
    stepIntoTest()(
      Breakpoint("CustomizedPatternMatching.scala", "main", 4) -> stepInto,
      Breakpoint("CustomizedPatternMatching.scala", "foo", 8) -> stepInto,
      Breakpoint("CustomizedPatternMatching.scala", "b", 16) -> resume
    )
  }

  addSourceFile("PrivateMethodUsedInLambda.scala",
    s"""object PrivateMethodUsedInLambda {
       |  private def privateMethod(i: Int) = {
       |    println("hello!") //should step here
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    val s = for (x <- Seq(1, 2)) {
       |      privateMethod(x) $breakpoint
       |    }
       |  }
       |}""".stripMargin)

  def testPrivateMethodUsedInLambda(): Unit = {
    stepIntoTest()(
      Breakpoint("PrivateMethodUsedInLambda.scala", "$anonfun$main$1", 8) -> stepInto,
      Breakpoint("PrivateMethodUsedInLambda.scala", "privateMethod", 3) -> resume,
      Breakpoint("PrivateMethodUsedInLambda.scala", "$anonfun$main$1", 8) -> stepInto,
      Breakpoint("PrivateMethodUsedInLambda.scala", "privateMethod", 3) -> resume
    )
  }

  addSourceFile("Specialization.scala",
    s"""class FunctionA extends Function[Int, Int] {
       |  override def apply(v1: Int): Int = {
       |    println("stop")
       |    v1
       |  }
       |}
       |
       |class FunctionB extends Function[String, Int] {
       |  override def apply(v1: String): Int = {
       |    println("stop")
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
       |    a(1) $breakpoint
       |    b("2") $breakpoint
       |  }
       |}
      """.stripMargin)

  def testSpecialization(): Unit = {
    stepIntoTest()(
      Breakpoint("Specialization.scala", "main", 21) -> stepInto,
      Breakpoint("Specialization.scala", "apply$mcII$sp", 3) -> resume,
      Breakpoint("Specialization.scala", "main", 22) -> stepInto,
      Breakpoint("Specialization.scala", "apply", 10) -> resume
    )
  }

  addSourceFile("CustomTraitForwarder.scala",
    s"""|object CustomTraitForwarder {
        |
        |  def main(args: Array[String]): Unit = {
        |    val inh = new Inheritor
        |    inh.foo(1) $breakpoint
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
    stepIntoTest()(
      Breakpoint("CustomTraitForwarder.scala", "main", 5) -> stepInto,
      Breakpoint("CustomTraitForwarder.scala", "foo", 10) -> { ctx =>
        resume(ctx)
        debuggerSettings.SKIP_SYNTHETIC_METHODS = old
      }
    )
  }

  addSourceFile("TraitObjectSameName.scala",
    s"""object TraitObjectSameName {
       |  def main(args: Array[String]): Unit = {
       |    val t: T = T
       |    val builder = new StringBuilder
       |    t.parse(builder) $breakpoint
       |  }
       |}
       |object T extends T
       |trait T {
       |  def parse(b: StringBuilder): Boolean = {
       |    b.toString()
       |    println(42)
       |    true
       |  }
       |}""".stripMargin)

  def testTraitObjectSameName(): Unit = {
    stepIntoTest()(
      Breakpoint("TraitObjectSameName.scala", "main", 5) -> stepInto,
      Breakpoint("TraitObjectSameName.scala", "parse", 11) -> resume
    )
  }
}
