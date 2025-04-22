package org.jetbrains.plugins.scala.conversion.copy.plainText

import com.intellij.core.CoreBundle
import com.intellij.ide.{IdeBundle, IdeView, PasteProvider}
import com.intellij.openapi.actionSystem.{ActionUpdateThread, DataContext, LangDataKeys, PlatformCoreDataKeys}
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages.showErrorDialog
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.util.IncorrectOperationException
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.conversion.ScalaConversionBundle
import org.jetbrains.plugins.scala.conversion.copy.plainText.ScalaFilePasteProvider._
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt, PsiMemberExt, ToNullSafe, inWriteCommandAction, startCommand}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.MethodInvocation
import org.jetbrains.plugins.scala.project.ModuleExt

import java.awt.datatransfer.DataFlavor
import java.nio.file.Path
import scala.jdk.CollectionConverters._
import scala.util.Try
import scala.util.control.NonFatal

/**
 * The class is responsible for pasting Scala/Worksheet/Sbt files in the Project View.
 * If the content which is pasted to Project View is a valid Scala code,
 * this class tries to calculate the best file name for the newly created Scala file which can be one of these:
 *  - regular file (*.scala)
 *  - worksheet file (*.sc), created when the code contains top-level expressions
 *  - plugins.sbt file when the content contains addSbtPlugin and is pasted to the build module root
 *
 * @note for a similar Java implementation see [[com.intellij.ide.JavaFilePasteProvider]]
 * @note the provider is used from [[com.intellij.ide.CopyPasteDelegator]]
 */
final class ScalaFilePasteProvider extends PasteProvider {

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT

  override def isPastePossible(dataContext: DataContext): Boolean = true

  override def isPasteEnabled(context: DataContext): Boolean = {
    val copyPasteManager = CopyPasteManager.getInstance
    if (copyPasteManager.copiedFiles.exists(_.nonEmpty))
      return false

    val isValidScalaFile: Option[Boolean] = for {
      _ <- context.maybeIdeView
      copiedText <- CopyPasteManager.getInstance.copiedText
      module <- context.maybeModuleWithScala
    } yield {
      PlainTextCopyUtil.looksLikeScalaFile(copiedText, module)
    }
    isValidScalaFile.contains(true)
  }

  override def performPaste(context: DataContext): Unit = {
    for {
      copiedText <- CopyPasteManager.getInstance.copiedText
      module <- context.maybeModuleWithScala
      directory <- context.maybeIdeView.flatMap(_.getOrChooseDirectory.toOption)
      fileName <- suggestedScalaFileNameForText(copiedText, module, Some(directory))
    } {
      createFileInDirectory(fileName, copiedText, directory)(module.getProject)
    }
  }

  @TestOnly
  def suggestedScalaFileNameForText(
    copiedText: String,
    module: Module,
    directory: Option[PsiDirectory] = None
  ): Option[FileNameWithExtension] = {
    for {
      scalaFile <- PlainTextCopyUtil.createScalaCodeFragment(copiedText, module)
    } yield {
      if (directory.exists(shouldCreatePluginsSbtFile(scalaFile, module, _))) {
        FileNameWithExtension("plugins", "sbt")
      }
      else {
        // Creates a file name that equals to the first top level member file name, otherwise a worksheet file.
        // If the file contains some package, it can't be a worksheet, so we always create a scala file
        val members = scalaFile.members
        val firstMemberName = members.headOption.flatMap(_.names.headOption)
        val firstMemberFileName = firstMemberName.map(FileNameWithExtension(_, "scala"))
        val regularScalaFileName = firstMemberFileName.orElse {
          if (scalaFile.firstPackaging.isDefined) Some(FileNameWithExtension("definitions", "scala"))
          else None
        }
        regularScalaFileName.getOrElse(FileNameWithExtension("worksheet", "sc"))
      }
    }
  }


