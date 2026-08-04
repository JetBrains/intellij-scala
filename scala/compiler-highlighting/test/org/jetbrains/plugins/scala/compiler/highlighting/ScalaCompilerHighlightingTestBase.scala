package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.codeInsight.daemon.impl.{DaemonCodeAnalyzerImpl, HighlightInfo}
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.{Editor, EditorFactory}
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import com.intellij.testFramework.{EdtTestUtil, IndexingTestUtil}
import com.intellij.util.messages.MessageBusConnection
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.{Description, Matcher}
import org.jetbrains.plugins.scala.CompilerHighlightingTests
import org.jetbrains.plugins.scala.compiler.{CompilerEvent, CompilerEventListener, ScalaCompilerTestBase}
import org.jetbrains.plugins.scala.extensions.{HighlightInfoExt, inReadAction, invokeAndWait, invokeLater}
import org.jetbrains.plugins.scala.project.VirtualFileExt
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.ScalaHighlightingMode
import org.jetbrains.plugins.scala.util.CompilerTestUtil.runWithErrorsFromCompiler
import org.jetbrains.plugins.scala.util.matchers.{HamcrestMatchers, ScalaBaseMatcher}
import org.junit.Assert.fail
import org.junit.experimental.categories.Category

import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

@Category(Array(classOf[CompilerHighlightingTests]))
abstract class ScalaCompilerHighlightingTestBase
  extends ScalaCompilerTestBase
    with HamcrestMatchers {

  protected var myEditor: Editor = uninitialized
  protected var myPsiFile: PsiFile = uninitialized

  // Safety-net timeout: normally the wait returns as soon as highlighting is applied. Hitting it means the
  // notification never arrived, i.e. compilation was never triggered for the file.
  private final val HighlightingAppliedTimeoutSeconds: Long = 30L

  override def useCompileServer: Boolean = true
  override def runInDispatchThread: Boolean = false

  override def setUp(): Unit = {
    EdtTestUtil.runInEdtAndWait(() => {
      ScalaCompilerHighlightingTestBase.super.setUp()
    })

    // Since the implementation of IJPL-165 and IDEA-341372, these tests specifically require manually saving the JDK
    // table to disk. This is because these tests invoke the Scala Compile Server directly in order to provide
    // Compiler Based Highlighting, without going through the CompilerTester API. Not saving the JDK to disk will
    // result in the Scala Compile Server not compiling any of the project code.
    ProjectJdkTable.getInstance().saveOnDisk()
    IndexingTestUtil.waitUntilIndexesAreReady(myProject)
  }

  override protected def tearDown(): Unit = {
    myEditor = null
    myPsiFile = null
    super.tearDown()
  }

  type ExpectedResult = Matcher[Seq[HighlightInfo]]


  protected def openAndFocusEditor(virtualFile: VirtualFile): Unit = invokeLater {
    val descriptor = new OpenFileDescriptor(getProject, virtualFile)
    val editor = FileEditorManager.getInstance(getProject).openTextEditor(descriptor, true)
    // The tests are running in a headless environment where focus events are not propagated.
    // We need to call our listener manually.
    new CompilerHighlightingEditorFocusListener(editor).focusGained()
  }

  protected def runTestCase(
    fileName: String,
    content: String,
    expectedResult: ExpectedResult,
    logTimestamps: Boolean = false,
    compileCycles: Int = 1,
  ): Unit = {
    val timeline = new PhaseTimeline(s"[compiler highlighting timing] test='$getName' file='$fileName'")
    timeline.mark(Phase.OverallStart)
    try {
      runWithErrorsFromCompiler(getProject) {
        // Only listen to compiler events (to break the wait into sub-phases) when we are going to log timings.
        val compilerEventsConnection = if (logTimestamps) Some(subscribeToCompilerEventTimings(timeline)) else None
        try {
          val virtualFile = addFileToProjectSources(fileName, content)

          timeline.mark(Phase.WaitStart)
          waitUntilHighlightingApplied(virtualFile, compileCycles) {
            timeline.mark(Phase.TriggerStart)
            openAndFocusEditor(virtualFile)
            timeline.mark(Phase.TriggerEnd)
          }
          timeline.mark(Phase.HighlightingApplied)

          doAssertion(virtualFile, expectedResult)
        } finally {
          compilerEventsConnection.foreach(_.disconnect())
        }
      }
    } finally {
      timeline.mark(Phase.OverallEnd)
      if (logTimestamps) printPhaseTimings(timeline)
    }
  }

  /** Records [[CompilerEvent.CompilationStarted]]/[[CompilerEvent.CompilationFinished]] instants into `timeline`. */
  private def subscribeToCompilerEventTimings(timeline: PhaseTimeline): MessageBusConnection = {
    val connection = getProject.getMessageBus.connect()
    connection.subscribe(CompilerEventListener.topic, new CompilerEventListener {
      override def eventReceived(event: CompilerEvent): Unit = event match {
        case _: CompilerEvent.CompilationStarted => timeline.mark(Phase.CompilationStarted)
        case _: CompilerEvent.CompilationFinished => timeline.mark(Phase.CompilationFinished)
        case _ =>
      }
    })
    connection
  }

  private def printPhaseTimings(timeline: PhaseTimeline): Unit = {
    timeline.print(
      s"triggerCompilation=${timeline.elapsedMillis(Phase.TriggerStart, Phase.TriggerEnd)}ms,\n " +
        s"waitForHighlightingApplied=${timeline.elapsedMillis(Phase.WaitStart, Phase.HighlightingApplied)}ms,\n " +
        s" [timeToCompilationStart=${timeline.elapsedMillis(Phase.WaitStart, Phase.CompilationStarted)}ms, " +
        s"compile=${timeline.elapsedMillis(Phase.CompilationStarted, Phase.CompilationFinished)}ms, " +
        s"applyHighlighting=${timeline.elapsedMillis(Phase.CompilationFinished, Phase.HighlightingApplied)}ms], \n " +
        s"total=${timeline.elapsedMillis(Phase.OverallStart, Phase.OverallEnd)}ms"
    )
  }

  /** Names of the [[PhaseTimeline]] marks recorded by [[runTestCase]], kept here to avoid stringly-typed drift. */
  private object Phase {
    final val OverallStart = "overallStart"
    final val WaitStart = "waitStart"
    final val TriggerStart = "triggerStart"
    final val TriggerEnd = "triggerEnd"
    final val CompilationStarted = "compilationStarted"
    final val CompilationFinished = "compilationFinished"
    final val HighlightingApplied = "highlightingApplied"
    final val OverallEnd = "overallEnd"
  }

  /**
   * Adds the file, triggers compilation, waits until its highlighting is applied, then asserts it.
   * Returns the created [[VirtualFile]] so callers can inspect it further (e.g. to apply quick fixes).
   */
  protected def waitAndAssert(
    fileName: String,
    content: String,
    expectedResult: ExpectedResult,
    compileCycles: Int = 1,
  ): VirtualFile = {
    val virtualFile = addFileToProjectSources(fileName, content)
    waitUntilHighlightingApplied(virtualFile, compileCycles) {
      openAndFocusEditor(virtualFile)
    }
    doAssertion(virtualFile, expectedResult)
    virtualFile
  }

  /**
   * Subscribes (before running `triggerCompilation`, so events can't be missed), then blocks until `compileCycles`
   * highlighting cycles that touched `virtualFile` have finished.
   *
   * A focused file is compiled in more than one cycle: a fast incremental compilation first, then a full document
   * compilation. Highlightings that only the document compilation produces (e.g. unused imports)
   * appear in the second cycle, so such tests must pass `compileCycles = 2` to wait for it;
   * the default of 1 wakes on the first cycle.
   *
   * If the latch times out, fewer than `compileCycles` notifications were received, meaning compilation was not
   * triggered as expected, and the test fails.
   */
  protected def waitUntilHighlightingApplied(virtualFile: VirtualFile, compileCycles: Int = 1)(triggerCompilation: => Unit): Unit = {
    val highlightingAppliedLatch = new CountDownLatch(compileCycles)
    val connection = getProject.getMessageBus.connect()
    try {
      connection.subscribe(ExternalHighlightingAppliedListener.topic, new ExternalHighlightingAppliedListener {
        override def highlightingApplied(virtualFiles: Set[VirtualFile]): Unit =
          if (virtualFiles.contains(virtualFile)) highlightingAppliedLatch.countDown()
      })
      triggerCompilation
      val applied = highlightingAppliedLatch.await(HighlightingAppliedTimeoutSeconds, TimeUnit.SECONDS)
      if (!applied) {
        // Fewer than `compileCycles` notifications arrived: compilation was not triggered as expected.
        fail(s"Compiler highlighting was not applied to '${virtualFile.getName}'" +
          s" for $compileCycles compilation cycle(s) within $HighlightingAppliedTimeoutSeconds seconds.")
      }
    } finally {
      connection.disconnect()
    }
  }

  protected def doAssertion(virtualFile: VirtualFile,
                            expectedResult: ExpectedResult): Unit = {
    val actualResult = fetchHighlightInfos(virtualFile)
    assertThat(actualResult, expectedResult)
  }

  protected def fetchHighlightInfos(virtualFile: VirtualFile): Seq[HighlightInfo] = invokeAndWait {
    val document = virtualFile.findDocument.get
    myEditor = EditorFactory.getInstance().getEditors(document).head
    myPsiFile = PsiDocumentManager.getInstance(getProject).getPsiFile(document)
    DaemonCodeAnalyzerImpl.getHighlights(document, null, getProject).asScala.toSeq
  }

  protected case class ExpectedHighlighting(severity: HighlightSeverity,
                                            range: Option[TextRange] = None,
                                            quickFixDescriptions: Seq[String],
                                            msgPrefix: String = "")

  protected def expectedResult(expected: ExpectedHighlighting*): ExpectedResult = new ScalaBaseMatcher[Seq[HighlightInfo]] {

    override protected def valueMatches(actualValue: Seq[HighlightInfo]): Boolean =
      expected.corresponds(actualValue) { case (expected, actual) =>
        actual.getSeverity == expected.severity &&
          expected.range.forall(_ == actual.range) &&
          actual.getDescription.startsWith(expected.msgPrefix) &&
          quickFixDescriptions(actual).toSet == expected.quickFixDescriptions.toSet
      }

    override protected def description: String =
      descriptionFor(expected)

    override def describeMismatch(item: Any, description: Description): Unit =
      item match {
        case seq: Seq[HighlightInfo @unchecked] =>
          val itemFixed = descriptionFor(seq.map(toExpectedHighlighting))
          super.describeMismatch(itemFixed, description)
        case _ =>
          super.describeMismatch(item, description)
      }

    private def toExpectedHighlighting(info: HighlightInfo): ExpectedHighlighting =
      ExpectedHighlighting(info.getSeverity, Some(info.range), quickFixDescriptions(info), info.getDescription)

    private def quickFixDescriptions(info: HighlightInfo) = {
      inReadAction {
        val builder = Seq.newBuilder[String]
        info.findRegisteredQuickFix { (descriptor, _) =>
          val action = descriptor.getAction
          if (action.isAvailable(getProject, myEditor, myPsiFile)) {
            builder += action.getText
          }
          null
        }
        builder.result()
      }
    }

    private def descriptionFor(highlightings: Seq[ExpectedHighlighting]): String =
      highlightings.map(descriptionFor).mkString("\n")

    private def descriptionFor(highlighting: ExpectedHighlighting): String = {
      val ExpectedHighlighting(severity, range, quickFixDescriptions, msgPrefix) = highlighting
      val values = Seq(
        "severity" -> severity,
        "range" -> range.getOrElse("?"),
        "quickFixDescriptions" -> quickFixDescriptions,
        "msgPrefix" -> msgPrefix
      ).map { case (name, value) =>
        s"$name=$value"
      }.mkString(",")
      s"HighlightInfo($values)"
    }
  }

  protected def setCompilerOptions(options: String*): Unit = {
    val defaultProfile = ScalaCompilerConfiguration.instanceIn(getProject).defaultProfile
    val newSettings = defaultProfile.getSettings.copy(
      additionalCompilerOptions = options
    )
    defaultProfile.setSettings(newSettings)
  }

  protected def withUseCompilerRangesDisabled(test: => Unit): Unit = {
    def setUseCompilerRanges(value: Boolean): Unit = {
      Registry.get("scala.compiler.highlighting.use.compiler.ranges").setValue(value)
    }

    val oldValue = ScalaHighlightingMode.useCompilerRanges
    try {
      setUseCompilerRanges(false)
      test
    } finally {
      setUseCompilerRanges(oldValue)
    }
  }
}
