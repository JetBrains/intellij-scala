package org.jetbrains.plugins.scala.conversion.copy

import java.awt.datatransfer.DataFlavor

import com.intellij.ide.PasteProvider
import com.intellij.openapi.actionSystem.{CommonDataKeys, DataContext, LangDataKeys}
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi._
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaCodeFragment
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.ModuleExt

/**
  * Created by Kate Ustyuzhanina on 12/13/16.
  */

class ScalaFilePasteProvider extends PasteProvider {

  import ScalaFilePasteProvider._

  override def performPaste(dataContext: DataContext): Unit = {

    def fileName(scalaFile: ScalaFile): String =
      scalaFile.getClasses.headOption.map(_.name + ".scala").getOrElse("scriptFile.scala")

    val text: String = CopyPasteManager.getInstance.getContents(DataFlavor.stringFlavor)
    val project = CommonDataKeys.PROJECT.getData(dataContext)

    val optScalaFile = Option(project).flatMap((project: Project) => createScalaFileFromString(text, project))
    val optTargetDir = Option(LangDataKeys.IDE_VIEW.getData(dataContext)).flatMap(_.getOrChooseDirectory.toOption)

    if (optScalaFile.isEmpty || optTargetDir.isEmpty) return

    extensions.inWriteCommandAction(project) {
      try {
        val scalaFile = optScalaFile.get
        val directory = optTargetDir.get
        val file = directory.createFile(fileName(scalaFile))

        Option(PsiDocumentManager.getInstance(project).getDocument(file)).foreach { document =>
          document.setText(scalaFile.getText)
          PsiDocumentManager.getInstance(project).commitDocument(document)

          updatePackageStatement(file, directory, project)
          new OpenFileDescriptor(project, file.getVirtualFile).navigate(true)
        }
      } catch {
        case e: IncorrectOperationException =>
          Messages.showErrorDialog(project, e.getMessage, "Paste")
      }
    }
  }

  private def updatePackageStatement(file: PsiFile, targetDir: PsiDirectory, project: Project) {
    if (!file.isInstanceOf[ScalaFile]) return
    CommandProcessor.getInstance.executeCommand(project, new Runnable {
      def run() {
        try {
          Option(JavaDirectoryService.getInstance.getPackage(targetDir))
            .foreach(dir => file.asInstanceOf[ScalaFile].setPackageName(dir.getQualifiedName))
        } catch {
          case _: IncorrectOperationException => //do nothing
        }
      }
    }, "Updating package statement", null)
  }

  override def isPastePossible(dataContext: DataContext): Boolean = {
    true
  }

  override def isPasteEnabled(dataContext: DataContext): Boolean = {
    Option(LangDataKeys.IDE_VIEW.getData(dataContext)).nonEmpty &&
      Option(LangDataKeys.MODULE.getData(dataContext)).exists(_.hasScala) && //don't affect NON scala projects even when scala plugin is turn on
      Option(CommonDataKeys.PROJECT.getData(dataContext))
        .flatMap(createScalaFileFromString(CopyPasteManager.getInstance.getContents(DataFlavor.stringFlavor), _)).nonEmpty
  }

  private def createScalaFileFromString(text: String, project: Project) = {
    val optFile = Option(text).flatMap(new ScalaCodeFragment(project, _).asOptionOf[ScalaFile])

    if (isValidScalaFile(optFile)) optFile else None
  }
}

object ScalaFilePasteProvider {
  def isValidScalaFile(file: Option[ScalaFile], acceptableErrors: Seq[String] = Seq.empty): Boolean = {
    !file.exists(_.depthFirst().exists {
      case err: PsiErrorElement if acceptableErrors.contains(err.getErrorDescription) => false
      case _: PsiErrorElement => true
      case _ => false
    })
  }
}

