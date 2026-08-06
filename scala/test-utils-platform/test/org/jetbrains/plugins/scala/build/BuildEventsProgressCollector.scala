package org.jetbrains.plugins.scala.build

import com.intellij.build.BuildProgressListener
import com.intellij.build.events.MessageEvent.Kind
import com.intellij.build.events.{BuildEvent, Failure, FailureResult, FinishEvent, MessageEvent, OutputBuildEvent, StartBuildEvent}
import com.intellij.execution.process.ProcessOutputType
import org.jetbrains.plugins.scala.build.BuildEventsProgressCollector._

import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}
import scala.jdk.CollectionConverters._

/**
 * Collects Build Tool Window diagnostics for a single captured build.
 */
final class BuildEventsProgressCollector extends BuildProgressListener {

  private val startedBuildIds = ConcurrentHashMap.newKeySet[Any]()
  private val capturedErrors = new ConcurrentLinkedQueue[String]()
  private val capturedWarnings = new ConcurrentLinkedQueue[String]()
  private val capturedRootOutput = new ConcurrentLinkedQueue[String]()
  private val capturedFinishFailures = new ConcurrentLinkedQueue[String]()

  def snapshot: Snapshot =
    Snapshot(
      errors = nonEmptyEntries(capturedErrors),
      warnings = nonEmptyEntries(capturedWarnings),
      rootOutput = nonEmptyEntries(capturedRootOutput),
      finishFailures = nonEmptyEntries(capturedFinishFailures),
    )

  override def onEvent(buildId: AnyRef, event: BuildEvent): Unit = event match {
    case _: StartBuildEvent =>
      startedBuildIds.add(buildId)
    case messageEvent: MessageEvent =>
      val shouldCapture = isRelatedToCapturedBuild(buildId, messageEvent)
      if (shouldCapture) {
        val rendered = renderMessageEvent(messageEvent)
        messageEvent.getKind match {
          case Kind.ERROR =>
            capturedErrors.add(rendered)
          case Kind.WARNING =>
            capturedWarnings.add(rendered)
          case _ =>
        }
      }
    case outputEvent: OutputBuildEvent =>
      val shouldCapture = isRelatedToCapturedBuild(buildId, outputEvent)
      if (shouldCapture && isRootBuildOutput(outputEvent, buildId)) {
        val rendered = renderOutputEvent(outputEvent)
        capturedRootOutput.add(rendered)
      }
    case finishEvent: FinishEvent =>
      val shouldCapture = isRelatedToCapturedBuild(buildId, finishEvent)
      if (shouldCapture) {
        finishEvent.getResult match {
          case failureResult: FailureResult =>
            val rendered = renderFailureResult(finishEvent, failureResult)
            capturedFinishFailures.add(rendered)
          case _ =>
        }
      }
    case _ =>
  }

  // Defensive filter: in theory `buildId` should already scope us to the current build,
  // but Build Tool Window events can be noisy or nested; keeping parent-id matching here is cheap
  // and helps avoid capturing unrelated events just in case.
  private def isRelatedToCapturedBuild(buildId: AnyRef, event: BuildEvent): Boolean =
    startedBuildIds.contains(buildId) || Option(event.getParentId).exists(startedBuildIds.contains)

  private def isRootBuildOutput(outputEvent: OutputBuildEvent, buildId: AnyRef): Boolean = {
    val parentId = outputEvent.getParentId
    parentId == null || parentId == buildId
  }
}

object BuildEventsProgressCollector {
  final case class Snapshot(
    errors: Seq[String],
    warnings: Seq[String],
    rootOutput: Seq[String],
    finishFailures: Seq[String],
  )

  private def renderMessageEvent(messageEvent: MessageEvent): String = {
    val message = BuildDiagnosticsText.normalizedAnsiText(messageEvent.getMessage)
    val details =
      BuildDiagnosticsText.normalizedAnsiTextNonempty(messageEvent.getDescription)
        .filterNot(_ == message)

    val group = messageEvent.getGroup
    details match {
      case Some(value) => s"[$group] $message | $value"
      case None => s"[$group] $message"
    }
  }

  private def renderOutputEvent(outputEvent: OutputBuildEvent): String = {
    val message = BuildDiagnosticsText.normalizedAnsiOutputText(outputEvent.getMessage)
    if (message.isEmpty) ""
    else {
      val outputType = normalizeOutputType(outputEvent.getOutputType)
      s"[$outputType] $message"
    }
  }

  private def renderFailureResult(finishEvent: FinishEvent, failureResult: FailureResult): String = {
    val eventMessage = BuildDiagnosticsText.normalizedAnsiText(finishEvent.getMessage)
    val renderedFailures = Option(failureResult.getFailures)
      .toSeq
      .flatMap(_.asScala)
      .flatMap(renderFailure)
      .mkString("; ")

    if (renderedFailures.nonEmpty) s"$eventMessage | $renderedFailures" else eventMessage
  }

  private def normalizeOutputType(outputType: ProcessOutputType): String =
    if (outputType == ProcessOutputType.STDERR) "stderr"
    else if (outputType == ProcessOutputType.SYSTEM) "system"
    else "stdout"

  private def renderFailure(failure: Failure): Option[String] = {
    val message = BuildDiagnosticsText.normalizedAnsiTextNonempty(failure.getMessage)
    val description = BuildDiagnosticsText.normalizedAnsiTextNonempty(failure.getDescription)
    val throwable = normalizedOptionalText(Option(failure.getError).map(_.toString))
    val causes = Option(failure.getCauses).toSeq.flatMap(_.asScala).flatMap(renderFailure)

    val pieces = Seq(message, description, throwable).flatten ++ causes
    if (pieces.nonEmpty) Some(pieces.mkString(" | ")) else None
  }

  private def nonEmptyEntries(values: java.util.Collection[String]): Seq[String] =
    values.iterator().asScala.filter(_.nonEmpty).toSeq

  private def normalizedOptionalText(text: Option[String]): Option[String] =
    text.map(_.trim).filter(_.nonEmpty)
}
