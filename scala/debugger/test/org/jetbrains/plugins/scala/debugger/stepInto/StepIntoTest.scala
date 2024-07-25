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
      Breakpoint("PrivateMethodUsedInLambda.scala", "PrivateMethodUsedInLambda$$anonfun$1", "apply$mcVI$sp", 8) -> stepInto,
      Breakpoint("PrivateMethodUsedInLambda.scala", "PrivateMethodUsedInLambda$", "PrivateMethodUsedInLambda$$privateMethod", 3) -> resume,
      Breakpoint("PrivateMethodUsedInLambda.scala", "PrivateMethodUsedInLambda$$anonfun$1", "apply$mcVI$sp", 8) -> stepInto,
      Breakpoint("PrivateMethodUsedInLambda.scala", "PrivateMethodUsedInLambda$", "PrivateMethodUsedInLambda$$privateMethod", 3) -> resume
    )
  }

  override def testUnapplyMethod(): Unit = {
    stepIntoTest()(
      Breakpoint("UnapplyMethod.scala", "UnapplyMethod$", "main", 5) -> stepInto,
      Breakpoint("TTT.scala", "TTT$", "unapply", 2) -> resume,
      Breakpoint("UnapplyMethod.scala", "UnapplyMethod$", "main", 5) -> resume
    )
  }

  override def testTraitObjectSameName(): Unit = {
    stepIntoTest()(
      Breakpoint("TraitObjectSameName.scala", "TraitObjectSameName$", "main", 5) -> stepInto,
      Breakpoint("TraitObjectSameName.scala", "T$class", "parse", 11) -> resume
    )
  }

  override def testTraitMethod(): Unit = {
    stepIntoTest()(
      Breakpoint("TraitMethod.scala", "TraitMethod$", "main", 3) -> stepInto,
      Breakpoint("RRR.scala", "RRR$class", "foo", 3) -> resume
    )
  }

  override def testLambdaStepIntoInnerLambda(): Unit = {
    stepIntoTest()(
      Breakpoint("LambdaStepIntoInnerLambda.scala", "LambdaStepIntoInnerLambda$$anonfun$1", "apply", 4) -> stepInto,
      Breakpoint("LambdaStepIntoInnerLambda.scala", "LambdaStepIntoInnerLambda$$anonfun$1$$anonfun$apply$1", "apply$mcDD$sp", 4) -> resume,
      Breakpoint("LambdaStepIntoInnerLambda.scala", "LambdaStepIntoInnerLambda$$anonfun$1", "apply", 4) -> stepInto,
      Breakpoint("LambdaStepIntoInnerLambda.scala", "LambdaStepIntoInnerLambda$$anonfun$1$$anonfun$apply$1", "apply$mcDD$sp", 4) -> resume,
      Breakpoint("LambdaStepIntoInnerLambda.scala", "LambdaStepIntoInnerLambda$$anonfun$1", "apply", 4) -> stepInto,
      Breakpoint("LambdaStepIntoInnerLambda.scala", "LambdaStepIntoInnerLambda$$anonfun$1$$anonfun$apply$1", "apply$mcDD$sp", 4) -> resume
    )
  }

  override def testLambdaStepIntoLambdaStepOutStepIntoLambda(): Unit = {
    stepIntoTest()(
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$$anonfun$1", "apply", 4) -> stepInto,
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$$anonfun$1$$anonfun$apply$1", "apply$mcDD$sp", 4) -> stepOut,
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$$anonfun$1", "apply", 4) -> stepInto,
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$$anonfun$1$$anonfun$apply$2", "apply$mcDD$sp", 4) -> resume,
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$$anonfun$1", "apply", 4) -> stepInto,
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$$anonfun$1$$anonfun$apply$1", "apply$mcDD$sp", 4) -> stepOut,
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$$anonfun$1", "apply", 4) -> stepInto,
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$$anonfun$1$$anonfun$apply$2", "apply$mcDD$sp", 4) -> resume,
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$$anonfun$1", "apply", 4) -> stepInto,
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$$anonfun$1$$anonfun$apply$1", "apply$mcDD$sp", 4) -> stepOut,
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$$anonfun$1", "apply", 4) -> stepInto,
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$$anonfun$1$$anonfun$apply$2", "apply$mcDD$sp", 4) -> resume
    )
  }

  override def testLineStepIntoLambda(): Unit = {
    stepIntoTest()(
      Breakpoint("LineStepIntoLambda.scala", "LineStepIntoLambda$", "<init>", 7) -> stepInto,
      Breakpoint("LineStepIntoLambda.scala", "LineStepIntoLambda$$anonfun$1", "apply$mcDI$sp", 7) -> resume,
      Breakpoint("LineStepIntoLambda.scala", "LineStepIntoLambda$", "fahrenheitDegrees", 7) -> resume
    )
  }

  override def testLineStepIntoLambdaStepOutStepIntoLambda(): Unit = {
    stepIntoTest()(
      Breakpoint("LineStepIntoLambdaStepOutStepIntoLambda.scala", "LineStepIntoLambdaStepOutStepIntoLambda$", "main", 3) -> stepInto,
      Breakpoint("LineStepIntoLambdaStepOutStepIntoLambda.scala", "LineStepIntoLambdaStepOutStepIntoLambda$$anonfun$1", "apply$mcDD$sp", 3) -> stepOut,
      Breakpoint("LineStepIntoLambdaStepOutStepIntoLambda.scala", "LineStepIntoLambdaStepOutStepIntoLambda$", "main", 3) -> stepInto,
      Breakpoint("LineStepIntoLambdaStepOutStepIntoLambda.scala", "LineStepIntoLambdaStepOutStepIntoLambda$$anonfun$2", "apply$mcDD$sp", 3) -> stepOut,
      Breakpoint("LineStepIntoLambdaStepOutStepIntoLambda.scala", "LineStepIntoLambdaStepOutStepIntoLambda$", "main", 3) -> resume
    )
  }

  override def testLambdaStepIntoMethod(): Unit = {
    stepIntoTest()(
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$$anonfun$1", "apply$mcDI$sp", 7) -> stepInto,
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$", "multi", 2) -> stepOut,
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$$anonfun$1", "apply$mcDI$sp", 7) -> stepInto,
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$", "sum", 3) -> resume,
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$$anonfun$1", "apply$mcDI$sp", 7) -> stepInto,
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$", "multi", 2) -> stepOut,
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$$anonfun$1", "apply$mcDI$sp", 7) -> stepInto,
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$", "sum", 3) -> resume,
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$$anonfun$1", "apply$mcDI$sp", 7) -> stepInto,
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$", "multi", 2) -> stepOut,
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$$anonfun$1", "apply$mcDI$sp", 7) -> stepInto,
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$", "sum", 3) -> resume
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
      Breakpoint("SamAbstractClass.scala", "SamAbstractClass$", "main", 8) -> stepInto,
      Breakpoint("SamAbstractClass.scala", "SamAbstractClass$", "SamAbstractClass$$$anonfun$main$1", 4) -> resume
    )
  }
}

