package org.jetbrains.jps.incremental.scala.remote

import com.intellij.openapi.util.io.{BufferExposingByteArrayOutputStream, FileUtil}
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.jps.api.{BuildType, CmdlineProtoUtil, GlobalOptions}
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.cmdline.{BuildRunner, JpsModelLoaderImpl, ProjectDescriptor}
import org.jetbrains.jps.incremental.fs.BuildFSState
import org.jetbrains.jps.incremental.messages.{BuildMessage, CompilerMessage, CustomBuilderMessage, ProgressMessage}
import org.jetbrains.jps.incremental.scala.{BuildParameters, Client, MessageKind, ScalaResourceBuilderEnabler}
import org.jetbrains.jps.incremental.{MessageHandler, Utils}
import org.jetbrains.plugins.scala.compiler.CompilerEvent.BuilderId
import org.jetbrains.plugins.scala.compiler.{CompilerEvent, CompilerEventType}
import org.jetbrains.plugins.scala.util.ObjectSerialization

import java.io.{DataOutputStream, FileNotFoundException, FileOutputStream, IOException}
import java.nio.file.{Files, Path}
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.{Lock, ReentrantLock}
import java.util.{Collections, UUID}
import scala.jdk.CollectionConverters.*
import scala.util.{Try, Using}

final class JpsFacadeImpl extends JpsFacade {
  private val systemRootSet: AtomicBoolean = new AtomicBoolean(false)

  private val projectLock: ConcurrentMap[Path, Lock] = ContainerUtil.createConcurrentSoftValueMap()

  def compileJpsLogic(command: CompileServerCommand.CompileJps, client: Client, jpsBuildSystemDir: Path): Unit = {
    if (systemRootSet.compareAndSet(false, true)) {
      Utils.setSystemRoot(jpsBuildSystemDir.toFile)
    }

    val CompileServerCommand.CompileJps(projectPath, globalOptionsPath, dataStorageRootPath, moduleNames, sourceScope, projectMetadata, externalProjectConfig) = command
    val lock = projectLock.computeIfAbsent(dataStorageRootPath, _ => new ReentrantLock())
    lock.lock()
    try {
      val loader = new JpsModelLoaderImpl(projectPath.toString, globalOptionsPath.toString, false, null)
      val buildRunner = new BuildRunner(loader)
      val customBuildId = UUID.randomUUID()
      val jpsProjectMetadata = projectMetadata.asCompactJsonString
      buildRunner.setBuilderParams(
        Map(
          BuildParameters.BuildTriggeredByCBH -> true.toString,
          BuildParameters.CustomBuildIdForCBH -> customBuildId.toString,
          BuildParameters.JpsProjectMetadataParameter -> jpsProjectMetadata
        ).asJava
      )
      var compiledFiles = Set.empty[Path]
      // Whether an error reached the client through the Scala builders' own events.
      var errorForwarded = false
      // The first error any builder reported as a plain CompilerMessage, Scala's included.
      var firstBuildError = Option.empty[CompilerMessage]
      val messageHandler = new MessageHandler {
        override def processMessage(msg: BuildMessage): Unit = msg match {
          case customMessage: CustomBuilderMessage =>
            fromCustomMessage(customMessage).foreach {
              case CompilerEvent.MessageEmitted(_, _, _, msg) =>
                if (msg.kind == MessageKind.Error || msg.kind == MessageKind.InternalBuilderError) errorForwarded = true
                client.message(msg)
              case CompilerEvent.CompilationFinished(_, _, sources) => compiledFiles ++= sources.map(_.toPath)
                // Forwarded for tracing only
              case CompilerEvent.CompilationPhase(_, _, name) => client.compilationPhase(name)
              case _ => ()
            }
          case progressMessage: ProgressMessage =>
            val text = Option(progressMessage.getMessageText).getOrElse("")
            val done = Option(progressMessage.getDone).filter(_ >= 0.0)
            client.progress(text, done)
          case compilerMessage: CompilerMessage if isError(compilerMessage) =>
            if (firstBuildError.isEmpty) firstBuildError = Some(compilerMessage)
          case _ =>
            ()
        }
      }

      if (client.isCanceled) return

      val fsState = new BuildFSState(true)
      val descriptor = withModifiedExternalProjectPath(externalProjectConfig) {
        buildRunner.load(messageHandler, dataStorageRootPath, fsState)
      }

      try {
        val buildTargetType = sourceScope match {
          case SourceScope.Production => JavaModuleBuildTargetType.PRODUCTION
          case SourceScope.Test => JavaModuleBuildTargetType.TEST
        }

        val scope = CmdlineProtoUtil.createTargetsScope(buildTargetType.getTypeId, moduleNames.asJava, false)

        if (client.isCanceled) return

        client.compilationStart()
        buildRunner.runBuild(
          descriptor,
          () => client.isCanceled,
          messageHandler,
          BuildType.BUILD,
          Collections.singletonList(scope),
          true
        )
      } finally {
        // Save the FS state data to disk, so that we do not corrupt the IDEA JPS process expected data.
        saveData(fsState, descriptor, descriptor.dataManager.getDataPaths.getDataStorageDir)
        reportBuildFailure(client, errorForwarded, firstBuildError)
        client.compilationEnd(compiledFiles)
        removeScalaResourceBuilderEnablersForBuild(customBuildId)
      }
    } finally {
      lock.unlock()
    }
  }

