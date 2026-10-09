package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.Disposable
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.{FileDocumentManager, FileDocumentSynchronizationVetoer}
import com.intellij.openapi.util.{Disposer, TextRange}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.compiler.{CompilerEvent, CompilerEventListener}
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.plugins.scala.util.CompilerTestUtil.runWithErrorsFromCompiler
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.util.control.NonFatal

/**
 * The editor moves a file's highlighters with its text, so between compilations they stay on the code they
 * belong to. A compilation that finishes while a file has been edited since its own last compilation must not
 * repaint that file from diagnostics computed for its previous text: their line and column positions describe
 * the old text, and drawn on the new one they land on different code.
 *
 * See `.junie/CbhStaleRepaintTicket.md`.
 */
@RunWith(classOf[JUnit4])
class StaleRepaintWhileCompilingTest extends ScalaCompilerHighlightingTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

  private val LinesInsertedAboveTheError = "// one\n// two\n// three\n"

  @Test
  def testEditDuringAnotherCompilationKeepsErrorsOnTheirCode(): Unit = runWithErrorsFromCompiler(getProject) {
    val broken = addFileToProjectSources("Broken.scala",
      """object Broken {
        |  val x: Int = "text"
        |}
        |""".stripMargin)
    val other = addFileToProjectSources("Other.scala",
      """object Other {
        |  val n: Int = 0
        |}
        |""".stripMargin)

    waitUntilHighlightingApplied(broken)(openEditor(broken))
    val errorTextsBefore = errorTexts(broken)
    assertTrue("expected a compiler error in Broken.scala to start from", errorTextsBefore.nonEmpty)

    // Insert lines above the error in `Broken` while a compilation of `Other` is running. Blocking the event
    // thread until the edit is applied guarantees it lands before that compilation finishes. A failure is kept
    // rather than thrown: thrown here, it would abort the compilation and surface only as a timeout.
    val edited = new AtomicBoolean(false)
    val editFailure = new AtomicReference[Throwable]()
    val connection = getProject.getMessageBus.connect()
    connection.subscribe(CompilerEventListener.topic, new CompilerEventListener {
      override def eventReceived(event: CompilerEvent): Unit = event match {
        case _: CompilerEvent.CompilationStarted if edited.compareAndSet(false, true) =>
          try insertAtStart(broken, LinesInsertedAboveTheError)
          catch { case NonFatal(t) => editFailure.set(t) }
        case _ =>
      }
    })
    try {
      // `Other` is edited so that it needs a compilation of its own, one whose scope does not include `Broken`.
      waitUntilHighlightingApplied(other) {
        setContent(other,
          """object Other {
            |  val n: Int = 1
            |}
            |""".stripMargin)
        openEditor(other)
      }
    } finally {
      connection.disconnect()
    }

    Option(editFailure.get()).foreach(t => throw new AssertionError("the edit to Broken.scala failed", t))
    assertTrue("the edit to Broken.scala did not land during the compilation of Other.scala", edited.get())
    assertEquals(
      "the errors in Broken.scala no longer cover the code they belong to",
      errorTextsBefore,
      errorTexts(broken)
    )
  }
  
  @Test
  def testVetoedSaveKeepsErrorsOnTheirCode(): Unit = runWithErrorsFromCompiler(getProject) {
    val broken = addFileToProjectSources("Broken.scala",
      """object Broken {
        |  val x: Int = "text"
        |}
        |""".stripMargin)
    val other = addFileToProjectSources("Other.scala",
      """object Other {
        |  val n: Int = 0
        |}
        |""".stripMargin)

    waitUntilHighlightingApplied(broken)(openEditor(broken))
    val errorTextsBefore = errorTexts(broken)
    assertTrue("expected a compiler error in Broken.scala to start from", errorTextsBefore.nonEmpty)

    val vetoDisposable = Disposer.newDisposable()
    try {
      vetoSaving(broken, vetoDisposable)
      // The disk keeps the error on line 2, while the document moves it down.
      insertAtStart(broken, LinesInsertedAboveTheError)
      waitUntilHighlightingApplied(other) {
        setContent(other,
          """object Other {
            |  val n: Int = 1
            |}
            |""".stripMargin)
        openEditor(other)
      }

      // Had the save gone through, the compiler would have read the edited text and the check would be vacuous.
      assertTrue("the save of Broken.scala was not vetoed",
        invokeAndWait(FileDocumentManager.getInstance().isDocumentUnsaved(documentOf(broken))))
      assertEquals(
        "the errors in Broken.scala no longer cover the code they belong to",
        errorTextsBefore,
        errorTexts(broken)
      )
    } finally {
      Disposer.dispose(vetoDisposable)
    }
  }

  /** Refuses every save of `virtualFile`'s document until `parentDisposable` is disposed, as a plugin may. */
  private def vetoSaving(virtualFile: VirtualFile, parentDisposable: Disposable): Unit = {
    val vetoed = invokeAndWait(documentOf(virtualFile))
    val vetoer = new FileDocumentSynchronizationVetoer {
      override def maySaveDocument(document: Document, isSaveExplicit: Boolean): Boolean = document ne vetoed
    }
    FileDocumentSynchronizationVetoer.EP_NAME.getPoint.registerExtension(vetoer, parentDisposable)
  }

  /** The text under each compiler error in `virtualFile`, at the error's current position in the document. */
  private def errorTexts(virtualFile: VirtualFile): Seq[String] = {
    val errors = fetchHighlightInfos(virtualFile).filter(_.getSeverity == HighlightSeverity.ERROR)
    invokeAndWait {
      val document = documentOf(virtualFile)
      // `getStartOffset` follows the highlighter as the text moves, unlike the offsets the info was created with.
      errors.map(info => document.getText(TextRange.create(info.getStartOffset, info.getEndOffset))).sorted
    }
  }

  /** A document open in an editor can only be changed inside a command, as a user's edit would be. */
  private def insertAtStart(virtualFile: VirtualFile, text: String): Unit = invokeAndWait {
    WriteCommandAction.runWriteCommandAction(getProject, (() => {
      val document = documentOf(virtualFile)
      document.insertString(0, text)
      PsiDocumentManager.getInstance(getProject).commitDocument(document)
    }): Runnable)
  }

  private def setContent(virtualFile: VirtualFile, content: String): Unit = invokeAndWait {
    WriteCommandAction.runWriteCommandAction(getProject, (() => {
      val document = documentOf(virtualFile)
      document.setText(content)
      PsiDocumentManager.getInstance(getProject).commitDocument(document)
    }): Runnable)
  }

  private def documentOf(virtualFile: VirtualFile): Document =
    FileDocumentManager.getInstance().getDocument(virtualFile)
}
