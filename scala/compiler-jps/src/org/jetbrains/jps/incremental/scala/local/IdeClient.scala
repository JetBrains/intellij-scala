package org.jetbrains.jps.incremental.scala
package local

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.messages.{BuildMessage, CompilerMessage, CustomBuilderMessage, FileDeletedEvent, ProgressMessage}
import org.jetbrains.jps.incremental.scala.Client.PosInfo
import org.jetbrains.jps.incremental.scala.remote.CompileServerMetrics
import org.jetbrains.plugins.scala.compiler.{CompilationUnitId, CompilerEvent}
import org.jetbrains.plugins.scala.util.{CompilationId, ObjectSerialization}

import java.io.File
import java.util
import java.util.UUID
import scala.util.Try

abstract class IdeClient(compilerName: String,
                         context: CompileContext,
                         chunk: ModuleChunk) extends Client {

  import IdeClient._

  private var hasErrors = false
  private val compilationId: CompilationId = CompilationId.generate()
  private val compilationUnitId = Some(IdeClient.getCompilationUnitId(chunk))

  override def message(msg: Client.ClientMsg): Unit = {
    val Client.ClientMsg(kind, text, source, pointer, _, _) = msg
    if (kind == MessageKind.Error) {
      hasErrors = true
    }

    val name = if (source.isEmpty) compilerName else ""

    val sourcePath = source.map(file => file.getPath)
    val (line, column) = pointer match {
      case Some(PosInfo(line, column)) => (Some(line.toLong), Some(column.toLong))
      case None => (None, None)
    }

    if (LogFilter.shouldLog(kind, text, source, line, column)) {
      val jpsKind = kind match {
        case MessageKind.Error => BuildMessage.Kind.ERROR
        case MessageKind.Warning => BuildMessage.Kind.WARNING
        case MessageKind.Info => BuildMessage.Kind.INFO
        case MessageKind.Progress => BuildMessage.Kind.PROGRESS
        case MessageKind.JpsInfo => BuildMessage.Kind.JPS_INFO
        case MessageKind.InternalBuilderError => BuildMessage.Kind.INTERNAL_BUILDER_ERROR
        case MessageKind.Other => BuildMessage.Kind.OTHER
      }

      val uuid = Try {
        val cancelStatus = context.getCancelStatus
        val mySessionIdField = cancelStatus.getClass.getDeclaredField("mySessionId")
        mySessionIdField.setAccessible(true)
        mySessionIdField.get(cancelStatus).asInstanceOf[UUID]
      }.toOption

      // CompilerMessage expects 1-based line and column indices.
      context.processMessage(new CompilerMessage(name, jpsKind, text, sourcePath.orNull,
        -1L, -1L, -1L, line.getOrElse(-1L), column.getOrElse(-1L)))
      context.processMessage(CompilerEvent.MessageEmitted(compilationId, compilationUnitId, uuid, msg).toCustomMessage)
    }
  }

  override def compilationStart(): Unit = {
    context.processMessage(new ProgressMessage(JpsBundle.message("compiling.progress.message", chunk.getPresentableShortName)))
    context.processMessage(CompilerEvent.CompilationStarted(compilationId, compilationUnitId).toCustomMessage)
  }

  override def worksheetOutput(text: String): Unit = ()

  override def compilationPhase(name: String): Unit =
    context.processMessage(CompilerEvent.CompilationPhase(compilationId, compilationUnitId, name).toCustomMessage)

  override def compilationUnit(path: String): Unit =
    context.processMessage(CompilerEvent.CompilationUnit(compilationId, compilationUnitId, path).toCustomMessage)

  override def compilationEnd(sources: Set[File]): Unit =
    context.processMessage(CompilerEvent.CompilationFinished(compilationId, compilationUnitId, sources).toCustomMessage)

  override def processingEnd(): Unit = ()

  override def trace(exception: Throwable): Unit =
    context.processMessage(CompilerMessage.createInternalCompilationError(compilerName, exception))

  override def progress(@Nls text: String, done: Option[Float]): Unit = {
    // SCL-18190
//    for {
//      unitId <- compilationUnitId
//      doneVal <- done
//    } AggregateProgressLogger.log(context, unitId, doneVal)
    done.foreach { doneVal =>
      context.processMessage(CompilerEvent.ProgressEmitted(compilationId, compilationUnitId, doneVal).toCustomMessage)
    }
  }

  override def internalInfo(text: String): Unit =
    ScalaBuilder.Log.info(text)

  override def internalDebug(text: String): Unit =
    ScalaBuilder.Log.debug(text)

  override def internalTrace(text: String): Unit =
    ScalaBuilder.Log.trace(text)

  override def deleted(module: File): Unit = {
    val paths = util.Collections.singletonList(FileUtil.toCanonicalPath(module.getPath))
    context.processMessage(new FileDeletedEvent(paths))
  }

  override def metrics(value: CompileServerMetrics): Unit = ()

  override def isCanceled: Boolean = context.getCancelStatus.isCanceled

  def hasReportedErrors: Boolean = hasErrors
}

object IdeClient {

  private def getCompilationUnitId(chunk: ModuleChunk): CompilationUnitId = {
    val moduleBuildTarget = chunk.representativeTarget
    val moduleId = moduleBuildTarget.getModule.getName
    val testScope = moduleBuildTarget.isTests
    CompilationUnitId(
      moduleId = moduleId,
      testScope = testScope
    )
  }

  private[IdeClient] implicit class CompilerEventExt(private val compilerEvent: CompilerEvent) extends AnyVal {
    def toCustomMessage: CustomBuilderMessage = new CustomBuilderMessage(
      CompilerEvent.BuilderId,
      compilerEvent.eventType.toString,
      ObjectSerialization.toBase64(compilerEvent)
    )
  }
}
