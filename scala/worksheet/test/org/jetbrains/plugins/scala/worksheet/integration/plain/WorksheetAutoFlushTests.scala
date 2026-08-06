package org.jetbrains.plugins.scala.worksheet.integration.plain

import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.ui.AwaitTestUtils
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction.RunWorksheetActionResult
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetIntegrationBaseTest.{Folding, ViewerEditorData}
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetCache
import org.jetbrains.plugins.scala.worksheet.ui.printers.WorksheetEditorPrinterPlain.{FoldingDataForTests, ViewerEditorState}
import org.jetbrains.plugins.scala.worksheet.ui.printers.{WorksheetEditorPrinterFactory, WorksheetEditorPrinterPlain}
import org.junit.Assert.{assertEquals, assertTrue, fail}
import org.junit.{ComparisonFailure, Test}

import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}

class WorksheetPlainCompileOnServerRunLocallyAutoFlushTest extends WorksheetPlainAutoFlushTestBase {
  override def useCompileServer = true
  override def runInCompileServerProcess = false
}

class WorksheetPlainCompileLocallyRunLocallyAutoFlushTest extends WorksheetPlainAutoFlushTestBase {
  override def useCompileServer = false
  override def runInCompileServerProcess = false
}

abstract class WorksheetPlainAutoFlushTestBase extends PlainWorksheetTestBase {
  @Test
  def testAutoFlushOnLongEvaluation_DefaultAutoFlushTimeout(): Unit =
    doTestAutoFlushOnLongEvaluationNTimes(
      timesToRunTest = 3,
      autoFlushTimeout = WorksheetEditorPrinterFactory.IDLE_TIME,
      sleepInLoop = WorksheetEditorPrinterFactory.IDLE_TIME.mul(1.1)
    )

  // flush timeout is currently not intended to be changed by user via any setting,
  // but this test helps to catch concurrency bugs
  @Test
  def testAutoFlushOnLongEvaluation_SmallAutoFlushTimeout(): Unit =
    doTestAutoFlushOnLongEvaluationNTimes(
      timesToRunTest = 10,
      autoFlushTimeout = 10.millis,
      sleepInLoop = 40.millis
    )


  /** @param timesToRunTest use large values to catch concurrency-related bugs locally */
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

    // NOTE: this test operates with race condition (worksheet prints with timeout, printer timer flushes with timeout),
    // so it's hard to test the exact states during evaluation. But we know for sure that the resulting states should
    // be from the below set in the same order. (so, some states can be missing)
    // NOTE: each output line is processed separately, even for the same input line
    // between these processing an auto-flush can appear
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

    val viewerStates: Seq[ViewerEditorData] = runLongEvaluation(leftText, rightExpectedStates.last).distinct

    // Example:
    // ##### State #0:
    // a
    // ##### State #1:
    // a
    // b
    def statesText(statesRendered: Seq[String]): String = {
      statesRendered.zipWithIndex
        .map { case (state, idx) => s"##### State #$idx:\n$state" }
        .mkString("\n")
    }

    val renderedStates = viewerStates.map(renderViewerData)
    val finalRenderedState = renderedStates.last
    // We only need to prove there was visible progress before completion:
    // one intermediate rendered state + one final rendered state.
    val hasIntermediateState = renderedStates.dropRight(1).exists(_ != finalRenderedState)
    assertTrue(
      s"""editor should observe at least one intermediate state before the final one, but observed only final state transitions, states:
         |${statesText(renderedStates)}""".stripMargin,
      hasIntermediateState
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
          // NOTE: it's not actually a proper usage of ComparisonFailure, cause left and right are not intended to be equal,
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

  private def runLongEvaluation(leftText: String, expectedFinalState: String): Seq[ViewerEditorData] = {
    val editorAndFile = prepareWorksheetEditor(leftText)

    val evaluationResult = waitForEvaluationEnd(runWorksheetEvaluation(editorAndFile))
    assertEquals(RunWorksheetActionResult.Done, evaluationResult)

    val printer = WorksheetCache.getInstance(project).getPrinter(editorAndFile.editor)
      .getOrElse(fail("no printer found").asInstanceOf[Nothing]).asInstanceOf[WorksheetEditorPrinterPlain]
    val viewer = WorksheetCache.getInstance(project).getViewer(editorAndFile.editor)

    def collectViewerStates(): Seq[ViewerEditorData] =
      printer.viewerEditorStates.map { case ViewerEditorState(text, foldings) =>
        val foldingsConverted = foldings.map { case FoldingDataForTests(start, end, _, expanded) =>
          Folding(start, end, expanded)
        }
        ViewerEditorData(viewer, text, foldingsConverted)
      }.toSeq

    // The evaluation Future is completed from `processTerminated` (see WorksheetCompilerLocalEvaluator),
    // which can win a race against the terminal flush triggered by the EVALUATION_END marker line.
    // On slower CI agents the last recorded viewer state can therefore still be an intermediate one
    // (missing the final `resN: ...` line) at the instant the Future completes; the worksheet document
    // is completed correctly a moment later. Wait for that final flush to be recorded before capturing
    // the states, instead of snapshotting them the instant evaluation "ends".
    AwaitTestUtils.waitForConditionDispatchingEdtEventsOrFail(
      10.seconds,
      "worksheet viewer did not reach the expected final evaluation state"
    )(() => collectViewerStates().lastOption.map(renderViewerData).contains(expectedFinalState))

    collectViewerStates()
  }
}
