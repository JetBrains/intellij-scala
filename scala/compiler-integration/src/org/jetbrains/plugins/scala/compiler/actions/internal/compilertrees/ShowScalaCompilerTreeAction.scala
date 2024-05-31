package org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees

import com.intellij.notification.{NotificationType, NotificationsManager}
import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.jps.incremental.scala.remote.CommandIds
import org.jetbrains.jps.incremental.scala.{Client, DummyClient, MessageKind}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.ShowScalaCompilerTreeAction.compileFileAndGetCompilerMessages
import org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.ui.CompilerTreesDialog
import org.jetbrains.plugins.scala.compiler.{CompileServerLauncher, CompilerIntegrationBundle, RemoteServerConnectorBase, RemoteServerRunner}
import org.jetbrains.plugins.scala.extensions.{OptionExt, invokeLater, withProgressSynchronouslyTry}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettings
import org.jetbrains.plugins.scala.project.{ModuleExt, ScalaLanguageLevel}
import org.jetbrains.plugins.scala.util.ScalaNotificationGroups

import java.io.File
import scala.collection.mutable

//TODO: (big feature) don't ignore last bytecode generation phase, show decompiled classes (1. bytecode and 2. as Java)
//TODO: (big feature) add UI for extra Scala 3 options `-Xprint-*`
//TODO: (big feature) Scala 3 has option to print tasty, we can add it as well
//TODO: (big improvement) implement folding (check how it's done for "Show Decompiled Code" action, if it even works for it)
//TODO: show notifications on all steps (no file, no virtual file, no module, etc...)
//TODO: if not trees are parsed, show some notification (when there are still no errors)
final class ShowScalaCompilerTreeAction extends AnAction(CompilerIntegrationBundle.message("show.scala.compiler.trees.action.title")) {

  override def update(e: AnActionEvent): Unit = {
    val data = getDataRequiredForAction(e)
    e.getPresentation.setEnabledAndVisible(data.isDefined)
  }

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT

  private def getDataRequiredForAction(e: AnActionEvent): Option[ActionData] =
    for {
      scalaFile <- Option(e.getData(CommonDataKeys.PSI_FILE)).filterByType[ScalaFile]
      // - don't process compiled files
      // - only process scala sources, ignore play templates, sbt, worksheets, etc...
      if !scalaFile.isCompiled && scalaFile.getFileType == ScalaFileType.INSTANCE
      physicalVirtualFile <- Option(scalaFile.getVirtualFile)
      module <- scalaFile.module
    } yield ActionData(scalaFile, physicalVirtualFile, module)

  private case class ActionData(scalaFile: ScalaFile, virtualFile: VirtualFile, module: Module)

  override def actionPerformed(e: AnActionEvent): Unit = {
    val actionData = getDataRequiredForAction(e) match {
      case Some(d) => d
      case _ => return
    }

    compileFileAndShowDialogWithResults(actionData)
  }

  private def compileFileAndShowDialogWithResults(actionData: ActionData): Unit = {
    val virtualFile = actionData.virtualFile
    val module = actionData.module

    FileDocumentManager.getInstance.saveAllDocuments()

    withProgressSynchronouslyTry(CompilerIntegrationBundle.message("compiling.file", virtualFile.getName), canBeCanceled = true) { _ =>
      CompileServerLauncher.ensureServerRunning(module.getProject)

      val compilerMessages = compileFileAndGetCompilerMessages(virtualFile, module)

      val errors = compilerMessages.filter(_.kind == MessageKind.Error)
      val compilerTrees = CompilerTrees.parseFromCompilerMessages(
        compilerMessages,
        module.scalaLanguageLevel.getOrElse(ScalaLanguageLevel.Scala_2_13)
      )

      invokeLater {
        displayCompilationResults(
          virtualFile,
          module,
          errors,
          compilerTrees
        )
      }
    }
  }

  private def displayCompilationResults(
    virtualFile: VirtualFile,
    module: Module,
    errors: Seq[Client.ClientMsg],
    compilerTrees: CompilerTrees
  ): Unit = {
    val project = module.getProject
    if (errors.nonEmpty) {
      notifyAboutCompilationErrors(virtualFile, project, errors)
    }
    else if (compilerTrees.phasesTrees.isEmpty) {
      notifyNoCompilerTrees(project)
    }

    //Show some trees even if there is a compilation error
    val hasSomeNonEmptyTree = compilerTrees.phasesTrees.exists(_.treeText.nonEmpty)
    if (hasSomeNonEmptyTree || ApplicationManager.getApplication.isUnitTestMode) {
      showCompilerTreesDialog(virtualFile, module , compilerTrees)
    }
  }

