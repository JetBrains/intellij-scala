package org.jetbrains.jps.incremental.scala
package local

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.lang.JavaVersion
import org.jetbrains.annotations.Nls
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.messages.{BuildMessage, CompilerMessage, FileDeletedEvent, ProgressMessage}
import org.jetbrains.jps.incremental.scala.Client.PosInfo
import org.jetbrains.jps.incremental.scala.model.JpsSbtExtensionService
import org.jetbrains.jps.incremental.scala.remote.{CompileServerMetrics, SerializablePath}
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.plugins.scala.compiler.{CompilationUnitId, CompilerEvent}
import org.jetbrains.plugins.scala.util.CompilationId

import java.nio.file.Path
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
    val isErrorOrWarning = kind == MessageKind.Error || kind == MessageKind.Warning
    val textWithWarning =
      if (isErrorOrWarning && containsScalaJdkCompatibilityError(text)) {
        s"""Incompatible JDK version for Scala.
          |
          |The compiler has encountered an error that is likely caused by incompatible Scala and JDK versions.
          |Please check the official compatibility table: https://docs.scala-lang.org/overviews/jdk-compatibility/overview.html#scala-compatibility-table
          |You may need to update either your Scala or JDK version to resolve this issue.
          |
          |$text
          |""".stripMargin
      } else text

    if (kind == MessageKind.Error) {
      hasErrors = true
    }

    val name = if (source.isEmpty) compilerName else ""

    val sourcePath = source.map(_.toPath.toString)
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
    context.processMessage(new CompilerMessage(name, jpsKind, textWithWarning, sourcePath.orNull,
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

  override def compilationEnd(sources: Set[Path]): Unit =
    context.processMessage(new Base64BuilderMessage(CompilerEvent.CompilationFinished(compilationId, compilationUnitId, sources.map(SerializablePath(_)))))

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

  override def deleted(module: Path): Unit = {
    val paths = util.Collections.singletonList(FileUtil.toCanonicalPath(module.toAbsolutePath.normalize().toString))
    context.processMessage(new FileDeletedEvent(paths))
  }

  override def metrics(value: CompileServerMetrics): Unit = ()

  override def isCanceled: Boolean = context.getCancelStatus.isCanceled

  def hasReportedErrors: Boolean = hasErrors

  /**
   * Determines whether the given error text matches a known Scala/JDK incompatibility error pattern.
   *
   * @param text the error message text to analyze.
   */
  private def containsScalaJdkCompatibilityError(text: String): Boolean = {
    // Error indicating JDK incompatibility with Scala 2.11.x or 2.12.x
    val case1 = text.contains("scala.reflect.internal.MissingRequirementError: object java.lang.Object in compiler mirror not found") &&
      text.contains("scala.reflect.internal.MissingRequirementError$.signal")

    // Error indicating JDK incompatibility with Scala 2.12.x or 2.13.x
    val case2 = text.contains("scala.reflect.internal.FatalError") &&
      text.contains("bad constant pool index: 0 at") &&
      text.contains("scala.reflect.internal.Reporting.abort(Reporting.scala")

    // Error indicating JDK incompatibility with Scala 3
    val accessFlagPattern = text.contains("error while loading AccessFlag") && text.contains("class file /modules/java.base/java/lang/reflect/AccessFlag.class is broken")
    val elementTypePattern = text.contains("error while loading ElementType") && text.contains("class file /modules/java.base/java/lang/annotation/ElementType.class is broken")
    val case3 = (accessFlagPattern || elementTypePattern) && text.contains("bad constant pool index: 0 at")

    case1 || case2 || case3 || isScala3_8JdkVersionError(context, text)
  }

  /**
   * Checks whether the given text contains a known Scala 3.8 and JDK < 17 compatibility error pattern.
   */
  private def isScala3_8JdkVersionError(context: CompileContext, text: String): Boolean = {
    val global = context.getProjectDescriptor.getModel.getGlobal
    val compileServerSdk = SettingsManager.getGlobalSettings(global).getCompileServerSdk
    if (compileServerSdk == null) return false

    // Additional validation to be more sure the Scala/JDK incompatibility note is shown for the right combination
    val isBelow17 = Try(JavaVersion.parse(compileServerSdk)).toOption.exists(_.feature < 17)
    isBelow17 && text.contains("java.lang.UnsupportedClassVersionError: dotty/tools/xsbt/CompilerBridge has been compiled by a more recent version of the Java Runtime")
  }
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