class StepIntoTest_2_13 extends StepIntoTest_2_12 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

  override def testLineStepIntoLambda(): Unit = {
    stepIntoTest()(
      Breakpoint("LineStepIntoLambda.scala", "LineStepIntoLambda$", "<clinit>", 7) -> stepInto,
      Breakpoint("LineStepIntoLambda.scala", "LineStepIntoLambda$", "$anonfun$fahrenheitDegrees$1", 7) -> resume,
      Breakpoint("LineStepIntoLambda.scala", "LineStepIntoLambda$", "fahrenheitDegrees", 7) -> resume
    )
  }
}

class StepIntoTest_3 extends StepIntoTest_2_13 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  override def testSamAbstractClass(): Unit = {
    stepIntoTest()(
      Breakpoint("SamAbstractClass.scala", "SamAbstractClass$", "main", 8) -> stepInto,
      Breakpoint("SamAbstractClass.scala", "SamAbstractClass$", "SamAbstractClass$$$_$_$$anonfun$1", 4) -> resume
    )
  }

  override def testLazyVal(): Unit = {
    stepIntoTest()(
      Breakpoint("LazyVal.scala", "LazyVal$", "main", 5) -> stepInto,
      Breakpoint("LazyVal.scala", "LazyVal$", "main", 6) -> resume
    )
  }

  override def testPrivateMethodUsedInLambda(): Unit = {
    stepIntoTest()(
      Breakpoint("PrivateMethodUsedInLambda.scala", "PrivateMethodUsedInLambda$", "$anonfun$1", 8) -> stepInto,
      Breakpoint("PrivateMethodUsedInLambda.scala", "PrivateMethodUsedInLambda$", "privateMethod", 3) -> resume,
      Breakpoint("PrivateMethodUsedInLambda.scala", "PrivateMethodUsedInLambda$", "$anonfun$1", 8) -> stepInto,
      Breakpoint("PrivateMethodUsedInLambda.scala", "PrivateMethodUsedInLambda$", "privateMethod", 3) -> resume
    )
  }

  override def testLambdaStepIntoInnerLambda(): Unit = {
    stepIntoTest()(
      Breakpoint("LambdaStepIntoInnerLambda.scala", "LambdaStepIntoInnerLambda$", "$anonfun$1", 4) -> stepInto,
      Breakpoint("LambdaStepIntoInnerLambda.scala", "LambdaStepIntoInnerLambda$", "$anonfun$1$$anonfun$1", 4) -> resume,
      Breakpoint("LambdaStepIntoInnerLambda.scala", "LambdaStepIntoInnerLambda$", "$anonfun$1", 4) -> stepInto,
      Breakpoint("LambdaStepIntoInnerLambda.scala", "LambdaStepIntoInnerLambda$", "$anonfun$1$$anonfun$1", 4) -> resume,
      Breakpoint("LambdaStepIntoInnerLambda.scala", "LambdaStepIntoInnerLambda$", "$anonfun$1", 4) -> stepInto,
      Breakpoint("LambdaStepIntoInnerLambda.scala", "LambdaStepIntoInnerLambda$", "$anonfun$1$$anonfun$1", 4) -> resume
    )
  }

  override def testLambdaStepIntoLambdaStepOutStepIntoLambda(): Unit = {
    stepIntoTest()(
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$", "$anonfun$1", 4) -> stepInto,
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$", "$anonfun$1$$anonfun$1", 4) -> stepOut,
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$", "$anonfun$1", 4) -> stepInto,
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$", "$anonfun$1$$anonfun$2", 4) -> resume,
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$", "$anonfun$1", 4) -> stepInto,
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$", "$anonfun$1$$anonfun$1", 4) -> stepOut,
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$", "$anonfun$1", 4) -> stepInto,
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$", "$anonfun$1$$anonfun$2", 4) -> resume,
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$", "$anonfun$1", 4) -> stepInto,
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$", "$anonfun$1$$anonfun$1", 4) -> stepOut,
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$", "$anonfun$1", 4) -> stepInto,
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$", "$anonfun$1$$anonfun$2", 4) -> resume
    )
  }

  override def testLineStepIntoLambda(): Unit = {
    stepIntoTest()(
      Breakpoint("LineStepIntoLambda.scala", "LineStepIntoLambda$", "<clinit>", 7) -> stepInto,
      Breakpoint("LineStepIntoLambda.scala", "LineStepIntoLambda$", "$init$$$anonfun$1", 7) -> resume,
      Breakpoint("LineStepIntoLambda.scala", "LineStepIntoLambda$", "fahrenheitDegrees", 7) -> resume
    )
  }

  override def testLineStepIntoLambdaStepOutStepIntoLambda(): Unit = {
    stepIntoTest()(
      Breakpoint("LineStepIntoLambdaStepOutStepIntoLambda.scala", "LineStepIntoLambdaStepOutStepIntoLambda$", "main", 3) -> stepInto,
      Breakpoint("LineStepIntoLambdaStepOutStepIntoLambda.scala", "LineStepIntoLambdaStepOutStepIntoLambda$", "$anonfun$1", 3) -> stepOut,
      Breakpoint("LineStepIntoLambdaStepOutStepIntoLambda.scala", "LineStepIntoLambdaStepOutStepIntoLambda$", "main", 3) -> stepInto,
      Breakpoint("LineStepIntoLambdaStepOutStepIntoLambda.scala", "LineStepIntoLambdaStepOutStepIntoLambda$", "$anonfun$2", 3) -> stepOut,
      Breakpoint("LineStepIntoLambdaStepOutStepIntoLambda.scala", "LineStepIntoLambdaStepOutStepIntoLambda$", "main", 3) -> resume
    )
  }

  override def testLambdaStepIntoMethod(): Unit = {
    stepIntoTest()(
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$", "$anonfun$1", 7) -> stepInto,
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$", "multi", 2) -> stepOut,
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$", "$anonfun$1", 7) -> stepInto,
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$", "sum", 3) -> resume,
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$", "$anonfun$1", 7) -> stepInto,
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$", "multi", 2) -> stepOut,
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$", "$anonfun$1", 7) -> stepInto,
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$", "sum", 3) -> resume,
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$", "$anonfun$1", 7) -> stepInto,
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$", "multi", 2) -> stepOut,
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$", "$anonfun$1", 7) -> stepInto,
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$", "sum", 3) -> resume
    )
  }

}