  private def isError(message: CompilerMessage): Boolean =
    message.getKind == BuildMessage.Kind.ERROR || message.getKind == BuildMessage.Kind.INTERNAL_BUILDER_ERROR

  /**
   * Tells the client that the build failed when only builders other than Scala's said so.
   *
   * The client learns about errors only from the Scala builders' events. Every other builder (Kotlin, Java, JPS
   * itself) reports through plain `CompilerMessage`s, which carry no Scala diagnostic. When one of them fails, JPS
   * stops before the remaining chunks, so the build must not be reported as successful: the document compiler
   * would then run against outputs that were never rebuilt.
   *
   * The failure is sent as one error without a source file. It flips the client's outcome without adding a
   * diagnostic to any file: what those builders report about their own files is not highlighted by CBH.
   * When a Scala error already reached the client, the outcome is already a failure and nothing is sent. The
   * Scala builders send every error both ways, so this also keeps Scala errors from being counted twice.
   */
  private def reportBuildFailure(client: Client, errorForwarded: Boolean, firstBuildError: Option[CompilerMessage]): Unit =
    if (!errorForwarded) firstBuildError.foreach { message =>
      val compilerName = message.getCompilerName
      val text = if (compilerName.trim.isEmpty) message.getMessageText else s"$compilerName: ${message.getMessageText}"
      client.error(text)
    }

  private def saveData(fsState: BuildFSState, descriptor: ProjectDescriptor, dataStorageRoot: Path): Unit = {
    try saveFsState(dataStorageRoot, fsState)
    finally descriptor.release()
  }

  /*
   * File name must match `org.jetbrains.jps.cmdline.BuildSession.FS_STATE_FILE` string constant. Unfortunately, it is
   * not publicly exposed and we cannot link directly to it.
   */
  private final val FsStateFile = "fs_state.dat"

  /**
   * This is a Scala translation of `org.jetbrains.jps.cmdline.BuildSession.saveFsState`.
   */
  private def saveFsState(dataStorageRoot: Path, state: BuildFSState): Unit = {
    val file = dataStorageRoot.resolve(FsStateFile)
    try {
      val bytes = new BufferExposingByteArrayOutputStream()
      Using.resource(new DataOutputStream(bytes)) { out =>
        // Use the latest FS State format version.
        out.writeInt(BuildFSState.VERSION)
        // Reset the fs event ordinal/counter. This forces the IDEA JPS process to re-check its FS state assumptions.
        out.writeLong(-1L)
        // Signal that there is work to do. This forces the IDEA JPS process to avoid the quick UP-TO-DATE optimization
        // and re-run all JPS builders to check that all targets are actually built. This avoids bugs like SCL-17303
        // where old bytecode is loaded in artifacts and run configurations.
        out.writeBoolean(true)
        state.save(out)
      }
      saveOnDisk(bytes, file)
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        try Files.deleteIfExists(file)
        catch {
          case _: IOException =>
        }
    }
  }

  /*
   * The next two functions are Scala translations of `org.jetbrains.jps.cmdline.BuildSession.saveOnDisk` and
   * `org.jetbrains.jps.cmdline.BuildSession.writeOrCreate`.
   */

  private def saveOnDisk(bytes: BufferExposingByteArrayOutputStream, file: Path): Unit = {
    Using.resource(writeOrCreate(file.toFile)) { fos =>
      fos.write(bytes.getInternalBuffer, 0, bytes.size())
    }
  }

  /**
   * This is a Scala translation of `org.jetbrains.jps.cmdline.BuildSession.writeOrCreate`. That function still uses
   * the `java.io.File` API, so we do too.
   */
  private def writeOrCreate(file: java.io.File): FileOutputStream =
    try new FileOutputStream(file)
    catch {
      case _: FileNotFoundException =>
        FileUtil.createIfDoesntExist(file)
        new FileOutputStream(file)
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
  private def withModifiedExternalProjectPath[T](externalProjectConfig: Option[Path])(body: => T): T = {
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
            System.setProperty(Key, value.toString)
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

  /**
   * Remove the registered Scala resource builder enablers to avoid memory leaks,
   * but only the ones from the current build.
   */
  private def removeScalaResourceBuilderEnablersForBuild(customBuildId: UUID): Unit = Try {
    import org.jetbrains.jps.incremental.resources.{ResourcesBuilder, StandardResourceBuilderEnabler}
    val resourcesBuilderClass = classOf[ResourcesBuilder]
    val ourEnablersField = resourcesBuilderClass.getDeclaredField("ourEnablers")
    ourEnablersField.setAccessible(true)
    val ourEnablers = ourEnablersField.get(null).asInstanceOf[java.util.List[StandardResourceBuilderEnabler]]
    ourEnablers.synchronized {
      val scalaEnablers = ourEnablers.asScala.filter {
        case e: ScalaResourceBuilderEnabler if e.customBuildId.contains(customBuildId) => true
        case _ => false
      }
      scalaEnablers.foreach(ourEnablers.remove)
    }
  }
}
