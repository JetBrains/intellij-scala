package org.jetbrains.plugins.scala.semantic

import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.{DiffContentFactory, DiffManager}
import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.{FileEditorManager, FileEditorOpenRequest}
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.{CompilerModuleExtension, LibraryOrderEntry, ModuleRootManager, ProjectFileIndex}
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiClass
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.plugins.scala.ScalaBundle.message
import org.jetbrains.plugins.scala.actions.ScalaActionUtil
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, inReadAction, withProgressSynchronously}
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.project.ProjectPsiFileExt
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.getInstance as ScalaApplicationSettings
import org.jetbrains.plugins.scala.statistics.ScalaActionUsagesCollector
import org.jetbrains.plugins.scala.{LanguageFileTypeBase, Scala3Language}

import java.nio.file.Files
import javax.swing.Icon
import scala.jdk.CollectionConverters.CollectionHasAsScala

class ElaborateCodeAction extends AnAction(message("elaborate.code.action.text"), message("elaborate.code.action.description"), /* icon = */ null) {
  override def getActionUpdateThread: ActionUpdateThread =
    ActionUpdateThread.BGT

  override def update(e: AnActionEvent): Unit =
    ScalaActionUtil.enableAndShowIfInScalaFile(e)

  override def actionPerformed(e: AnActionEvent): Unit = {
    ScalaActionUsagesCollector.logDesugarCode(e.getProject)

    val project = e.getProject
    val editor = CommonDataKeys.EDITOR.getData(e.getDataContext)
    val document = editor.getDocument
    val virtualFile = editor.getVirtualFile
    val psiFile = CommonDataKeys.PSI_FILE.getData(e.getDataContext).asInstanceOf[ScalaFile]
    if (psiFile.isCompiled) {
      CommonRefactoringUtil.showErrorHint(project, editor, message("elaborate.code.no.source"), getTemplateText, null)
      return
    }
    val cls = classAtCaret(editor, psiFile).orElse(psiFile.typeDefinitions.headOption).getOrElse {
      CommonRefactoringUtil.showErrorHint(project, editor, message("elaborate.code.no.class"), getTemplateText, null)
      return
    }

    val isScala3 = cls.isInScala3File

    val fileIndex = ProjectFileIndex.getInstance(project)

    val (tastyFileName, tastyFileContents, upToDate, classpath) = if (!isScala3) ("", Array.emptyByteArray, true, Seq.empty[String]) else if (fileIndex.isInSourceContent(virtualFile)) {
      val module = psiFile.module.getOrElse(throw new RuntimeException(s"No module for $psiFile"))
      val outputDir =
        if (fileIndex.isInTestSourceContent(virtualFile)) CompilerModuleExtension.getInstance(module).getCompilerOutputPathForTests.toNioPath
        else CompilerModuleExtension.getInstance(module).getCompilerOutputPath.toNioPath
      val tastyFile = {
        val elements = (cls: PsiClass).getQualifiedName.split('.').toSeq.dropRight(1) :+ (cls: PsiClass).getName.stripSuffix("$") + ".tasty"
        elements.foldLeft(outputDir)((acc, x) => acc.resolve(x))
      }
      if (!Files.exists(tastyFile)) {
        CommonRefactoringUtil.showErrorHint(project, editor, message("elaborate.code.no.file", outputDir.relativize(tastyFile)), getTemplateText, null)
        return
      }
      val upToDate = document.getModificationStamp <= virtualFile.getModificationStamp && virtualFile.getTimeStamp <= Files.getLastModifiedTime(tastyFile).toMillis
      (tastyFile.getFileName.toString, Files.readAllBytes(tastyFile), upToDate, classpathOf(module))
    } else {
      val compiledPsiFile = cls.getOriginalElement.getContainingFile
      val compiledVirtualFile = compiledPsiFile.getOriginalFile.getVirtualFile
      val module = fileIndex.getOrderEntriesForFile(compiledVirtualFile).asScala.collectFirst { case entry: LibraryOrderEntry => entry.getOwnerModule }.getOrElse {
        CommonRefactoringUtil.showErrorHint(project, editor, message("elaborate.code.no.module"), getTemplateText, null)
        return
      }
      (compiledVirtualFile.getName, compiledVirtualFile.contentsToByteArray(), true, classpathOf(module))
    }

    val (compilerText, pluginText) = withProgressSynchronously(message("elaborate.code.elaborating", cls.name)) {
      val compilerText = if (!isScala3) "" else {
        val decompiler = Decompiler(classpath, Decompiler.classLoader(getClass.getClassLoader))
        decompiler.decompile(tastyFileName, tastyFileContents.toIndexedSeq)
      }
      val pluginText = inReadAction {
        try {
          ScalaApplicationSettings.PRECISE_TEXT = true
          ScalaApplicationSettings.PRECISE_TEXT_FOR_TYPE_PARAMETERS = isScala3
          ClassPrinter.textOf(cls, isScala3 = isScala3)
        } finally {
          ScalaApplicationSettings.PRECISE_TEXT = false
          ScalaApplicationSettings.PRECISE_TEXT_FOR_TYPE_PARAMETERS = false
        }
      }
      (compilerText, pluginText)
    }

    if (!isScala3) {
      val file = new LightVirtualFile(message("elaborate.code.elaborated", cls.name), psiFile.getFileType, pluginText)
      FileEditorManager.getInstance(project).requestOpenFile(file, FileEditorOpenRequest.withFocus(true))
    } else {
      val leftTitle = s"${message("elaborate.code.compiler")}${if (upToDate) "" else s" (${message("elaborate.code.outdated")})"}"
      val rightTitle = s"${message("elaborate.code.plugin")}"
      val left = DiffContentFactory.getInstance.create(project, compilerText, Scala3FileType)
      val right = DiffContentFactory.getInstance.create(project, pluginText, Scala3FileType)
      DiffManager.getInstance.showDiff(project, new SimpleDiffRequest(message("elaborate.code.elaboration", cls.qualifiedName), left, right, leftTitle, rightTitle))
    }
  }

  private def classpathOf(module: Module): Seq[String] =
    ModuleRootManager.getInstance(module)
      .orderEntries.withoutSdk.classes.getRoots.toSeq
      .map(VfsUtil.getLocalFile(_).getPath)

  private def classAtCaret(editor: Editor, file: ScalaFile): Option[ScTypeDefinition] = {
    val offset = editor.getCaretModel.getOffset
    val elementAtCaret = Option(file.findElementAt(offset))
    val classAtCaret = elementAtCaret.flatMap(_.contexts.collectFirst { case td: ScTypeDefinition if td.isTopLevel => td })
    classAtCaret
  }

  private object Scala3FileType extends LanguageFileTypeBase(Scala3Language.INSTANCE) {
    override def getIcon: Icon = Icons.SCALA_FILE
  }
}