class StepIntoTest_3_RC extends StepIntoTest_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_RC

  // TODO: Revisit lazy vals in Scala 3.4+
  override def testLazyVal(): Unit = {}
}

class StepIntoTest_3_Next_RC extends StepIntoTest_3_RC {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_Next_RC
}

abstract class StepIntoTestBase extends ScalaDebuggerTestCase {

  protected case class Breakpoint(file: String, declaringType: String, method: String, line: Int)

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
      val actual = Breakpoint(loc.sourceName(), loc.method().declaringType().name(), loc.method().name(), srcPos.getLine + 1)
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
      Breakpoint("Simple.scala", "Simple$", "main", 3) -> stepInto,
      Breakpoint("Simple.scala", "AAA$", "foo", 9) -> resume
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
      Breakpoint("Constructor.scala", "Constructor$", "main", 3) -> stepInto,
      Breakpoint("ZZZ.scala", "ZZZ", "<init>", 1) -> resume
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
      Breakpoint("MyApplyMethod.scala", "MyApplyMethod$", "main", 3) -> stepInto,
      Breakpoint("QQQ.scala", "QQQ$", "apply", 9) -> resume
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
      Breakpoint("IntoPackageObject.scala", "test.IntoPackageObject$", "main", 5) -> stepInto,
      Breakpoint("package.scala", "test.package$", "foo", 3) -> resume
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
      Breakpoint("FromPackageObject.scala", "test1.FromPackageObject$", "main", 5) -> stepInto,
      Breakpoint("package.scala", "test1.package$", "foo", 3) -> stepInto,
      Breakpoint("FromPackageObject.scala", "test1.FromPackageObject$", "bar", 9) -> resume
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
      Breakpoint("WithDefaultParam.scala", "WithDefaultParam$", "main", 3) -> stepInto,
      Breakpoint("EEE.scala", "EEE$", "withDefault", 3) -> resume
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
      Breakpoint("TraitMethod.scala", "TraitMethod$", "main", 3) -> stepInto,
      Breakpoint("RRR.scala", "RRR", "foo", 3) -> resume
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
      Breakpoint("UnapplyMethod.scala", "UnapplyMethod$", "main", 5) -> stepInto,
      Breakpoint("TTT.scala", "TTT$", "unapply", 2) -> resume
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
      Breakpoint("ImplicitConversion.scala", "ImplicitConversion$", "main", 13) -> stepInto,
      Breakpoint("ImplicitConversion.scala", "ImplicitConversion$", "a2B", 7) -> resume
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
      Breakpoint("LazyVal.scala", "LazyVal$", "main", 5) -> stepInto,
      Breakpoint("LazyVal.scala", "LazyVal$", "lzy$lzycompute", 2) -> resume
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
      Breakpoint("LazyVal2.scala", "LazyVal2$", "main", 6) -> stepInto,
      Breakpoint("LazyVal2.scala", "LazyVal2$AAA", "foo", 10) -> resume
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
      Breakpoint("SimpleGetters.scala", "SimpleGetters$", "main", 6) -> stepInto,
      Breakpoint("SimpleGetters.scala", "SimpleGetters$", "main", 7) -> stepInto,
      Breakpoint("SimpleGetters.scala", "SimpleGetters$", "sum", 10) -> { ctx =>
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
      Breakpoint("CustomizedPatternMatching.scala", "CustomizedPatternMatching$", "main", 4) -> stepInto,
      Breakpoint("CustomizedPatternMatching.scala", "CustomizedPatternMatching$", "foo", 8) -> stepInto,
      Breakpoint("CustomizedPatternMatching.scala", "CustomizedPatternMatching$B", "b", 16) -> resume
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
      Breakpoint("PrivateMethodUsedInLambda.scala", "PrivateMethodUsedInLambda$", "$anonfun$main$1", 8) -> stepInto,
      Breakpoint("PrivateMethodUsedInLambda.scala", "PrivateMethodUsedInLambda$", "privateMethod", 3) -> resume,
      Breakpoint("PrivateMethodUsedInLambda.scala", "PrivateMethodUsedInLambda$", "$anonfun$main$1", 8) -> stepInto,
      Breakpoint("PrivateMethodUsedInLambda.scala", "PrivateMethodUsedInLambda$", "privateMethod", 3) -> resume
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
      Breakpoint("Specialization.scala", "Specialization$", "main", 21) -> stepInto,
      Breakpoint("Specialization.scala", "FunctionA", "apply$mcII$sp", 3) -> resume,
      Breakpoint("Specialization.scala", "Specialization$", "main", 22) -> stepInto,
      Breakpoint("Specialization.scala", "FunctionB", "apply", 10) -> resume
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
      Breakpoint("CustomTraitForwarder.scala", "CustomTraitForwarder$", "main", 5) -> stepInto,
      Breakpoint("CustomTraitForwarder.scala", "Inheritor", "foo", 10) -> { ctx =>
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
      Breakpoint("TraitObjectSameName.scala", "TraitObjectSameName$", "main", 5) -> stepInto,
      Breakpoint("TraitObjectSameName.scala", "T", "parse", 11) -> resume
    )
  }

