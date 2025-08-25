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
import groovy.transform.Internal
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.conversion.ScalaConversionBundle
import org.jetbrains.plugins.scala.conversion.copy.plainText.ScalaFilePasteProvider._
import org.jetbrains.plugins.scala.extensions.{IteratorExt, ObjectExt, PsiElementExt, PsiMemberExt, ToNullSafe, inWriteCommandAction, startCommand}
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

  import ScalaFilePasteProvider.PasteActionIntention._

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
    calculatePasteActionOutcome(context) match {
      case Some((pasteActionOutcome, project)) =>
        executePasteActionOutcome(pasteActionOutcome)(project)
      case _ => // Nothing to paste or invalid context
    }
  }

  private def calculatePasteActionOutcome(context: DataContext): Option[(PasteActionIntention, Project)] = {
    for {
      copiedText <- CopyPasteManager.getInstance.copiedText
      module <- context.maybeModuleWithScala
      directory <- context.maybeIdeView.flatMap(_.getOrChooseDirectory.toOption)
      pasteActionOutcome <- calculatePasteActionOutcome(pastedText = copiedText, module, directory)
    } yield (pasteActionOutcome, module.getProject)
  }

  @Internal
  @TestOnly
  def calculatePasteActionOutcome(
    pastedText: String,
    module: Module,
    directory: PsiDirectory
  ): Option[PasteActionIntention] = {
    for {
      scalaFragment <- PlainTextCopyUtil.createScalaCodeFragmentIfParsedTolerably(pastedText, module)
    } yield {
      if (shouldCreateOrUpdatePluginsSbtFile(scalaFragment, module, directory)) {
        val pluginsFileName = "plugins.sbt"
        val existingPluginsFile = directory.findFile(pluginsFileName)

        if (existingPluginsFile != null)
          calculateUpdateExistingFileOutcome(existingPluginsFile, pastedText)
        else
          CreateNewFile(directory, FileNameWithExtension("plugins", "sbt"), pastedText)
      } else {
        // Handle regular Scala files - create a new file with a suggested name
        val fileName = calculateBestFileNameFromPastedContent(scalaFragment)
        CreateNewFile(directory, fileName, pastedText)
      }
    }
  }

  private def calculateUpdateExistingFileOutcome(existingPluginsFile: PsiFile, pastedText: String): UpdateExistingFile = {
    val lastAddSbtPluginStatement = existingPluginsFile.elements.findLast(isAddSbtPluginStatement)
    lastAddSbtPluginStatement match {
      case Some(element) =>
        val elementEndOffset = element.getTextRange.getEndOffset
        UpdateExistingFile(
          existingPluginsFile,
          insertedText = "\n" + pastedText,
          insertionOffset = elementEndOffset,
          navigationOffset = elementEndOffset + 1
        )
      case None =>
        val existingContent = existingPluginsFile.getText
        val existingContentStripped = existingContent.stripTrailing()
        if (existingContentStripped.isEmpty)
          UpdateExistingFile(
            existingPluginsFile,
            insertedText = pastedText,
            insertionOffset = 0,
            navigationOffset = 0
          )
        else {
          val insertionOffset = existingContentStripped.length
          UpdateExistingFile(
            existingPluginsFile,
            insertedText = "\n" + pastedText,
            insertionOffset = insertionOffset,
            navigationOffset = insertionOffset + 1
          )
        }
    }
  }

  private def calculateBestFileNameFromPastedContent(pastedScalaFragment: ScalaFile): FileNameWithExtension = {
    val topLevelMembers = pastedScalaFragment.members
    val firstMemberName = topLevelMembers.headOption.flatMap(_.names.headOption)
    val firstMemberFileName = firstMemberName.map(FileNameWithExtension(_, "scala"))
    val regularScalaFileName = firstMemberFileName.orElse {
      if (pastedScalaFragment.firstPackaging.isDefined) Some(FileNameWithExtension("definitions", "scala"))
      else None
    }

    regularScalaFileName.getOrElse(FileNameWithExtension("worksheet", "sc"))
  }

  private def shouldCreateOrUpdatePluginsSbtFile(
    pastedScalaCodeFragment: ScalaFile,
    module: Module,
    directory: PsiDirectory
  ): Boolean = {
    // true if we paste to the `project/` directory in a sbt project
    val isSbtBuildModuleRoot = module.isBuildModule && ModuleRootManager.getInstance(module).getSourceRoots.contains(directory.getVirtualFile)
    if (!isSbtBuildModuleRoot)
      return false

    hasAddSbtPluginTopLevelStatement(pastedScalaCodeFragment)
  }

  private def hasAddSbtPluginTopLevelStatement(pastedScalaCodeFragment: ScalaFile): Boolean = {
    val addSbtPluginStatement = pastedScalaCodeFragment.children.find(isAddSbtPluginStatement)
    addSbtPluginStatement.nonEmpty
  }

  private def isAddSbtPluginStatement(psiElement: PsiElement): Boolean = psiElement match {
    case MethodInvocation((ref, _)) =>
      ref.textMatches("addSbtPlugin")
    case _ =>
      false
  }

  private def executePasteActionOutcome(
    pasteActionOutcome: PasteActionIntention
  )(implicit project: Project): Unit = try {
    pasteActionOutcome match {
      case create: CreateNewFile =>
        createNewFileInDirectory(create)

      case update: UpdateExistingFile =>
        updateExistingFileInDirectory(update)
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

  private def createNewFileInDirectory(create: CreateNewFile)(implicit project: Project): Unit =  {
    val FileNameWithExtension(name, extension) = create.fileName
    val targetPsiDir = create.targetDirectory

    val allowCreateMultipleFilesWithSameName = extension == "sc" || extension == "sbt"
    // For some file types, allow creating multiple worksheets in the same directory:
    //  - worksheet.sc, worksheet1.sc, worksheet2.sc, etc...
    //  - plugins.sbt, plugins1.sbt (only when not updating existing)
    val fileName: String =
      if (allowCreateMultipleFilesWithSameName)
        VfsUtil.getNextAvailableName(targetPsiDir.getVirtualFile, name, extension)
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
        document.setText(create.fileText)
        documentManager.commitDocument(document)

        CodeStyleManager.getInstance(project).adjustLineIndent(psiFile, psiFile.getTextRange)
        documentManager.commitDocument(document)

        updatePackageStatement(psiFile, targetPsiDir)

        new OpenFileDescriptor(project, psiFile.getVirtualFile).navigate(true)
      }
    }
  }

  private def updateExistingFileInDirectory(update: UpdateExistingFile)(implicit project: Project): Unit = {
    inWriteCommandAction {
      val documentManager = PsiDocumentManager.getInstance(project)
      val psiFile = update.psiFile
      val document = documentManager.getDocument(psiFile)

      if (document != null) {
        // Insert the new content at the specified offset
        document.insertString(update.insertionOffset, update.insertedText)
        documentManager.commitDocument(document)

        CodeStyleManager.getInstance(project).adjustLineIndent(psiFile, update.insertionOffset)
        documentManager.commitDocument(document)

        // Navigate to the insertion point
        val fileDescriptor = new OpenFileDescriptor(project, psiFile.getVirtualFile, update.navigationOffset)
        fileDescriptor.navigate(true)
      }
    }
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

  sealed trait PasteActionIntention

  object PasteActionIntention {
    case class CreateNewFile(
      targetDirectory: PsiDirectory,
      fileName: FileNameWithExtension,
      fileText: String,
    ) extends PasteActionIntention

    case class UpdateExistingFile(
      psiFile: PsiFile,
      insertedText: String,
      insertionOffset: Int,
      navigationOffset: Int,
    ) extends PasteActionIntention
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
