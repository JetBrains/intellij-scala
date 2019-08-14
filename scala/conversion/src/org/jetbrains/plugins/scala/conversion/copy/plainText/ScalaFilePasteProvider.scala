package org.jetbrains.plugins.scala.conversion.copy.plainText

import java.awt.datatransfer.DataFlavor

import com.intellij.ide.{IdeView, PasteProvider}
import com.intellij.openapi.actionSystem.{CommonDataKeys, DataContext, LangDataKeys}
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages.showErrorDialog
import com.intellij.psi._
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.conversion.copy.plainText.ScalaFilePasteProvider._
import org.jetbrains.plugins.scala.extensions.{ObjectExt, ToNullSafe, inWriteCommandAction, startCommand}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.ModuleExt

import scala.util.Try

final class ScalaFilePasteProvider extends PasteProvider {

  override def isPastePossible(dataContext: DataContext): Boolean = true

  override def isPasteEnabled(context: DataContext): Boolean = {
    context.maybeIdeView.isDefined &&
      context.maybeModule.exists(_.hasScala) && //don't affect NON scala projects even when scala plugin is turn on
      copiedContents.zip(context.maybeProject).exists {
        case (text, project) => PlainTextCopyUtil.isValidScalaFile(text)(project)
      }
  }

  override def performPaste(context: DataContext): Unit = {
    val text: String = CopyPasteManager.getInstance.getContents(DataFlavor.stringFlavor)

    for {
      scalaFile <- context.maybeProject.flatMap(PlainTextCopyUtil.createScalaFile(text)(_))
      directory <-  context.maybeIdeView.flatMap(_.getOrChooseDirectory.toOption)
    } {
      createFileInDirectory(fileName(scalaFile), text, directory)(scalaFile.getProject)
    }
  }

  private def createFileInDirectory(fileName: String, fileText: String, directory: PsiDirectory)
                                   (implicit project: Project) =
    Try {
      inWriteCommandAction {
        val file = directory.createFile(fileName).asInstanceOf[ScalaFile]
        val documentManager = PsiDocumentManager.getInstance(project)

        Option(documentManager.getDocument(file)).foreach { document =>
          document.setText(fileText)
          documentManager.commitDocument(document)
          updatePackageStatement(file, directory)
          new OpenFileDescriptor(project, file.getVirtualFile).navigate(true)
        }
      }
    }.recover {
      case e: IncorrectOperationException =>
        showErrorDialog(project, e.getMessage, "Paste")
    }

  private def fileName(scalaFile: ScalaFile): String =
    scalaFile.typeDefinitions.headOption.map(_.name).getOrElse("scriptFile") + ".scala"

  private def updatePackageStatement(file: ScalaFile, targetDir: PsiDirectory)
                                    (implicit project: Project): Unit =
    startCommand("Updating package statement") {
      Try {
        JavaDirectoryService.getInstance().nullSafe
          .map(_.getPackage(targetDir))
          .map(_.getQualifiedName)
          .foreach(file.setPackageName)
      }
    }
}

private object ScalaFilePasteProvider {

  import CommonDataKeys._
  import LangDataKeys._

  implicit class DataContextExt(private val context: DataContext) extends AnyVal {
    def maybeIdeView: Option[IdeView] = Option(IDE_VIEW.getData(context))
    def maybeProject: Option[Project] = Option(PROJECT.getData(context))
    def maybeModule: Option[Module] = Option(MODULE.getData(context))
  }

  private def copiedContents: Option[String] = Option(CopyPasteManager.getInstance.getContents(DataFlavor.stringFlavor))
}