  addSourceFile("LambdaStepIntoInnerLambda.scala",
    s"""object LambdaStepIntoInnerLambda {
       |  def main(args: Array[String]): Unit = {
       |    val celsiusDegrees = List(0, 20, 100)
       |    val fahrenheitDegrees = celsiusDegrees.flatMap(i => Option(i * 1.8 + 32).map(j => (j - 32) / 1.8)) $breakpoint ${lambdaOrdinal(0)}
       |    println(fahrenheitDegrees)
       |  }
       |}
       |
       |""".stripMargin)

  def testLambdaStepIntoInnerLambda(): Unit = {
    stepIntoTest()(
      Breakpoint("LambdaStepIntoInnerLambda.scala", "LambdaStepIntoInnerLambda$", "$anonfun$main$1", 4) -> stepInto,
      Breakpoint("LambdaStepIntoInnerLambda.scala", "LambdaStepIntoInnerLambda$", "$anonfun$main$2", 4) -> resume,
      Breakpoint("LambdaStepIntoInnerLambda.scala", "LambdaStepIntoInnerLambda$", "$anonfun$main$1", 4) -> stepInto,
      Breakpoint("LambdaStepIntoInnerLambda.scala", "LambdaStepIntoInnerLambda$", "$anonfun$main$2", 4) -> resume,
      Breakpoint("LambdaStepIntoInnerLambda.scala", "LambdaStepIntoInnerLambda$", "$anonfun$main$1", 4) -> stepInto,
      Breakpoint("LambdaStepIntoInnerLambda.scala", "LambdaStepIntoInnerLambda$", "$anonfun$main$2", 4) -> resume
    )
  }

