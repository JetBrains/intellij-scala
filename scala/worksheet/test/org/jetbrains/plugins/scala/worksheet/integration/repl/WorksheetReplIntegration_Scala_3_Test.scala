package org.jetbrains.plugins.scala.worksheet.integration.repl

import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.base.FailableTest
import org.jetbrains.plugins.scala.util.runners._
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetRuntimeExceptionsTests
import org.jetbrains.plugins.scala.worksheet.ui.printers.WorksheetEditorPrinterRepl
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.Assert._

import scala.language.postfixOps

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_Latest,
  TestScalaVersion.Scala_3_Latest_RC
))
class WorksheetReplIntegration_Scala_3_Latest_Test extends WorksheetReplIntegration_Since_3_2_TestBase
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_2
))
class WorksheetReplIntegration_Scala_3_2_Test extends WorksheetReplIntegration_Since_3_2_TestBase

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_0,
  TestScalaVersion.Scala_3_1,
))
class WorksheetReplIntegration_Before_Scala_3_2_Test extends WorksheetReplIntegration_Scala_3_BaseTest

abstract class WorksheetReplIntegration_Since_3_2_TestBase extends WorksheetReplIntegration_Scala_3_BaseTest with FailableTest {
  override def testAllInOne(): Unit = {
    val before =
      """import java.io.PrintStream
        |import scala.collection.Seq;
        |
        |println(Seq(1, 2, 3))
        |println(1)
        |
        |()
        |23
        |"str"
        |
        |def foo = "123" + 1
        |def foo0 = 1
        |def foo1() = 1
        |def foo2: Int = 1
        |def foo3(): Int = 1
        |def foo4(p: String) = 1
        |def foo5(p: String): Int = 1
        |def foo6(p: String, q: Short): Int = 1
        |def foo7[T] = 1
        |def foo8[T]() = 1
        |def foo9[T]: Int = 1
        |def foo10[T](): Int = 1
        |def foo11[T](p: String) = 1
        |def foo12[T](p: String): Int = 1
        |def foo13[T](p: String, q: Short): Int = 1
        |
        |val _ = 1
        |val x = 2
        |val y = x.toString + foo
        |val x2: PrintStream = null
        |
        |def f = 11
        |var _ = 5
        |var v1 = 6
        |var v2 = v1 + f
        |v2 = v1
        |
        |class A
        |trait B
        |object B
        |
        |enum ListEnum[+A] {
        |  case Cons(h: A, t: ListEnum[A])
        |  case Empty
        |}
        |
        |println(ListEnum.Empty)
        |println(ListEnum.Cons(42, ListEnum.Empty))""".stripMargin
    val after =
      """
        |
        |
        |List(1, 2, 3)
        |1
        |
        |
        |val res0: Int = 23
        |val res1: String = str
        |
        |def foo: String
        |def foo0: Int
        |def foo1(): Int
        |def foo2: Int
        |def foo3(): Int
        |def foo4(p: String): Int
        |def foo5(p: String): Int
        |def foo6(p: String, q: Short): Int
        |def foo7[T]: Int
        |def foo8[T](): Int
        |def foo9[T]: Int
        |def foo10[T](): Int
        |def foo11[T](p: String): Int
        |def foo12[T](p: String): Int
        |def foo13[T](p: String, q: Short): Int
        |
        |
        |val x: Int = 2
        |val y: String = 21231
        |val x2: java.io.PrintStream = null
        |
        |def f: Int
        |
        |var v1: Int = 6
        |var v2: Int = 17
        |v2: Int = 6
        |
        |// defined class A
        |// defined trait B
        |// defined object B
        |
        |// defined class ListEnum
        |
        |
        |
        |
        |Empty
        |Cons(42,Empty)""".stripMargin
    doRenderTest(before, after)
  }

  override def testScalaConcurrentDurationInstantiation(): Unit = {
    try {
      // TODO: This test passes when the `super` test fails. This is a platform bug in Scala 3.2.1.
      //       When the issue is fixed upstream, this test will fail and can be removed.
      //       https://github.com/lampepfl/dotty/issues/16322
      super.testScalaConcurrentDurationInstantiation()
      fail(failingPassed)
    } catch {
      case _: AssertionError =>
    }
  }

