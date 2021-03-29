package org.jetbrains.plugins.scala.worksheet.integration.plain

import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.WorksheetEvaluationTests
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.util.assertions.StringAssertions.assertStringMatches
import org.jetbrains.plugins.scala.util.runners._
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction.RunWorksheetActionResult
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction.RunWorksheetActionResult.WorksheetRunError
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetIntegrationBaseTest.{Folding, ViewerEditorData}
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetRuntimeExceptionsTests.Folded
import org.jetbrains.plugins.scala.worksheet.integration.util.{EditorRobot, MyUiUtils}
import org.jetbrains.plugins.scala.worksheet.integration.{WorksheetIntegrationBaseTest, WorksheetRunTestSettings, WorksheetRuntimeExceptionsTests}
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.WorksheetCompilerResult
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetCache
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetExternalRunType
import org.jetbrains.plugins.scala.worksheet.settings.persistent.WorksheetFilePersistentSettings
import org.jetbrains.plugins.scala.worksheet.ui.printers.WorksheetEditorPrinterPlain.{FoldingDataForTests, ViewerEditorState}
import org.jetbrains.plugins.scala.worksheet.ui.printers.{WorksheetEditorPrinterFactory, WorksheetEditorPrinterPlain}
import org.junit.Assert._
import org.junit.ComparisonFailure
import org.junit.experimental.categories.Category

import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}
import scala.language.postfixOps