  addSourceFile("LambdaStepIntoLambdaStepOutStepIntoLambda.scala",
    s"""object LambdaStepIntoLambdaStepOutStepIntoLambda {
       |  def main(args: Array[String]): Unit = {
       |    val celsiusDegrees = List(0, 20, 100)
       |    val fahrenheitDegrees = celsiusDegrees.flatMap(i => Option(i * 1.8 + 32).map(j => (j - 32) / 1.8).map(x => x)) $breakpoint ${lambdaOrdinal(0)}
       |    println(fahrenheitDegrees)
       |  }
       |}
       |
       |""".stripMargin)

  def testLambdaStepIntoLambdaStepOutStepIntoLambda(): Unit = {
    stepIntoTest()(
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$", "$anonfun$main$1", 4) -> stepInto,
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$", "$anonfun$main$2", 4) -> stepOut,
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$", "$anonfun$main$1", 4) -> stepInto,
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$", "$anonfun$main$3", 4) -> resume,
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$", "$anonfun$main$1", 4) -> stepInto,
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$", "$anonfun$main$2", 4) -> stepOut,
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$", "$anonfun$main$1", 4) -> stepInto,
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$", "$anonfun$main$3", 4) -> resume,
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$", "$anonfun$main$1", 4) -> stepInto,
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$", "$anonfun$main$2", 4) -> stepOut,
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$", "$anonfun$main$1", 4) -> stepInto,
      Breakpoint("LambdaStepIntoLambdaStepOutStepIntoLambda.scala", "LambdaStepIntoLambdaStepOutStepIntoLambda$", "$anonfun$main$3", 4) -> resume
    )
  }

