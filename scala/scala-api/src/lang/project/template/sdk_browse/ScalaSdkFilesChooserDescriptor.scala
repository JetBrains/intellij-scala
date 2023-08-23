package org.jetbrains.plugins.scala.project.template.sdk_browse

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.vfs.{VfsUtilCore, VirtualFile}
import org.jetbrains.plugins.scala.project.sdkdetect.repository.{CompilerClasspathResolveFailure, SystemDetector}
import org.jetbrains.plugins.scala.project.template.sdk_browse.ScalaSdkFilesChooserDescriptor._
import org.jetbrains.plugins.scala.project.template.{FileExt, ScalaSdkComponent, ScalaSdkDescriptor}
import org.jetbrains.plugins.scala.{NlsString, ScalaBundle}

private class ScalaSdkFilesChooserDescriptor extends FileChooserDescriptor(true, true, true, true, false, true) {
  setTitle(ScalaBundle.message("title.scala.sdk.files"))
  setDescription(ScalaBundle.message("choose.either.a.scala.sdk.directory.or.scala.jar.files"))

  private var _resultSdkDescriptor: Option[ScalaSdkDescriptor] = None
  def resultSdkDescriptor: Option[ScalaSdkDescriptor] = _resultSdkDescriptor

  override def isFileSelectable(file: VirtualFile): Boolean = {
    super.isFileSelectable(file) && file.isDirectory || file.getExtension == "jar"
  }

  override def validateSelectedFiles(virtualFiles: Array[VirtualFile]): Unit = {
    buildSdkDescriptor(virtualFiles.toSeq) match {
      case Left(errors) =>
        throw new ValidationException(buildErrorsNlsMessage(errors))
      case Right(sdk) =>
        _resultSdkDescriptor = Some(sdk)
    }
  }
}

object ScalaSdkFilesChooserDescriptor {

  // the message will be shown on UI by the IntelliJ
  private class ValidationException(message: NlsString) extends RuntimeException(message.nls)

  private def buildSdkDescriptor(virtualFiles: Seq[VirtualFile]): Either[Seq[CompilerClasspathResolveFailure], ScalaSdkDescriptor] = {
    val files = virtualFiles.map(VfsUtilCore.virtualToIoFile)
    val allFiles = files.filter(_.isFile) ++ files.flatMap(_.allFiles)
    val components = ScalaSdkComponent.fromFiles(allFiles)
    SystemDetector.buildFromComponents(components, None)
  }

  //noinspection ReferencePassedToNls
  private def buildErrorsNlsMessage(errors: Seq[CompilerClasspathResolveFailure]): NlsString =
    NlsString(errors.flatMap(_.nlsErrorMessage).mkString("\n"))
}
