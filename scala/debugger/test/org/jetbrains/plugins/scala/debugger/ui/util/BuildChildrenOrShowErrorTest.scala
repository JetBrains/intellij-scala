package org.jetbrains.plugins.scala
package debugger
package ui
package util

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.frame.{XCompositeNode, XDebuggerTreeNodeHyperlink, XValueChildrenList}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.util.CollectingLoggedMessagesProcessor.collectErrorThrowables
import org.junit.Assert.{assertEquals, assertThrows}
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.util.concurrent.{CompletableFuture, ConcurrentLinkedQueue}
import javax.swing.Icon
import scala.jdk.CollectionConverters.*

/**
 * Checks that a debugger tree node whose children cannot be built finishes with an error message instead of staying
 * at "Collecting data" for good, SCL-26219.
 */
@RunWith(classOf[JUnit4])
@Category(Array(classOf[DebuggerTests]))
class BuildChildrenOrShowErrorTest extends ScalaLightCodeInsightFixtureTestCase:
  import BuildChildrenOrShowErrorTest.*

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  @Test
  def builtChildrenShowNoError(): Unit =
    val node = RecordingNode()
    val shown = shownBy(node):
      buildChildrenOrShowError(node)(CompletableFuture.completedFuture(()))
    assertEquals(Shown(errorMessages = Seq.empty, loggedErrors = Seq.empty), shown)

  @Test
  def failedEvaluationShowsItsMessage(): Unit =
    val node = RecordingNode()
    val shown = shownBy(node):
      buildChildrenOrShowError(node)(CompletableFuture.failedFuture(EvaluateException("Method threw 'java.lang.IllegalStateException' exception.")))
    assertEquals(Shown(errorMessages = Seq("Method threw 'java.lang.IllegalStateException' exception."), loggedErrors = Seq.empty), shown)

  @Test
  def failureAfterBuildingStartedShowsTheError(): Unit =
    val node = RecordingNode()
    val shown = shownBy(node):
      val future = CompletableFuture[Unit]()
      buildChildrenOrShowError(node)(future)
      assertEquals(Seq.empty, node.errorMessages)
      future.completeExceptionally(EvaluateException("failed later"))
    assertEquals(Shown(errorMessages = Seq("failed later"), loggedErrors = Seq.empty), shown)

  @Test
  def failureOfAnEarlierStageShowsItsCause(): Unit =
    val node = RecordingNode()
    val shown = shownBy(node):
      // a later stage fails with a CompletionException that wraps the failure of the earlier one
      buildChildrenOrShowError(node)(CompletableFuture.failedFuture[Unit](EvaluateException("the cause")).map(identity))
    assertEquals(Shown(errorMessages = Seq("the cause"), loggedErrors = Seq.empty), shown)

  @Test
  def throwingWhileBuildingShowsTheError(): Unit =
    val node = RecordingNode()
    val shown = shownBy(node):
      buildChildrenOrShowError(node)(throw EvaluateException("thrown right away"))
    assertEquals(Shown(errorMessages = Seq("thrown right away"), loggedErrors = Seq.empty), shown)

  @Test
  def cancelledCommandShowsThatTheContextHasChanged(): Unit =
    val node = RecordingNode()
    val shown = shownBy(node):
      // what `onDebuggerManagerThread` does when its debugger command is cancelled, e.g. the debuggee resumed
      val command = CompletableFuture[Unit]()
      buildChildrenOrShowError(node)(command.map(identity))
      command.cancel(false)
    assertEquals(Shown(errorMessages = Seq(contextHasChanged), loggedErrors = Seq.empty), shown)

  @Test
  def unexpectedExceptionIsShownAndLoggedAsAnError(): Unit =
    val node = RecordingNode()
    val shown = shownBy(node):
      buildChildrenOrShowError(node)(CompletableFuture.failedFuture(new NoSuchElementException("None.get")))
    val expectedMessage = DebuggerBundle.message("error.while.collecting.data", "java.util.NoSuchElementException: None.get")
    assertEquals(Shown(errorMessages = Seq(expectedMessage), loggedErrors = Seq("java.util.NoSuchElementException: None.get")), shown)

  @Test
  def processCanceledExceptionIsShownAndRethrown(): Unit =
    val node = RecordingNode()
    val shown = shownBy(node):
      assertThrows(classOf[ProcessCanceledException], () => buildChildrenOrShowError(node)(throw ProcessCanceledException()))
    assertEquals(Shown(errorMessages = Seq(contextHasChanged), loggedErrors = Seq.empty), shown)

  @Test
  def obsoleteNodeIsLeftAlone(): Unit =
    val node = RecordingNode(obsolete = true)
    val shown = shownBy(node):
      buildChildrenOrShowError(node)(CompletableFuture.failedFuture(EvaluateException("too late")))
    assertEquals(Shown(errorMessages = Seq.empty, loggedErrors = Seq.empty), shown)

  /**
   * Runs `scenario` and returns the error messages that `node` shows and the errors that were logged meanwhile.
   */
  private def shownBy(node: RecordingNode)(scenario: => Unit): Shown =
    val (_, loggedErrors) = collectErrorThrowables(scenario)
    Shown(node.errorMessages, loggedErrors.map(_.toString))

  private def contextHasChanged: String = JavaDebuggerBundle.message("error.context.has.changed")

private object BuildChildrenOrShowErrorTest:
  final case class Shown(errorMessages: Seq[String], loggedErrors: Seq[String])

  final class RecordingNode(obsolete: Boolean = false) extends XCompositeNode:
    private val messages = ConcurrentLinkedQueue[String]()

    def errorMessages: Seq[String] = messages.asScala.toSeq

    override def setErrorMessage(errorMessage: String): Unit =
      messages.add(errorMessage)

    override def setErrorMessage(errorMessage: String, link: XDebuggerTreeNodeHyperlink): Unit =
      setErrorMessage(errorMessage)

    override def addChildren(children: XValueChildrenList, last: Boolean): Unit = ()

    //noinspection ScalaDeprecation
    override def tooManyChildren(remaining: Int): Unit = ()

    override def setAlreadySorted(alreadySorted: Boolean): Unit = ()

    override def setMessage(message: String, icon: Icon, attributes: SimpleTextAttributes, link: XDebuggerTreeNodeHyperlink): Unit = ()

    override def isObsolete: Boolean = obsolete
