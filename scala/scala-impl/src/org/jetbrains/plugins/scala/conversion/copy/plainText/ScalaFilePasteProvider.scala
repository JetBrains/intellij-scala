package org.jetbrains.plugins.scala.conversion.copy.plainText

import java.awt.datatransfer.DataFlavor.stringFlavor

import com.intellij.ide.{IdeView, PasteProvider}
import com.intellij.openapi.actionSystem.LangDataKeys.{IDE_VIEW, MODULE}
import com.intellij.openapi.actionSystem.{CommonDataKeys, DataContext}
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages.showErrorDialog
import com.intellij.psi._
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.extensions.{ObjectExt, startCommand}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.ModuleExt

import scala.util.Try

/**
  * Created by Kate Ustyuzhanina on 12/13/16.
  */

class ScalaFilePasteProvider extends PasteProvider {

  import ScalaFilePasteProvider._

  def fileName(scalaFile: ScalaFile): String =
    scalaFile.typeDefinitions.headOption.map(_.name).getOrElse("scriptFile") + ".scala"

  override def performPaste(dataContext: DataContext): Unit = {
    val text: String = CopyPasteManager.getInstance.getContents(stringFlavor)
    implicit val context = dataContext

    maybeProject.flatMap(PlainTextCopyUtil.createScalaFile(text, _)).zip {
      maybeIdeView.flatMap(_.getOrChooseDirectory.toOption)
    }.foreach {
      case (scalaFile, directory) => createFileInDirectory(fileName(scalaFile), text, directory, scalaFile.getProject)
    }
  }

  private def createFileInDirectory(fileName: String, fileText: String, directory: PsiDirectory, project: Project) = {
    Try {
      extensions.inWriteCommandAction(project) {
        val file = directory.createFile(fileName).asInstanceOf[ScalaFile]
        val documentManager = PsiDocumentManager.getInstance(project)

        Option(documentManager.getDocument(file))
          .foreach { document =>
            document.setText(fileText)
            documentManager.commitDocument(document)
            updatePackageStatement(file, directory, project)

            new OpenFileDescriptor(project, file.getVirtualFile)
              .navigate(true)
          }
      }
    }.recover {
      case e: IncorrectOperationException => showErrorDialog(project, e.getMessage, "Paste")
    }
  }

  private def updatePackageStatement(file: ScalaFile, targetDir: PsiDirectory, project: Project) =
    startCommand(project, new Runnable {
      def run(): Unit = {
        Try {
          Some(JavaDirectoryService.getInstance)
            .flatMap(_.getPackage(targetDir).toOption)
            .map(_.getQualifiedName)
            .foreach(file.setPackageName)
        }
      }
    }, "Updating package statement")

  override def isPastePossible(dataContext: DataContext): Boolean = true

  override def isPasteEnabled(dataContext: DataContext): Boolean = {
    implicit val context = dataContext

    maybeIdeView.nonEmpty &&
      maybeModule.exists(_.hasScala) && //don't affect NON scala projects even when scala plugin is turn on
      maybeContent.zip(maybeProject).exists {
        case (text, project) => PlainTextCopyUtil.isValidScalaFile(text, project)
      }
  }
}

object ScalaFilePasteProvider {

  import CommonDataKeys._

  private def maybeProject(implicit context: DataContext): Option[Project] = Option(PROJECT.getData(context))

  private def maybeIdeView(implicit context: DataContext): Option[IdeView] = Option(IDE_VIEW.getData(context))

  private def maybeModule(implicit context: DataContext): Option[Module] = Option(MODULE.getData(context))

  private def maybeContent: Option[String] = Option(CopyPasteManager.getInstance.getContents(stringFlavor))
}
