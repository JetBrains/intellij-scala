package org.jetbrains.plugins.scala.conversion.copy.plainText

import com.intellij.core.CoreBundle
import com.intellij.ide.{IdeBundle, IdeView, PasteProvider}
import com.intellij.openapi.actionSystem.{CommonDataKeys, DataContext, LangDataKeys, PlatformCoreDataKeys}
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages.showErrorDialog
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi._
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.conversion.ScalaConversionBundle
import org.jetbrains.plugins.scala.conversion.copy.plainText.ScalaFilePasteProvider._
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiMemberExt, ToNullSafe, inWriteCommandAction, startCommand}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.ModuleExt

import java.awt.datatransfer.DataFlavor
import java.io.File
import java.{util => ju}
import scala.jdk.CollectionConverters._
import scala.util.Try

final class ScalaFilePasteProvider extends PasteProvider {

  override def isPastePossible(dataContext: DataContext): Boolean = true

  override def isPasteEnabled(context: DataContext): Boolean = {
    val copyPasteManager = CopyPasteManager.getInstance
    context.maybeIdeView.isDefined &&
      copyPasteManager.copiedFiles.forall(_.isEmpty) &&
      context.maybeModule.exists(_.hasScala) && //don't affect NON scala projects even when scala plugin is turn on
      copyPasteManager.copiedText.zip(context.maybeProject).exists {
        case (text, project) =>
          PlainTextCopyUtil.isValidScalaFile(text)(project)
      }
  }

  override def performPaste(context: DataContext): Unit =
    for {
      text <- CopyPasteManager.getInstance.copiedText
      scalaFile <- context.maybeProject.flatMap(
        PlainTextCopyUtil.createScalaFile(text)(_)
      )
      directory <- context.maybeIdeView.flatMap(_.getOrChooseDirectory.toOption)
    } createFileInDirectory(fileNameAndExtension(scalaFile), text, directory)(
      scalaFile.getProject
    )

  private def createFileInDirectory(fileNameAndExtension: (String, String), fileText: String, targetPsiDir: PsiDirectory)
                                   (implicit project: Project): Unit =
    Try {
      inWriteCommandAction {
        val (name, extension) = fileNameAndExtension
        val isWorksheet = extension == "sc"
        //allow creating multiple worksheets in same directory
        val fileName: String =
          if (isWorksheet) VfsUtil.getNextAvailableName(targetPsiDir.getVirtualFile, name, extension)
          else s"$name.$extension"

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

        Option(documentManager.getDocument(psiFile)).foreach { document =>
          document.setText(fileText)
          documentManager.commitDocument(document)
          updatePackageStatement(psiFile, targetPsiDir)
          new OpenFileDescriptor(project, psiFile.getVirtualFile).navigate(true)
        }
      }
    }.recover { case e: IncorrectOperationException =>
      //noinspection ReferencePassedToNls
      showErrorDialog(
        project,
        e.getMessage,
        ScalaConversionBundle.message("paste.error.title")
      )
    }

  private def fileNameAndExtension(scalaFile: ScalaFile): (String, String) = {
    val firstMemberName = scalaFile.members.headOption.flatMap(_.names.headOption)
    firstMemberName
      .map((_, "scala"))
      .getOrElse(("worksheet", "sc"))
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

private object ScalaFilePasteProvider {

  implicit class DataContextExt(private val context: DataContext)
    extends AnyVal {
    def maybeIdeView: Option[IdeView] = Option(
      LangDataKeys.IDE_VIEW.getData(context)
    )
    def maybeProject: Option[Project] = Option(
      CommonDataKeys.PROJECT.getData(context)
    )
    def maybeModule: Option[Module] = Option(
      PlatformCoreDataKeys.MODULE.getData(context)
    )
  }

  implicit class CopyPasteManagerExt(private val manager: CopyPasteManager)
    extends AnyVal {
    def copiedText: Option[String] =
      Option(manager.getContents[String](DataFlavor.stringFlavor))

    def copiedFiles: Option[collection.Seq[File]] =
      Option(manager.getContents[ju.List[File]](DataFlavor.javaFileListFlavor))
        .map(_.asScala)
  }
}
