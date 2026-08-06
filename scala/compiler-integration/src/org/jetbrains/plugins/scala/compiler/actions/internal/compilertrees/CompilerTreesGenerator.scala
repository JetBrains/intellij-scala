package org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees

import com.intellij.notification.{NotificationType, NotificationsManager}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.{EmptyProgressIndicator, ProgressIndicator}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.{RequiresBackgroundThread, RequiresEdt}
import org.jetbrains.annotations.Nls
import org.jetbrains.jps.incremental.scala.remote.CommandIds
import org.jetbrains.jps.incremental.scala.{Client, DummyClient, MessageKind}
import org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.phasesParser.PhaseCollector
import org.jetbrains.plugins.scala.compiler.{CompileServerLauncher, CompilerIntegrationBundle, EelPathTranslator, RemoteServerConnectorBase, RemoteServerRunner}
import org.jetbrains.plugins.scala.extensions.invokeLater
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettings
import org.jetbrains.plugins.scala.project.{ModuleExt, ScalaLanguageLevel}
import org.jetbrains.plugins.scala.util.ScalaNotificationGroups

import java.nio.file.Path
import scala.collection.mutable

private final class CompilerTreesGenerator(
  virtualFile: VirtualFile,
  module: Module
) {

  private val Log: Logger = Logger.getInstance(this.getClass)

  private var compilerTreesListener: CompilerTreesCollectionListener.Composite =
    new CompilerTreesCollectionListener.Composite()

  private val progressIndicator: ProgressIndicator = new EmptyProgressIndicator()

  /**
   * Returns the progress indicator used by the background compilation task.
   * It is used to connect UI components (e.g., progress dialogs) to cancellation and state updates.
   */
  def getProgressIndicator: ProgressIndicator = progressIndicator

  def addListener(listener: CompilerTreesCollectionListener): Unit = {
    compilerTreesListener = compilerTreesListener.withExtraListener(listener)
  }

  def runCompilationAndCollectTrees(): Unit = {
    ApplicationManager.getApplication.executeOnPooledThread(new Runnable {
      override def run(): Unit = {
        val listener = compilerTreesListener
        try {
          if (progressIndicator.isCanceled) {
            listener.collectionFinished()
            return
          }
          doRunCompilationAndCollectTrees(listener)
        } catch {
          case e: Exception =>
            invokeLater {
              //noinspection ReferencePassedToNls
              val message = Option(e.getMessage).getOrElse(e.getClass.getName)
              Notifications.notifyCompilationError(virtualFile, module.getProject, message)
            }
        }
      }
    })
  }

  @RequiresBackgroundThread
  private def doRunCompilationAndCollectTrees(
    listener: CompilerTreesCollectionListener.Composite
  ): Unit = {
    Log.info(s"Compiler trees collection started for ${virtualFile.getPath}")

    val phaseCollectorListener = new CompilerTreesCollectionListener.Collecting
    val compilerTreesWithCollector = listener.withExtraListener(phaseCollectorListener)
    try {
      CompileServerLauncher.ensureServerRunning(module.getProject)
      val errors = compileFileAndCollectPhases(compilerTreesWithCollector)

      if (progressIndicator.isCanceled) {
        return
      }

      // Show notifications about errors or non-existing trees if needed
      if (errors.nonEmpty) {
        invokeLater {
          Notifications.notifyAboutCompilationErrors(virtualFile, module.getProject, errors)
        }
      } else if (phaseCollectorListener.collectedPhases.isEmpty) {
        invokeLater {
          Notifications.notifyNoCompilerTrees(module.getProject)
        }
      }
    } finally {
      compilerTreesWithCollector.collectionFinished()
      if (progressIndicator.isCanceled) {
        Log.info(s"Compiler trees collection canceled for ${virtualFile.getPath}")
      } else {
        Log.info(s"Compiler trees collection finished for ${virtualFile.getPath}")
      }
    }
  }

  @RequiresBackgroundThread
  private def compileFileAndCollectPhases(
    compilerTrees: CompilerTreesCollectionListener.Composite
  ): Seq[Client.ClientMsg] = {
    val collectingClient = new ClientCollectingAndStreamingMessages(
      module.scalaLanguageLevel.getOrElse(ScalaLanguageLevel.Scala_2_13),
      compilerTrees,
      progressIndicator
    )

    val tempOutputDir = FileUtil.createTempDirectory("ShowScalaCompilerTreeAction", null, true).toPath
    val serverConnector = new MyRemoteServerConnector(
      virtualFile,
      module,
      tempOutputDir,
      collectingClient
    )
    try {
      serverConnector.run()
    } finally {
      // Ensure the collector flushes any pending phases even if compilation fails
      collectingClient.finish()
    }

    collectingClient.errors.toSeq
  }

  private object Notifications {
    @RequiresEdt
    def notifyAboutCompilationErrors(virtualFile: VirtualFile, project: Project, errors: Seq[Client.ClientMsg]): Unit = {
      notifyCompilationError(virtualFile, project, formatCompilerErrors(errors))
    }

    @RequiresEdt
    def notifyCompilationError(virtualFile: VirtualFile, project: Project, @Nls message: String): Unit = {
      val notification = ScalaNotificationGroups.scalaGeneral.createNotification(
        CompilerIntegrationBundle.message("compilation.error", virtualFile.getName),
        message,
        NotificationType.ERROR
      )
      NotificationsManager.getNotificationsManager.showNotification(notification, project)
    }

    @RequiresEdt
    def notifyNoCompilerTrees(project: Project): Unit = {
      val notification = ScalaNotificationGroups.scalaGeneral.createNotification(
        CompilerIntegrationBundle.message("could.not.parse.trees.from.the.compiler.output"),
        NotificationType.WARNING
      )
      NotificationsManager.getNotificationsManager.showNotification(notification, project)
    }

    private def formatCompilerErrors(errors: Seq[Client.ClientMsg]): String =
      errors.map(_.text).mkString("\n\n")
  }

  /**
   * Client that collects compiler messages and streams phases in real-time.
   */
  private class ClientCollectingAndStreamingMessages(
    languageLevel: ScalaLanguageLevel,
    compilerTreesListener: CompilerTreesCollectionListener.Composite,
    progressIndicator: ProgressIndicator
  ) extends DummyClient() {
    val errors: mutable.Buffer[Client.ClientMsg] = mutable.ArrayBuffer.empty[Client.ClientMsg]

    private val phaseCollector = new PhaseCollector(
      languageLevel,
      phase => {
        Log.debug(s"Compiler trees phase collected: ${phase.phaseName} [${phase.phaseKind}]")
        compilerTreesListener.phaseAdded(phase)
      }
    )

    override def message(msg: Client.ClientMsg): Unit = {
      val normalizedMsg = msg.copy(text = msg.text.replace("\r\n", "\n"))

      // Collect errors for later notification
      if (normalizedMsg.kind == MessageKind.Error) {
        errors += normalizedMsg
      }

      // Process message for phase detection
      phaseCollector.processMessage(normalizedMsg)
    }

    override def isCanceled: Boolean = progressIndicator.isCanceled

    def finish(): Unit = {
      phaseCollector.finish()
    }
  }

  private class MyRemoteServerConnector(
    virtualFile: VirtualFile,
    module: Module,
    outputDir: Path,
    client: Client
  ) extends RemoteServerConnectorBase(
    module,
    Some(Seq(virtualFile.toNioPath)),
    outputDir
  ) {
    override protected def compilerSettings: ScalaCompilerSettings = {
      val settings = super.compilerSettings
      val existingOptions = settings.additionalCompilerOptions
      //If there are already such compiler options on the project definition, ignore them, cause we are adding them manually
      val existingOptionsFiltered = existingOptions.filterNot { o =>
        o.startsWith("-Xprint:") || //-Xprint: is alias to -Yprint
          o.startsWith("-Yprint:") ||
          o.startsWith("-Vprint:") ||
          o.startsWith("-Xprint-types") ||
          o.startsWith("-Xprint-diff") ||
          o.startsWith("-Ystop-before:")
      }
      val scalacOptionsToPrintTrees = getScalacOptionsToPrintCompilerTrees(module.languageLevel)
      val optionsNew = existingOptionsFiltered ++ scalacOptionsToPrintTrees
      settings.copy(
        // remove duplicates in case those options were already in the build, just to avoid redundant warnings from the compiler
        additionalCompilerOptions = optionsNew.distinct
      )
    }

    def run(): Unit = {
      new RemoteServerRunner(module.getProject)
        .buildProcess(CommandIds.Compile, arguments.asStrings(EelPathTranslator), client)
        .runSync()
    }
  }

  private def getScalacOptionsToPrintCompilerTrees(languageLevel: Option[ScalaLanguageLevel]): Seq[String] =
    languageLevel match {
      case Some(value) if value.isScala3 =>
        // Print both phases and Tasty
        Seq("-Vprint:all", "-Yprint-tasty")
      case Some(ScalaLanguageLevel.Scala_2_13) =>
        //NOTE: before Scala 2.13.11 only `_` is recognised `all` is supported only since 2.13.11
        //We use `_` to support all scala 2.13.x versions
        Seq("-Vprint:_")
      case Some(value) if value < ScalaLanguageLevel.Scala_2_12 =>
        //NOTE: before 2.12 only `-Xprint` option exists, since 2.12 `-Xprint` is deprecated in favor of `-Vprint`
        //Even though `-Xprint` alias still exists in newer versions, we use a non-deprecated option just in case
        //(see also https://github.com/scala/bug/issues/12737#issuecomment-1705606926)
        Seq("-Xprint:all")
      case _ =>
        Seq("-Vprint:all")
    }
}