  addSourceFile("LineStepIntoLambda.scala",
    s"""object LineStepIntoLambda {
       |  def multi(x: Double,y :Double): Double = x * y
       |
       |  def sum(x: Double, y: Double): Double = x + y
       |
       |  val celsiusDegrees = List(0, 20, 100)
       |  val fahrenheitDegrees = celsiusDegrees.map(i => sum(multi(i, 1.8), 32)) $breakpoint ${lambdaOrdinal(-1)}
       |
       |  def main(args: Array[String]): Unit = {
       |    println(fahrenheitDegrees)
       |  }
       |}
       |""".stripMargin)

  def testLineStepIntoLambda(): Unit = {
    stepIntoTest()(
      Breakpoint("LineStepIntoLambda.scala", "LineStepIntoLambda$", "<init>", 7) -> stepInto,
      Breakpoint("LineStepIntoLambda.scala", "LineStepIntoLambda$", "$anonfun$fahrenheitDegrees$1", 7) -> resume,
      Breakpoint("LineStepIntoLambda.scala", "LineStepIntoLambda$", "fahrenheitDegrees", 7) -> resume
    )
  }

  addSourceFile("LineStepIntoLambdaStepOutStepIntoLambda.scala",
    s"""object LineStepIntoLambdaStepOutStepIntoLambda {
       |  def main(args: Array[String]): Unit = {
       |    val fahrenheitDegrees = Option(20 * 1.8 + 32).map(x => x).map(y => y) $breakpoint ${lambdaOrdinal(-1)}
       |    println(fahrenheitDegrees)
       |  }
       |}
       |""".stripMargin)

  def testLineStepIntoLambdaStepOutStepIntoLambda(): Unit = {
    stepIntoTest()(
      Breakpoint("LineStepIntoLambdaStepOutStepIntoLambda.scala", "LineStepIntoLambdaStepOutStepIntoLambda$", "main", 3) -> stepInto,
      Breakpoint("LineStepIntoLambdaStepOutStepIntoLambda.scala", "LineStepIntoLambdaStepOutStepIntoLambda$", "$anonfun$main$1", 3) -> stepOut,
      Breakpoint("LineStepIntoLambdaStepOutStepIntoLambda.scala", "LineStepIntoLambdaStepOutStepIntoLambda$", "main", 3) -> stepInto,
      Breakpoint("LineStepIntoLambdaStepOutStepIntoLambda.scala", "LineStepIntoLambdaStepOutStepIntoLambda$", "$anonfun$main$2", 3) -> stepOut,
      Breakpoint("LineStepIntoLambdaStepOutStepIntoLambda.scala", "LineStepIntoLambdaStepOutStepIntoLambda$", "main", 3) -> resume
    )
  }

  addSourceFile("LambdaStepIntoMethod.scala",
    s"""object LambdaStepIntoMethod {
       |  def multi(x: Double, y: Double): Double = x * y
       |  def sum(x: Double, y: Double): Double = x + y
       |
       |  def main(args: Array[String]): Unit = {
       |    val celsiusDegrees = List(0, 20, 100)
       |    val fahrenheitDegrees = celsiusDegrees.map(i => sum(multi(i, 1.8), 32)) $breakpoint ${lambdaOrdinal(0)}
       |    println(fahrenheitDegrees)
       |  }
       |}
       |""".stripMargin)

  def testLambdaStepIntoMethod(): Unit = {
    stepIntoTest()(
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$", "$anonfun$main$1", 7) -> stepInto,
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$", "multi", 2) -> stepOut,
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$", "$anonfun$main$1", 7) -> stepInto,
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$", "sum", 3) -> resume,
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$", "$anonfun$main$1", 7) -> stepInto,
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$", "multi", 2) -> stepOut,
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$", "$anonfun$main$1", 7) -> stepInto,
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$", "sum", 3) -> resume,
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$", "$anonfun$main$1", 7) -> stepInto,
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$", "multi", 2) -> stepOut,
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$", "$anonfun$main$1", 7) -> stepInto,
      Breakpoint("LambdaStepIntoMethod.scala", "LambdaStepIntoMethod$", "sum", 3) -> resume
    )
  }
}