//noinspection RedundantBlock
@Category(Array(classOf[WorksheetEvaluationTests]))
abstract class WorksheetPlainIntegrationBaseTest extends WorksheetIntegrationBaseTest
  with WorksheetRunTestSettings
  with WorksheetRuntimeExceptionsTests
  with WorksheetPlainCheckRuntimeVersionScalaTests {

  override def runType: WorksheetExternalRunType = WorksheetExternalRunType.PlainRunType

  // with some health check runs
  @RunWithScalaVersions(extra = Array(
    TestScalaVersion.Scala_2_11_0,
    TestScalaVersion.Scala_2_12_0,
    TestScalaVersion.Scala_2_13_0,
  ))
  def testSimple_1(): Unit = {
    val left =
      """val a = 1
        |val b = 2
        |""".stripMargin

    val right =
      """a: Int = 1
        |b: Int = 2""".stripMargin

    doRenderTest(left, right)
  }

  def testSimple_2(): Unit = {
    val left =
      """val s = "Boo"
        |var b = 2
        |
        |class A {
        |  def foo = 1
        |}
        |
        |b = new A().foo
        |""".stripMargin

    val right =
      """s: String = Boo
        |b: Int = 2
        |
        |defined class A
        |
        |
        |
        |b: Int = 1""".stripMargin

    doRenderTest(left, right)
  }

  def testTemplateDeclarations(): Unit = doRenderTest(
    """trait A {
      |}
      |trait B
      |abstract class C extends A
      |case class D(i: Int, s: String) extends C with B
      |object E extends B
      |
      |sealed trait Parent
      |case class Child1() extends Parent
      |case class Child2() extends Parent
      |object Child3 extends Parent
      |""".stripMargin,
    """defined trait A
      |
      |defined trait B
      |defined class C
      |defined class D
      |defined object E
      |
      |defined trait Parent
      |defined class Child1
      |defined class Child2
      |defined object Child3""".stripMargin
  )

  def testTypeAlias(): Unit = doRenderTest(
    """class A[T] {
      |  def foo(t: T): T = t
      |}
      |
      |type B = A[String]
      |""".stripMargin,
    """defined class A
      |
      |
      |
      |defined type alias B""".stripMargin
  )

  def testSimpleFolding(): Unit = {
    val left =
      """println("1\n2\n3")
        |val x = 42
        |""".stripMargin

    val right =
      s"""${foldStart}1
         |2
         |3
         |res0: Unit = ()$foldEnd
         |x: Int = 42""".stripMargin

    doRenderTest(left, right)
  }

  def testMultipleFoldings(): Unit = {
    val left =
      """println("1\n2\n3")
        |val x = 42
        |println("4\n5\n6")
        |val y = 23
        |
        |val c = true
        |
        |if (c) {
        |  for (_ <- 1 to 10) println("boo!")
        |}
        |
        |val a = 123
        |
        |a match {
        |  case 1 =>
        |  case _ =>
        |}
        |""".stripMargin

    val right =
      s"""${foldStart}1
         |2
         |3
         |res0: Unit = ()$foldEnd
         |x: Int = 42
         |${foldStart}4
         |5
         |6
         |res1: Unit = ()$foldEnd
         |y: Int = 23
         |
         |c: Boolean = true
         |
         |boo!
         |boo!
         |${foldStart}boo!
         |boo!
         |boo!
         |boo!
         |boo!
         |boo!
         |boo!
         |boo!
         |res2: Unit = ()$foldEnd
         |
         |a: Int = 123
         |
         |res3: Unit = ()
         |
         |
         |""".stripMargin

    doRenderTest(left, right)
  }

  def testFunctions(): Unit = doRenderTestWithoutCompilationWarningsChecks(
    """def foo() = 123
      |
      |def boo(i: Int) {
      |  for (_ <- 1 to i) println("boo!")
      |}
      |
      |def bar(s: String): Unit = println(s)
      |
      |def concat(s1: String, s2: String, s3: String) = s1 + s2 + s3
      |
      |val a: Int = foo()
      |boo(a)
      |bar("boo")
      |val s: String = concat("b", "o", "o")
      |""".stripMargin,
    s"""foo: foo[]() => Int
       |
       |boo: boo[](val i: Int) => Unit
       |
       |
       |
       |bar: bar[](val s: String) => Unit
       |
       |concat: concat[](val s1: String,val s2: String,val s3: String) => String
       |
       |a: Int = 123
       |${foldStart}boo!
       |boo!
       |boo!
       |boo!
       |boo!
       |boo!
       |boo!
       |boo!
       |boo!
       |boo!
       |boo!
       |boo!
       |boo!
       |boo!
       |boo!
       |Output exceeds cutoff limit.$foldEnd
       |${foldStart}boo
       |res1: Unit = ()$foldEnd
       |s: String = boo""".stripMargin
  )

  def doRenderTestWithoutCompilationWarningsChecks(): Unit = doRenderTest(
    """import java.util._
      |import java.lang.Math
      |
      |class A {
      |  import scala.collection.mutable._
      |
      |  def foo = HashMap[String, String]()
      |}
      |
      |def bar() {
      |  import java.io.File
      |  val f = new File("")
      |}
      |""".stripMargin,
    """import java.util._
      |import java.lang.Math
      |
      |defined class A
      |
      |
      |
      |
      |
      |bar: bar[]() => Unit
      |
      |
      |""".stripMargin
  )

  def testDisplayFirstRuntimeException(): Unit = {
    val left =
      """println("1\n2")
        |
        |println(1 / 0)
        |
        |println(2 / 0)
        |""".stripMargin

    val right  =
      s"""${foldStart}1
         |2
         |res0: Unit = ()$foldEnd
         |
         |""".stripMargin

    val exceptionOutputAssert: String => Unit = text => {
      val stackTraceDepthLimit = WorksheetEditorPrinterFactory.BULK_COUNT
      assertStringMatches(
        text,
        ("\\Qjava.lang.ArithmeticException: / by zero\\E" +
          s"(\n\tat [^\n]*){1,$stackTraceDepthLimit}" +
          s"(\nOutput exceeds cutoff limit\\.)?").r
      )
    }


    val editor = testDisplayFirstRuntimeException(left, right, Folded(expanded = false), exceptionOutputAssert)
    // run again with same editor, the output should be the same between these runs
    testDisplayFirstRuntimeException(editor, right, Folded(expanded = false), exceptionOutputAssert)
  }

  def testCompilationError(): Unit = {
    val before =
      """val x = new A()
        |""".stripMargin

    val editor = doFailingTest(before, WorksheetRunError(WorksheetCompilerResult.CompilationError))

    // TODO: fix SCL-16497
    return
    assertCompilerMessages(editor)(
      """Error:(1, 13) not found: type A
        |val x = new A()
        |""".stripMargin.trim
    )
  }

  def testCompilationError_ContentLines(): Unit = {
    val before =
      """
        |
        |  val x = new A()
        |""".stripMargin

    val editor = doFailingTest(before, WorksheetRunError(WorksheetCompilerResult.CompilationError))

    // TODO: fix SCL-16497
    return
    assertCompilerMessages(editor)(
      """Error:(3, 15) not found: type A
        |val x = new A()
        |""".stripMargin.trim
    )
  }

  def testCompilationError_ContentIndentedInInnerScope(): Unit = {
    val before =
      """
        |class Outer {
        |  def foo = {
        |
        |    val x = new A()
        |  }
        |}
        |""".stripMargin

    val editor = doFailingTest(before, WorksheetRunError(WorksheetCompilerResult.CompilationError))

    // TODO: fix SCL-16497
    return
    assertCompilerMessages(editor)(
      """Error:(5, 17) not found: type A
        |val x = new A()
        |""".stripMargin.trim
    )
  }

  def testArrayRender(): Unit = {
    doRenderTest(
      """var a1 = new Array[Int](3)
        |val a2 = Array(1, 2, 3)""".stripMargin,
      """a1: Array[Int] = Array(0, 0, 0)
        |a2: Array[Int] = Array(1, 2, 3)""".stripMargin
    )
  }

  def testInteractive(): Unit = {
    val editor = doRenderTest(
      """42""",
      """res0: Int = 42""".stripMargin
    )
    val viewer = WorksheetCache.getInstance(project).getViewer(editor)
    val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument)
    WorksheetFilePersistentSettings(file.getVirtualFile).setInteractive(true)

    val robot = new EditorRobot(editor)
    robot.moveToEnd()
    robot.typeString("\n23\n")

    // TODO: this is not the best way of testing, cause it relies on lucky threading conditions,
    //  but current architecture doesn't allow us do it some other way, think how this can be improved
    val stamp = viewer.getDocument.getModificationStamp
    MyUiUtils.waitConditioned(5 seconds) { () =>
      viewer.getDocument.getModificationStamp != stamp
    }

    assertViewerEditorText(editor,
      """res0: Int = 42
        |res1: Int = 23""".stripMargin
    )
    assertNoErrorMessages(editor)
  }

  def testInteractive_WithError(): Unit = {
    val editor = doRenderTest(
      """42""",
      """res0: Int = 42""".stripMargin
    )
    val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument)
    WorksheetFilePersistentSettings(file.getVirtualFile).setInteractive(true)

    val robot = new EditorRobot(editor)
    robot.moveToEnd()
    robot.typeString("\n2 + unknownRef + 4\n")

    MyUiUtils.wait(5 seconds)

    assertViewerEditorText(editor,
      """res0: Int = 42""".stripMargin
    )
    assertNoErrorMessages(editor)
  }

  def testAutoFlushOnLongEvaluation_DefaultAutoFlushTimeout(): Unit =
    doTestAutoFlushOnLongEvaluationNTimes(
      timesToRunTest = 3,
      autoFlushTimeout = WorksheetEditorPrinterFactory.IDLE_TIME,
      sleepInLoop = WorksheetEditorPrinterFactory.IDLE_TIME.mul(1.1)
    )

  // flush timeout is currently not intended to be changed  by user via any setting,
  // but this test helps catching concurrency bugs
  def testAutoFlushOnLongEvaluation_SmallAutoFlushTimeout(): Unit =
    doTestAutoFlushOnLongEvaluationNTimes(
      timesToRunTest = 20,
      autoFlushTimeout = 10.millis,
      sleepInLoop = 100.millis
    )

  /** @param timesToRunTest use large values  to catch concurrency-related bugs locally */
  private def doTestAutoFlushOnLongEvaluationNTimes(
    timesToRunTest: Int,
    autoFlushTimeout: FiniteDuration,
    sleepInLoop: Duration,
  ): Unit = {
    val before = WorksheetEditorPrinterFactory.IDLE_TIME
    try {
      WorksheetEditorPrinterFactory.IDLE_TIME = autoFlushTimeout
      val attempts = timesToRunTest
      for (i <- 1 to attempts) {
        println(s"test run $i")
        doTestAutoFlushOnLongEvaluation(sleepInLoop)
      }
    } finally {
      WorksheetEditorPrinterFactory.IDLE_TIME = before
    }
  }

  private def doTestAutoFlushOnLongEvaluation(sleepInLoop: Duration): Unit = {
    val sleepTime: Int = sleepInLoop.toMillis.toInt
    val leftText =
      s"""println("a\\nb\\nc")
         |
         |def foo() = {
         |  for (i <- 1 to 3) {
         |    println(s"Hello $$i")
         |    Thread.sleep($sleepTime)
         |  }
         |}
         |
         |foo()
         |foo()
         |""".stripMargin // TODO: extra foo()

    // NOTE: this test operates with race condition (worksheet prints with timeout, printer timer flushes with timout)
    // so it's hard to test the exact states during evaluation. But we know for sure that the resulting states should
    // be from the below set in the same order. (so, some states can be missing)
    // NOTE: each output line is processed separately, even for the same input line
    // between these processing auto-flush can appear
    val states1 = Seq(
      s"a",
      s"${foldStart}a\nb$foldEnd",
      s"${foldStart}a\nb\nc$foldEnd",
      s"${foldStart}a\nb\nc\nres0: Unit = ()$foldEnd",
      s"${foldStart}a\nb\nc\nres0: Unit = ()$foldEnd\n",
      s"${foldStart}a\nb\nc\nres0: Unit = ()$foldEnd\n\n",
      s"${foldStart}a\nb\nc\nres0: Unit = ()$foldEnd\n\nfoo: foo[]() => Unit",
      s"${foldStart}a\nb\nc\nres0: Unit = ()$foldEnd\n\nfoo: foo[]() => Unit\n",
      s"${foldStart}a\nb\nc\nres0: Unit = ()$foldEnd\n\nfoo: foo[]() => Unit\n\n",
      s"${foldStart}a\nb\nc\nres0: Unit = ()$foldEnd\n\nfoo: foo[]() => Unit\n\n\n",
      s"${foldStart}a\nb\nc\nres0: Unit = ()$foldEnd\n\nfoo: foo[]() => Unit\n\n\n\n",
      s"${foldStart}a\nb\nc\nres0: Unit = ()$foldEnd\n\nfoo: foo[]() => Unit\n\n\n\n\n",
      s"${foldStart}a\nb\nc\nres0: Unit = ()$foldEnd\n\nfoo: foo[]() => Unit\n\n\n\n\n\n",
    )
    //noinspection RedundantBlock
    val states2 = Seq(
      "",
      s"\nHello 1",
      s"\n${foldStart}Hello 1\nHello 2${foldEnd}",
      s"\n${foldStart}Hello 1\nHello 2\nHello 3${foldEnd}",
      s"\n${foldStart}Hello 1\nHello 2\nHello 3\nres1: Unit = ()${foldEnd}",
      s"\n${foldStart}Hello 1\nHello 2\nHello 3\nres1: Unit = ()${foldEnd}\nHello 1",
      s"\n${foldStart}Hello 1\nHello 2\nHello 3\nres1: Unit = ()${foldEnd}\n${foldStart}Hello 1\nHello 2$foldEnd",
      s"\n${foldStart}Hello 1\nHello 2\nHello 3\nres1: Unit = ()${foldEnd}\n${foldStart}Hello 1\nHello 2\nHello 3$foldEnd",
      s"\n${foldStart}Hello 1\nHello 2\nHello 3\nres1: Unit = ()${foldEnd}\n${foldStart}Hello 1\nHello 2\nHello 3\nres2: Unit = ()$foldEnd",
    ).map(states1.last + _)

    val rightExpectedStates = (states1 ++ states2).map(_.withNormalizedSeparator)

    val viewerStates: Seq[ViewerEditorData] = runLongEvaluation(leftText).distinct

    def statesText(statesRendered: Seq[String]): String = {
      val statesSeparator = "\n#####\n"
      statesRendered.zipWithIndex.map { case (state, idx) => s"$idx: $state" }.mkString(statesSeparator)
    }

    val flushAtLeast = 3
    assertTrue(
      s"""editor should be flushed at least $flushAtLeast times, but flushed only ${viewerStates.size} times, states:
         |${statesText(viewerStates.map(renderViewerData))}""".stripMargin,
      viewerStates.size >= flushAtLeast
    )
    var lastStateIdx = -1
    viewerStates.zipWithIndex.foreach { case (actualViewerState, actualStateIdx) =>
      val actualTextWithFoldings = renderViewerData(actualViewerState)
      rightExpectedStates.indexWhere(_ == actualTextWithFoldings) match {
        case idx if idx > lastStateIdx=>
          lastStateIdx = idx
        case _ =>
          val message = s"editor state at step $actualStateIdx doesn't match any expected state:\n$actualTextWithFoldings"
          val expected = statesText(rightExpectedStates)
          val actual = statesText(viewerStates.map(renderViewerData))
          // NOTE: it's not actually a proper usage of ComparisonFailure, cause left and right are not intended to be equal
          // but I use it to conveniently view expected and actual states in a diff view in IDEA
          throw new ComparisonFailure(message, expected, actual)
      }
    }

    assertEquals(
      "final editor state doesn't match",
      rightExpectedStates.last,
      renderViewerData(viewerStates.last)
    )
  }

  private def renderViewerData(viewerData: ViewerEditorData): String = {
    val text = viewerData.text
    val foldings = viewerData.foldings
    val builder = new java.lang.StringBuilder()

    val foldingsWithHelpers = Folding(0, 0) +: foldings :+ Folding(text.length, text.length)
    foldingsWithHelpers.sliding(2).foreach { case Seq(prev, Folding(startOffset, endOffset, isExpanded)) =>
      builder.append(text, prev.endOffset, startOffset)
      val isHelperFolding = startOffset == endOffset && startOffset == text.length
      if (!isHelperFolding) {
        builder.append(if (isExpanded) foldStartExpanded else foldStart)
          //.append(placeholder)
          .append(text, startOffset, endOffset)
          .append(if (isExpanded) foldEndExpanded else foldEnd)
      }
    }
    builder.toString
  }

  private def runLongEvaluation(leftText: String): Seq[ViewerEditorData] = {
    val editor = prepareWorksheetEditor(leftText)

    val evaluationResult = waitForEvaluationEnd(runWorksheetEvaluation(editor))
    assertEquals(RunWorksheetActionResult.Done, evaluationResult)

    val printer = WorksheetCache.getInstance(project).getPrinter(editor)
      .getOrElse(fail("no printer found").asInstanceOf[Nothing]).asInstanceOf[WorksheetEditorPrinterPlain]
    val viewer = WorksheetCache.getInstance(project).getViewer(editor)

    val viewerStates: Seq[ViewerEditorData] =
      printer.viewerEditorStates.map { case ViewerEditorState(text, foldings) =>
        val foldingsConverted = foldings.map { case FoldingDataForTests(start, end, _, expanded) =>
          Folding(start, end, expanded)
        }
        ViewerEditorData(viewer, text, foldingsConverted)
      }.toSeq

    viewerStates
  }

  private val TestProfileName = "TestProfileName"
  private val PartialUnificationCompilerOptions = Seq("-Ypartial-unification", "-language:higherKinds")
  private val PartialUnificationTestText =
    """def foo[F[_], A](fa: F[A]): String = "123"
      |foo { x: Int => x * 2 }
      |""".stripMargin

  // -Ypartial-unification is enabled in 2.13 by default, so testing on 2.12
  @RunWithScalaVersions(Array(TestScalaVersion.Scala_2_12))
  def testWorksheetShouldRespectCompilerSettingsFromCompilerProfile(): Unit = {
    val editor = prepareWorksheetEditor(PartialUnificationTestText, scratchFile = true)
    val profile = getModule.scalaCompilerSettingsProfile
    val newSettings = profile.getSettings.copy(
      additionalCompilerOptions = PartialUnificationCompilerOptions
    )
    profile.setSettings(newSettings)
    doRenderTest(editor,
      """foo: foo[F[_],A](val fa: F[A]) => String
        |res0: String = 123""".stripMargin
    )
  }

  @RunWithScalaVersions(Array(TestScalaVersion.Scala_2_12))
  def testWorksheetShouldRespectCompilerSettingsFromCompilerProfile_WithoutSetting(): Unit = {
    val editor = prepareWorksheetEditor(PartialUnificationTestText, scratchFile = true)
    val profile = getModule.scalaCompilerSettingsProfile
    val newSettings = profile.getSettings.copy(
      additionalCompilerOptions = Seq.empty
    )
    profile.setSettings(newSettings)
    doResultTest(editor, RunWorksheetActionResult.WorksheetRunError(WorksheetCompilerResult.CompilationError))
  }

  @RunWithScalaVersions(Array(TestScalaVersion.Scala_2_12))
  def testWorksheetShouldRespectCompilerSettingsFromCompilerProfile_NonDefaultProfile(): Unit = {
    val editor = prepareWorksheetEditor(PartialUnificationTestText, scratchFile = true)
    worksheetSettings(editor).setCompilerProfileName(TestProfileName)
    val profile = createCompilerProfileForCurrentModule(TestProfileName)
    val newSettings = profile.getSettings.copy(
      additionalCompilerOptions = PartialUnificationCompilerOptions
    )
    profile.setSettings(newSettings)
    doRenderTest(editor,
      """foo: foo[F[_],A](val fa: F[A]) => String
        |res0: String = 123""".stripMargin
    )
  }

  @RunWithScalaVersions(Array(TestScalaVersion.Scala_2_12))
  def testWorksheetShouldRespectCompilerSettingsFromCompilerProfile_WithoutSetting_NonDefaultProfile(): Unit = {
    val editor = prepareWorksheetEditor(PartialUnificationTestText, scratchFile = true)
    worksheetSettings(editor).setCompilerProfileName(TestProfileName)
    val profile = createCompilerProfileForCurrentModule(TestProfileName)
    val newSettings = profile.getSettings.copy(
      additionalCompilerOptions = Seq.empty
    )
    profile.setSettings(newSettings)
    doResultTest(editor, RunWorksheetActionResult.WorksheetRunError(WorksheetCompilerResult.CompilationError))
  }

  // TODO: it flickers in WorksheetPlainCompileOnServerRunLocallyIntegrationTest, but works fine in prod
  @RunWithScalaVersions(Array(TestScalaVersion.Scala_3_0))
  def testScala3_AllInOne(): Unit = {
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
      s"""
         |
         |
         |
         |${foldStart}List(1, 2, 3)
         |val res0: Unit = ()$foldEnd
         |${foldStart}1
         |val res1: Unit = ()${foldEnd}
         |
         |val res2: Unit = ()
         |val res3: Int = 23
         |val res4: String = str
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
         |val q1: scala.concurrent.duration.package.DurationInt = scala.concurrent.duration.package$$DurationInt@3
         |var q2: scala.concurrent.duration.package.DurationInt = scala.concurrent.duration.package$$DurationInt@4
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
         |// defined enum ListEnum
         |
         |
         |
         |
         |${foldStart}Empty
         |val res5: Unit = ()${foldEnd}
         |${foldStart}Cons(42,Empty)
         |val res6: Unit = ()${foldEnd}""".stripMargin
    doRenderTest(before, after)
  }

  @RunWithScalaVersions(Array(TestScalaVersion.Scala_3_0))
  def testScala3_WithBracelessSyntax(): Unit = {
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
}