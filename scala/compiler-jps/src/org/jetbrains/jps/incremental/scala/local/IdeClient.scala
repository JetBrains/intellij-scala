package org.jetbrains.jps.incremental.scala
package local

import java.io.File
import java.util

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.jps.incremental.messages.{CompilerMessage, FileDeletedEvent, ProgressMessage}
import org.jetbrains.jps.incremental.scala.Client.PosInfo
import org.jetbrains.jps.incremental.scala.remote.{CompileServerMeteringInfo, CompileServerMetrics}
import org.jetbrains.plugins.scala.compiler.{CompilationUnitId, CompilerEvent}
import org.jetbrains.plugins.scala.util.CompilationId


/**
 * Nikolay.Tropin
 * 11/18/13
 */
abstract class IdeClient(compilerName: String,
                         context: CompileContext,
                         chunk: ModuleChunk) extends Client {

  private var hasErrors = false
  private val compilationId: CompilationId = CompilationId.generate()
  private val compilationUnitId = Some(IdeClient.getCompilationUnitId(chunk))

  override def message(msg: Client.ClientMsg): Unit = {
    val Client.ClientMsg(kind, text, source, PosInfo(line, column, _), _) = msg
    if (kind == Kind.ERROR) {
      hasErrors = true
    }

    val name = if (source.isEmpty) compilerName else ""

    val sourcePath = source.map(file => file.getPath)

    if (LogFilter.shouldLog(kind, text, source, line, column)) {
      context.processMessage(new CompilerMessage(name, kind, text, sourcePath.orNull,
        -1L, -1L, -1L, line.getOrElse(-1L), column.getOrElse(-1L)))
      context.processMessage(CompilerEvent.MessageEmitted(compilationId, compilationUnitId, msg).toCustomMessage)
    }
  }

  override def compilationStart(): Unit = {
    val progressMsg = JpsBundle.message("compiling.progress.message", chunk.getPresentableShortName) + "..."
    context.processMessage(new ProgressMessage(progressMsg))
    context.processMessage(CompilerEvent.CompilationStarted(compilationId, compilationUnitId).toCustomMessage)
  }

  override def compilationPhase(name: String): Unit =
    context.processMessage(CompilerEvent.CompilationPhase(compilationId, compilationUnitId, name).toCustomMessage)

  override def compilationUnit(path: String): Unit =
    context.processMessage(CompilerEvent.CompilationUnit(compilationId, compilationUnitId, path).toCustomMessage)

  override def compilationEnd(sources: Set[File]): Unit =
    context.processMessage(CompilerEvent.CompilationFinished(compilationId, compilationUnitId, sources).toCustomMessage)

  override def trace(exception: Throwable): Unit =
    context.processMessage(CompilerMessage.createInternalCompilationError(compilerName, exception))

  override def progress(text: String, done: Option[Float]): Unit = {
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

  override def deleted(module: File): Unit = {
    val paths = util.Collections.singletonList(FileUtil.toCanonicalPath(module.getPath))
    context.processMessage(new FileDeletedEvent(paths))
  }

  override def meteringInfo(info: CompileServerMeteringInfo): Unit = ()

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
}
