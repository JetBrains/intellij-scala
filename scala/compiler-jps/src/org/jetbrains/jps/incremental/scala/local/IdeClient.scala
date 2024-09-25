package org.jetbrains.jps.incremental.scala
package local

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.messages.{BuildMessage, CompilerMessage, FileDeletedEvent, ProgressMessage}
import org.jetbrains.jps.incremental.scala.Client.PosInfo
import org.jetbrains.jps.incremental.scala.model.JpsSbtExtensionService
import org.jetbrains.jps.incremental.scala.remote.CompileServerMetrics
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.plugins.scala.compiler.{CompilationUnitId, CompilerEvent}
import org.jetbrains.plugins.scala.util.CompilationId

import java.io.File
import java.util
import java.util.UUID
import scala.collection.immutable
import scala.util.Try

abstract class IdeClient(compilerName: String,
                         context: CompileContext,
                         chunk: ModuleChunk) extends Client {

  private var hasErrors = false
  private val compilationId: CompilationId = CompilationId(timestamp = System.nanoTime(), documentVersions = immutable.HashMap.empty)
  private val compilationUnitId = Some(IdeClient.getCompilationUnitId(chunk))

  override def message(msg: Client.ClientMsg): Unit = {
    val Client.ClientMsg(kind, text, source, pointer, _, _, _) = msg
    if (kind == MessageKind.Error) {
      hasErrors = true
    }

    val name = if (source.isEmpty) compilerName else ""

    val sourcePath = source.map(file => file.getPath)
    val (line, column) = pointer match {
      case Some(PosInfo(line, column)) => (Some(line.toLong), Some(column.toLong))
      case None => (None, None)
    }

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
    context.processMessage(new Base64BuilderMessage(CompilerEvent.MessageEmitted(compilationId, compilationUnitId, uuid, msg)))
  }

  override def compilationStart(): Unit = {
    context.processMessage(new ProgressMessage(JpsBundle.message("compiling.progress.message", chunk.getPresentableShortName)))
    context.processMessage(new Base64BuilderMessage(CompilerEvent.CompilationStarted(compilationId, compilationUnitId)))
  }

  override def worksheetOutput(text: String): Unit = ()

  override def compilationPhase(name: String): Unit =
    context.processMessage(new Base64BuilderMessage(CompilerEvent.CompilationPhase(compilationId, compilationUnitId, name)))

  override def compilationUnit(path: String): Unit =
    context.processMessage(new Base64BuilderMessage(CompilerEvent.CompilationUnit(compilationId, compilationUnitId, path)))

  override def compilationEnd(sources: Set[File]): Unit =
    context.processMessage(new Base64BuilderMessage(CompilerEvent.CompilationFinished(compilationId, compilationUnitId, sources)))

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
      context.processMessage(new Base64BuilderMessage(CompilerEvent.ProgressEmitted(compilationId, compilationUnitId, doneVal)))
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
    val moduleId = getDisplayModuleNameIfApplicable(moduleBuildTarget.getModule)
    val testScope = moduleBuildTarget.isTests
    CompilationUnitId(
      moduleId = moduleId,
      testScope = testScope
    )
  }

  private def getDisplayModuleNameIfApplicable(module: JpsModule): String =
    if (shouldUseDisplayModuleNames) getDisplayModuleName(module)
    else module.getName

  private def getDisplayModuleName(module: JpsModule): String = {
    val service = JpsSbtExtensionService.getInstance
    val sbtModuleExtension = service.getExtension(module)
    val displayName = sbtModuleExtension.flatMap(_.getDisplayModuleName)
    displayName match {
      case Some(name) => name
      case _ =>
        val moduleName = module.getName
        ScalaBuilder.Log.info(s"Couldn't find display module name for module $moduleName")
        moduleName
    }
  }

  private def shouldUseDisplayModuleNames: Boolean =
    Option(System.getProperty("use.module.display.name")).flatMap(_.toBooleanOption).getOrElse(false)
}
