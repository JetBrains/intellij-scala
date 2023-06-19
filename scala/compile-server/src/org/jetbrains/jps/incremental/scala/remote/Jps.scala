package org.jetbrains.jps.incremental.scala.remote

import org.jetbrains.jps.api.{BuildType, CmdlineProtoUtil, GlobalOptions}
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.cmdline.{BuildRunner, JpsModelLoaderImpl}
import org.jetbrains.jps.incremental.{MessageHandler, Utils}
import org.jetbrains.jps.incremental.fs.BuildFSState
import org.jetbrains.jps.incremental.messages.{BuildMessage, CustomBuilderMessage, ProgressMessage}
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.plugins.scala.compiler.{CompilerEvent, CompilerEventType}
import org.jetbrains.plugins.scala.compiler.CompilerEvent.BuilderId
import org.jetbrains.plugins.scala.util.ObjectSerialization

import java.io.File
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import scala.jdk.CollectionConverters._
import scala.util.Try

private object Jps {
  private val systemRootSet: AtomicBoolean = new AtomicBoolean(false)

  def compileJpsLogic(command: CompileServerCommand.CompileJps, client: Client, scalaCompileServerSystemDir: Path): Unit = {
    if (systemRootSet.compareAndSet(false, true)) {
      Utils.setSystemRoot(scalaCompileServerSystemDir.toFile)
    }

    val CompileServerCommand.CompileJps(projectPath, globalOptionsPath, dataStorageRootPath, moduleName, sourceScope, externalProjectConfig) = command
    val dataStorageRoot = new File(dataStorageRootPath)
    val loader = new JpsModelLoaderImpl(projectPath, globalOptionsPath, false, null)
    val buildRunner = new BuildRunner(loader)
    var compiledFiles = Set.empty[File]
    val messageHandler = new MessageHandler {
      override def processMessage(msg: BuildMessage): Unit = msg match {
        case customMessage: CustomBuilderMessage =>
          fromCustomMessage(customMessage).foreach {
            case CompilerEvent.MessageEmitted(_, _, _, msg) => client.message(msg)
            case CompilerEvent.CompilationFinished(_, _, sources) => compiledFiles ++= sources
            case _ => ()
          }
        case progressMessage: ProgressMessage =>
          val text = Option(progressMessage.getMessageText).getOrElse("")
          val done = Option(progressMessage.getDone).filter(_ >= 0.0)
          client.progress(text, done)
        case _ =>
          ()
      }
    }
    val descriptor = withModifiedExternalProjectPath(externalProjectConfig) {
      buildRunner.load(messageHandler, dataStorageRoot, new BuildFSState(true))
    }

    val buildTargetType = sourceScope match {
      case SourceScope.Production => JavaModuleBuildTargetType.PRODUCTION
      case SourceScope.Test => JavaModuleBuildTargetType.TEST
    }

    val scopes = Seq(
      CmdlineProtoUtil.createTargetsScope(buildTargetType.getTypeId, Seq(moduleName).asJava, false)
    ).asJava

    client.compilationStart()
    try {
      buildRunner.runBuild(
        descriptor,
        () => client.isCanceled,
        messageHandler,
        BuildType.BUILD,
        scopes,
        true
      )
    } finally {
      client.compilationEnd(compiledFiles)
      descriptor.release()
    }
  }

  private val ExternalProjectConfigPropertyLock = new Object

  /**
   * In case project configuration is stored externally (outside `.idea` folder) we need to provide the path to the external storage.
   *
   * @see `org.jetbrains.jps.model.serialization.JpsProjectLoader.loadFromDirectory`
   * @see [[org.jetbrains.jps.model.serialization.JpsProjectLoader.resolveExternalProjectConfig]]
   * @see [[org.jetbrains.jps.api.GlobalOptions.EXTERNAL_PROJECT_CONFIG]]
   * @see `com.intellij.compiler.server.BuildManager.launchBuildProcess`
   * @see `org.jetbrains.plugins.scala.compiler.highlighting.IncrementalCompiler.compile`
   */
  private def withModifiedExternalProjectPath[T](externalProjectConfig: Option[String])(body: => T): T = {
    externalProjectConfig match {
      case Some(value) =>
        //NOTE: We have use lock here because currently we can only pass the external project config path via System.get/setProperty
        //This can lead to issues when incremental compilation is triggered for several projects which use compiler-based highlighting
        //This is because Scala Compiler Server is currently reused between all projects and System.get/setProperty modifies global JVM state.
        //TODO: Ideally we would need some way to pass the value to JpsProjectLoader more transparently
        ExternalProjectConfigPropertyLock.synchronized {
          val Key = GlobalOptions.EXTERNAL_PROJECT_CONFIG
          val previousValue = System.getProperty(Key)
          try {
            System.setProperty(Key, value)
            body
          }
          finally {
            if (previousValue == null)
              System.clearProperty(Key)
            else
              System.setProperty(Key, previousValue)
          }
        }
      case _ =>
        body
    }
  }

  // Duplicated in org.jetbrains.plugins.scala.compiler.CompilerEventFromCustomBuilderMessageListener
  // to avoid complex compile time dependencies between modules.
  private def fromCustomMessage(customMessage: CustomBuilderMessage): Option[CompilerEvent] = {
    val text = customMessage.getMessageText
    Option(customMessage)
      .filter(_.getBuilderId == BuilderId)
      .flatMap(msg => Try(CompilerEventType.withName(msg.getMessageType)).toOption)
      .map(_ => ObjectSerialization.fromBase64[CompilerEvent](text))
  }
}