  private def shouldCreatePluginsSbtFile(
    scalaFile: ScalaFile,
    module: Module,
    directory: PsiDirectory
  ): Boolean = {
    // true if we paste to the `project/` directory in a sbt project
    val isSbtBuildModuleRoot = module.isBuildModule && ModuleRootManager.getInstance(module).getSourceRoots.contains(directory.getVirtualFile)
    if (!isSbtBuildModuleRoot)
      return false

    val `has addSbtPlugin top-level call` = scalaFile.children.exists {
      case MethodInvocation((ref, _)) => ref.textMatches("addSbtPlugin")
      case _ => false
    }
    `has addSbtPlugin top-level call`
  }

  private def createFileInDirectory(
    fileNameAndExtension: FileNameWithExtension,
    fileText: String,
    targetPsiDir: PsiDirectory
  )(implicit project: Project): Unit = try {
    val FileNameWithExtension(name, extension) = fileNameAndExtension

    val allowCreateMultipleFilesWithSameName = extension == "sc" || extension == "sbt"
    // For some file types, allow creating multiple worksheets in the same directory:
    //  - worksheet.sc, worksheet1.sc, worksheet2.sc, etc...
    //  - plugins.sbt, plugins1.sbt
    val fileName: String =
      if (allowCreateMultipleFilesWithSameName) VfsUtil.getNextAvailableName(targetPsiDir.getVirtualFile, name, extension)
      else s"$name.$extension"

    //If the file with same name already exists ask the user whether s/he wants to replace it
    val existingFile = targetPsiDir.findFile(fileName)
    if (existingFile != null) {
      val dialog = MessageDialogBuilder.yesNo(
        IdeBundle.message("title.file.already.exists"),
        CoreBundle.message("prompt.overwrite.project.file", fileName, "")
      )
      val replaceExistingFile = dialog.ask(project)
      if (!replaceExistingFile) {
        return
      }
    }

    inWriteCommandAction {
      val psiFile =
        if (existingFile != null)
          existingFile.asInstanceOf[ScalaFile] //we are sure it's scala file because of `.scala` extension
        else {
          try targetPsiDir.createFile(fileName).asInstanceOf[ScalaFile]
          catch {
            case _: IncorrectOperationException =>
              return
          }
        }

      val documentManager = PsiDocumentManager.getInstance(project)

      val document = documentManager.getDocument(psiFile)
      if (document != null) {
        document.setText(fileText)
        documentManager.commitDocument(document)

        CodeStyleManager.getInstance(project).adjustLineIndent(psiFile, psiFile.getTextRange)
        documentManager.commitDocument(document)

        updatePackageStatement(psiFile, targetPsiDir)

        new OpenFileDescriptor(project, psiFile.getVirtualFile).navigate(true)
      }
    }
  } catch {
    case e: IncorrectOperationException =>
      //noinspection ReferencePassedToNls
      showErrorDialog(
        project,
        e.getMessage,
        ScalaConversionBundle.message("paste.error.title")
      )
    case NonFatal(ex) =>
      throw ex
  }

  private def updatePackageStatement(file: ScalaFile, targetDir: PsiDirectory)
                                    (implicit project: Project): Unit =
    startCommand(ScalaConversionBundle.message("updating.package.statement")) {
      Try {
        JavaDirectoryService
          .getInstance()
          .nullSafe
          .map(_.getPackage(targetDir))
          .map(_.getQualifiedName)
          .foreach(file.setPackageName)
      }
    }
}

object ScalaFilePasteProvider {

  case class FileNameWithExtension(name: String, extension: String) {
    def fullName: String = s"$name.$extension"
  }

  implicit class DataContextExt(private val context: DataContext) extends AnyVal {
    def maybeIdeView: Option[IdeView] = Option(LangDataKeys.IDE_VIEW.getData(context))

    private def maybeModule: Option[Module] = Option(PlatformCoreDataKeys.MODULE.getData(context))

    def maybeModuleWithScala: Option[Module] = maybeModule.filter(_.hasScala)
  }

  implicit class CopyPasteManagerExt(private val manager: CopyPasteManager) extends AnyVal {
    def copiedText: Option[String] =
      Option(manager.getContents[String](DataFlavor.stringFlavor))

    def copiedFiles: Option[Seq[Path]] =
      Option(manager.getContents[java.util.List[java.io.File]](DataFlavor.javaFileListFlavor)).map(_.asScala.map(_.toPath).toSeq)
  }
}
