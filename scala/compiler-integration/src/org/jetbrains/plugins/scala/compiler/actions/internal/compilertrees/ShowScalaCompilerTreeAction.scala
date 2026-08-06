package org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees

import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.compiler.CompilerIntegrationBundle
import org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.ui.CompilerTreesDialog
import org.jetbrains.plugins.scala.extensions.OptionExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

//TODO: (big feature) don't ignore last bytecode generation phase, show decompiled classes (1. bytecode and 2. as Java)
//TODO: (big improvement) implement folding (check how it's done for "Show Decompiled Code" action, if it even works for it)
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

    val treesGenerator = new CompilerTreesGenerator(virtualFile, module)

    val dialog = new CompilerTreesDialog(module.getProject, module, treesGenerator.getProgressIndicator)
    dialog.setTitle(CompilerIntegrationBundle.message("scala.compiler.trees.for", virtualFile.getName))
    dialog.show()

    // Start background compilation
    // (NOTE: it's important to do it after the dialog is shown in order in Tests, UI Interceptors can add an extra collecting listener)
    treesGenerator.addListener(dialog.compilerTreesListener)
    treesGenerator.runCompilationAndCollectTrees()
  }
}