  def testIgnoreFatalWarningsCompilerOption(): Unit = {
    val worksheetText =
      """//warning since scala 2.13.11: Implicit definition should have explicit type (inferred String)
        |implicit def foo = "42"
        |""".stripMargin

    val editorAndFile = prepareWorksheetEditor(worksheetText, scratchFile = true)

    setAdditionalCompilerOptions(Seq("-Werror", "-Xfatal-warnings"))

    doRenderTestWithoutCompilationChecks(editorAndFile,
      s"""
         |$foldStart-- Error: ----------------------------------------------------------------------
         |2 |implicit def foo = "42"
         |  |             ^
         |  |           result type of implicit definition needs to be given explicitly
         |1 error found$foldEnd""".stripMargin
    )

    //TODO: in Scala 3 worksheets error reporting works different from Scala 2 due to some limitations
    // (see TODOs in org.jetbrains.jps.incremental.scala.local.worksheet.repl_interface.ILoopWrapper330Impl)
    // After that is fixed expected data should be adjusted
    //    doRenderTestWithoutCompilationChecks(editorAndFile,
    //      s"""
    //         |def foo: String""".stripMargin
    //    )
    //    assertCompilerMessages(editorAndFile.editor)(
    //      """Warning:(2, 14) Implicit definition should have explicit type (inferred String)
    //        |implicit def foo = "42"
    //        |""".stripMargin
    //    )
  }
}