  private def showCompilerTreesDialog(virtualFile: VirtualFile, module: Module, compilerTrees: CompilerTrees): Unit = {
    val dialog = new CompilerTreesDialog(module.getProject, module, compilerTrees)
    dialog.setTitle(CompilerIntegrationBundle.message("scala.compiler.trees.for", virtualFile.getName))
    dialog.show()
  }

  private def notifyAboutCompilationErrors(virtualFile: VirtualFile, project: Project, errors: Seq[Client.ClientMsg]): Unit = {
    val notification = ScalaNotificationGroups.scalaGeneral.createNotification(
      CompilerIntegrationBundle.message("compilation.error", virtualFile.getName),
      formatCompilerErrors(errors),
      NotificationType.ERROR
    )
    NotificationsManager.getNotificationsManager.showNotification(notification, project)
  }

  private def notifyNoCompilerTrees(project: Project): Unit = {
    val notification = ScalaNotificationGroups.scalaGeneral.createNotification(
      CompilerIntegrationBundle.message("couldn.t.parse.trees.from.the.compiler.output"),
      NotificationType.WARNING
    )
    NotificationsManager.getNotificationsManager.showNotification(notification, project)
  }

  private def formatCompilerErrors(errors: Seq[Client.ClientMsg]): String =
    errors.map(_.text).mkString("\n\n")
}

object ShowScalaCompilerTreeAction {

  private def compileFileAndGetCompilerMessages(virtualFile: VirtualFile, module: Module): Seq[Client.ClientMsg] = {
    val collectingClient = new ClientCollectingCompilerMessages

    val tempOutputDir = FileUtil.createTempDirectory("ShowScalaCompilerTreeAction", null, true)
    val serverConnector = new MyRemoteServerConnector(
      virtualFile,
      module,
      tempOutputDir,
      collectingClient
    )
    serverConnector.run()

    collectingClient.messages.toSeq
  }

  private class ClientCollectingCompilerMessages extends DummyClient() {
    val messages: mutable.Buffer[Client.ClientMsg] = mutable.ArrayBuffer.empty[Client.ClientMsg]

    override def message(msg: Client.ClientMsg): Unit = {
      messages += msg.copy(text = msg.text.replace("\r\n", "\n"))
    }
  }

  private class MyRemoteServerConnector(
    virtualFile: VirtualFile,
    module: Module,
    outputDir: File,
    client: Client
  ) extends RemoteServerConnectorBase(
    module,
    Some(Seq(virtualFile.toNioPath.toFile)),
    outputDir
  ) {
    override protected def compilerSettings: ScalaCompilerSettings = {
      val settings = super.compilerSettings
      val existingOptions = settings.additionalCompilerOptions
      //If there are already such compiler options on the project definition, ignore them, cause we are adding them manually
      val existingOptionsFiltered = existingOptions.filterNot { o =>
        o.startsWith("-Xprint:") || o.startsWith("-Yprint:") || //-Xprint: is alias to -Yprint
          o.startsWith("-Ystop-before:")
      }
      val scalacOptionsToPrintTrees = getScalacOptionsToPrintCompilerTrees(module.languageLevel)
      val optionsNew = existingOptionsFiltered ++ scalacOptionsToPrintTrees
      settings.copy(additionalCompilerOptions = optionsNew)
    }

    def run(): Unit = {
      new RemoteServerRunner()
        .buildProcess(CommandIds.Compile, arguments.asStrings, client)
        .runSync()
    }
  }

  private def getScalacOptionsToPrintCompilerTrees(languageLevel: Option[ScalaLanguageLevel]): Seq[String] =
    languageLevel match {
      case Some(value) if value.isScala3 =>
        Seq("-Vprint:all")
      case Some(ScalaLanguageLevel.Scala_2_13) =>
        //NOTE: before Scala 2.13.11 only `_` is recognised `all` is supported only since 2.13.11
        //We use `_` to support all scala 2.13.x versions
        Seq("-Vprint:_")
      case Some(value) if value < ScalaLanguageLevel.Scala_2_12 =>
        //NOTE: before 2.12 only `-Xprint` option exists, since 2.12 `-Xprint` is deprecated in favor of `-Vprint`
        //Even though `-Xprint` alias still exists in newer versions, we use non-deprecated option just in case
        //(see also https://github.com/scala/bug/issues/12737#issuecomment-1705606926)
        Seq("-Xprint:all")
      case _ =>
        Seq("-Vprint:all")
    }
}
