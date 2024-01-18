package org.jetbrains.jps.incremental.scala.remote

import com.intellij.openapi.util.io.{BufferExposingByteArrayOutputStream, FileUtil}
import org.jetbrains.jps.api.{BuildType, CmdlineProtoUtil, GlobalOptions}
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.cmdline.{BuildRunner, JpsModelLoaderImpl, ProjectDescriptor}
import org.jetbrains.jps.incremental.fs.BuildFSState
import org.jetbrains.jps.incremental.messages.{BuildMessage, CustomBuilderMessage, ProgressMessage}
import org.jetbrains.jps.incremental.scala.{BuildParameters, Client}
import org.jetbrains.jps.incremental.{MessageHandler, Utils}
import org.jetbrains.plugins.scala.compiler.CompilerEvent.BuilderId
import org.jetbrains.plugins.scala.compiler.{CompilerEvent, CompilerEventType}
import org.jetbrains.plugins.scala.util.ObjectSerialization

import java.io.{DataOutputStream, File, FileNotFoundException, FileOutputStream}
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import scala.jdk.CollectionConverters._
import scala.util.{Try, Using}

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
    buildRunner.setBuilderParams(Map(BuildParameters.BuildTriggeredByCBH -> true.toString).asJava)
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

    val fsState = new BuildFSState(true)
    val descriptor = withModifiedExternalProjectPath(externalProjectConfig) {
      buildRunner.load(messageHandler, dataStorageRoot, fsState)
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
      // Save the FS state data to disk, so that we do not corrupt the IDEA JPS process expected data.
      saveData(fsState, descriptor, descriptor.dataManager.getDataPaths.getDataStorageRoot)
      client.compilationEnd(compiledFiles)
    }
  }

  private def saveData(fsState: BuildFSState, descriptor: ProjectDescriptor, dataStorageRoot: File): Unit = {
    try saveFsState(fsState, dataStorageRoot)
    finally descriptor.release()
  }

  /*
   * File name must match `org.jetbrains.jps.cmdline.BuildSession.FS_STATE_FILE` string constant. Unfortunately, it is
   * not publicly exposed and we cannot link directly to it.
   */
  private final val FsStateFile = "fs_state.dat"

  private def saveFsState(fsState: BuildFSState, dataStorageRoot: File): Unit = {
    val file = new File(dataStorageRoot, FsStateFile)
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
        fsState.save(out)
      }
      saveOnDisk(bytes, file)
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        FileUtil.delete(file)
    }
  }

  /*
   * The next two functions are Scala translations of `org.jetbrains.jps.cmdline.BuildSession.saveOnDisk` and
   * `org.jetbrains.jps.cmdline.BuildSession.writeOrCreate`.
   */

  private def saveOnDisk(bytes: BufferExposingByteArrayOutputStream, file: File): Unit = {
    Using.resource(writeOrCreate(file)) { fos =>
      fos.write(bytes.getInternalBuffer, 0, bytes.size())
    }
  }

  private def writeOrCreate(file: File): FileOutputStream =
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
