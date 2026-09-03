package org.jetbrains.plugins.scala.semantic

import com.intellij.diff.{DiffContentFactory, DiffManager}
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.roots.{CompilerModuleExtension, ModuleRootManager}
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.ProjectPsiFileExt
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}
import org.jetbrains.plugins.scala.{Scala3Language, ScalaBundle}

class DesugarCodeAction extends AnAction(
  ScalaBundle.message("desugar.scala.code.action.text"),
  ScalaBundle.message("desugar.scala.code.action.description"),
  /* icon = */null) {

  override def actionPerformed(e: AnActionEvent): Unit = {
    val project = e.getProject
    val file = CommonDataKeys.PSI_FILE.getData(e.getDataContext).asInstanceOf[ScalaFile]
    val module = file.module.orNull
    val cls = file.typeDefinitions.head

    val outputDir = CompilerModuleExtension.getInstance(module).getCompilerOutputPath

    val compilerText = {
      val decompiler = {
        val classpath =
          ModuleRootManager.getInstance(module)
            .orderEntries.productionOnly.librariesOnly.classes.getRoots.toSeq
            .map(virtualFile => VfsUtil.getLocalFile(virtualFile).getPath)
        Decompiler(classpath :+ outputDir.getPath, Decompiler.classLoader(getClass.getClassLoader))
      }

      val tastyFile = {
        val tastyFilePath = cls.qualifiedName.replace('.', '/') + ".tasty"
        outputDir.findFileByRelativePath(tastyFilePath)
      }

      decompiler.decompile(tastyFile.getName, tastyFile.contentsToByteArray())
    }

    val pluginText = try {
      ScalaApplicationSettings.PRECISE_TEXT = true
      ScalaApplicationSettings.PRECISE_TEXT_FOR_TYPE_PARAMETERS = true
      ClassPrinter.textOf(cls)
    } finally {
      ScalaApplicationSettings.PRECISE_TEXT = false
      ScalaApplicationSettings.PRECISE_TEXT_FOR_TYPE_PARAMETERS = false
    }

    val left = DiffContentFactory.getInstance.create(project, compilerText, Scala3Language.INSTANCE.getAssociatedFileType)
    val right = DiffContentFactory.getInstance.create(project, pluginText, Scala3Language.INSTANCE.getAssociatedFileType)
    DiffManager.getInstance.showDiff(project, new SimpleDiffRequest("Desugaring of " + cls.qualifiedName, left, right, "Compiler", "Plugin"))
  }
}