@RunWithJdkVersions(Array(TestJdkVersion.JDK_1_8))
abstract class WorksheetReplIntegration_Scala_3_BaseTest extends WorksheetReplIntegrationBaseTest
  with WorksheetRuntimeExceptionsTests {

  override protected def supportedIn(version: ScalaVersion): Boolean = version > LatestScalaVersions.Scala_2_10

  def testAllInOne(): Unit = {
    val before =
      """import java.io.PrintStream
        |import scala.concurrent.duration._;
        |import scala.collection.Seq;
        |
        |println(Seq(1, 2, 3))
        |println(1)
        |
        |()
        |23
        |"str"
        |
        |def foo = "123" + 1
        |def foo0 = 1
        |def foo1() = 1
        |def foo2: Int = 1
        |def foo3(): Int = 1
        |def foo4(p: String) = 1
        |def foo5(p: String): Int = 1
        |def foo6(p: String, q: Short): Int = 1
        |def foo7[T] = 1
        |def foo8[T]() = 1
        |def foo9[T]: Int = 1
        |def foo10[T](): Int = 1
        |def foo11[T](p: String) = 1
        |def foo12[T](p: String): Int = 1
        |def foo13[T](p: String, q: Short): Int = 1
        |
        |val _ = 1
        |val x = 2
        |val y = x.toString + foo
        |val x2: PrintStream = null
        |val q1 = new DurationInt(3)
        |var q2 = new DurationInt(4)
        |
        |def f = 11
        |var _ = 5
        |var v1 = 6
        |var v2 = v1 + f
        |v2 = v1
        |
        |class A
        |trait B
        |object B
        |
        |enum ListEnum[+A] {
        |  case Cons(h: A, t: ListEnum[A])
        |  case Empty
        |}
        |
        |println(ListEnum.Empty)
        |println(ListEnum.Cons(42, ListEnum.Empty))""".stripMargin
    val after =
      """
        |
        |
        |
        |List(1, 2, 3)
        |1
        |
        |
        |val res0: Int = 23
        |val res1: String = str
        |
        |def foo: String
        |def foo0: Int
        |def foo1(): Int
        |def foo2: Int
        |def foo3(): Int
        |def foo4(p: String): Int
        |def foo5(p: String): Int
        |def foo6(p: String, q: Short): Int
        |def foo7[T] => Int
        |def foo8[T](): Int
        |def foo9[T] => Int
        |def foo10[T](): Int
        |def foo11[T](p: String): Int
        |def foo12[T](p: String): Int
        |def foo13[T](p: String, q: Short): Int
        |
        |
        |val x: Int = 2
        |val y: String = 21231
        |val x2: java.io.PrintStream = null
        |val q1: scala.concurrent.duration.package.DurationInt = 3
        |var q2: scala.concurrent.duration.package.DurationInt = 4
        |
        |def f: Int
        |
        |var v1: Int = 6
        |var v2: Int = 17
        |v2: Int = 6
        |
        |// defined class A
        |// defined trait B
        |// defined object B
        |
        |// defined class ListEnum
        |
        |
        |
        |
        |Empty
        |Cons(42,Empty)""".stripMargin
    doRenderTest(before, after)
  }

  def testBracelessSyntax(): Unit = {
    val before =
      """def foo42(x: Int) =
        |  val y = x + 1
        |  y + 1
        |
        |class A(x: Int):
        |  val a = x + 2
        |  def method =
        |    val b = a + 2
        |    b
        |
        |foo42(1)
        |
        |A(1).method
        |""".stripMargin
    val after =
      s"""def foo42(x: Int): Int
         |
         |
         |
         |// defined class A
         |
         |
         |
         |
         |
         |val res0: Int = 3
         |
         |val res1: Int = 5""".stripMargin
    doRenderTest(before, after)
  }

  def testScalaConcurrentDurationInstantiation(): Unit = {
    val before =
      """val n = scala.concurrent.duration.DurationInt(5)
        |""".stripMargin
    val after =
      s"""val n: scala.concurrent.duration.package.DurationInt = 5""".stripMargin
    doRenderTest(before, after)
  }

  def testSealedTraitHierarchy_1(): Unit = {
    val editor = doRenderTest(
      """sealed trait T""",
      """// defined trait T"""
    ).editor
    assertLastLine(editor, 0)
  }

  def testSealedTraitHierarchy_2(): Unit = {
    val editor = doRenderTest(
      """sealed trait T
        |case class A() extends T""".stripMargin,
      """// defined trait T
        |// defined case class A""".stripMargin
    ).editor
    assertLastLine(editor, 1)
  }

  def testSealedTraitHierarchy_3(): Unit = {
    val editor = doRenderTest(
      """sealed trait T
        |case class A() extends T
        |case class B() extends T""".stripMargin,
      """// defined trait T
        |// defined case class A
        |// defined case class B""".stripMargin
    ).editor
    assertLastLine(editor, 2)
  }

  def testSealedTraitHierarchy_WithSpacesAndComments(): Unit = {
    val editor = doRenderTest(
      """sealed trait T
        |case class A() extends T
        |case class B() extends T
        |
        |//
        |//
        |case class C() extends T
        |
        |
        |/**
        |  *
        |  */
        |case class D() extends T""".stripMargin,
      """// defined trait T
        |// defined case class A
        |// defined case class B
        |
        |
        |
        |// defined case class C
        |
        |
        |
        |
        |
        |// defined case class D""".stripMargin
    ).editor
    assertLastLine(editor, 12)
  }

  def testSealedTraitHierarchy_Several(): Unit = {
    val editor = doRenderTest(
      """sealed trait T1
        |
        |val x = 1
        |
        |sealed trait T2
        |case class A() extends T2
        |case class B() extends T2
        |
        |sealed trait T3
        |case class C() extends T3""".stripMargin,
      """// defined trait T1
        |
        |val x: Int = 1
        |
        |// defined trait T2
        |// defined case class A
        |// defined case class B
        |
        |// defined trait T3
        |// defined case class C""".stripMargin
    ).editor
    assertLastLine(editor, 9)
  }

  def testIgnoreWarnAboutUnusedImportsCompilerOption(): Unit = {
    val worksheetText =
      """import scala.util.Random
        |Random.nextInt().abs.min(0)""".stripMargin

    val editorAndFile = prepareWorksheetEditor(worksheetText, scratchFile = true)

    setAdditionalCompilerOptions(Seq("-Wunused:imports"))

    doRenderTestWithoutCompilationChecks(editorAndFile,
      s"""
         |val res0: Int = 0""".stripMargin
    )
    assertCompilerMessages(editorAndFile.editor)("")
  }

  private def assertLastLine(editor: Editor, line: Int): Unit = {
    val printer = worksheetCache.getPrinter(editor).get.asInstanceOf[WorksheetEditorPrinterRepl]
    assertEquals(
      "last processed line should point to last successfully evaluated line",
      Some(line), printer.lastProcessedLine
    )
  }
}
