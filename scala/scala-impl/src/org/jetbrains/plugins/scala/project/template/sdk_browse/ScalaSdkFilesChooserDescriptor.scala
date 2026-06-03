package org.jetbrains.plugins.scala.project.template.sdk_browse

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.project.sdkdetect.repository.{CompilerClasspathResolveFailure, SystemDetector}
import org.jetbrains.plugins.scala.project.template.ScalaSdkDescriptor
import org.jetbrains.plugins.scala.project.template.sdk_browse.ScalaSdkFilesChooserDescriptor._
import org.jetbrains.plugins.scala.{NlsString, ScalaBundle}

import scala.annotation.nowarn
import scala.util.{Failure, Success, Try}

private class ScalaSdkFilesChooserDescriptor extends FileChooserDescriptor(true, true, true, true, false, true) {
  setTitle(ScalaBundle.message("title.scala.sdk.files"))
  setDescription(ScalaBundle.message("choose.either.a.scala.sdk.directory.or.scala.jar.files"))

  private var _resultSdkDescriptor: Option[ScalaSdkDescriptor] = None
  def resultSdkDescriptor: Option[ScalaSdkDescriptor] = _resultSdkDescriptor

  override def isFileSelectable(file: VirtualFile): Boolean = {
    (super.isFileSelectable(file): @nowarn("cat=deprecation")) && file.isDirectory || file.getExtension == "jar"
  }

  override def validateSelectedFiles(virtualFiles: Array[VirtualFile]): Unit = {
    Try(SystemDetector.buildSdkDescriptor(virtualFiles.toSeq)) match {
      case Success(Right(sdk)) =>
        _resultSdkDescriptor = Some(sdk)
      case Success(Left(errors)) =>
        throw new ValidationException(buildErrorsNlsMessage(errors))
      case Failure(ex) =>
        // some unpredictable exception (e.g. some IO exception while working with the file system)
        Log.error("Exception while validating scala sdk files", ex)
        throw new ValidationException(NlsString.force(ex.toString))
    }
  }
}

object ScalaSdkFilesChooserDescriptor {
  private val Log = Logger.getInstance(classOf[ScalaSdkFilesChooserDescriptor])

  // the message will be shown on UI by the IntelliJ
  private class ValidationException(message: NlsString) extends RuntimeException(message.nls)

  //noinspection ReferencePassedToNls
  private def buildErrorsNlsMessage(errors: Seq[CompilerClasspathResolveFailure]): NlsString =
    NlsString(errors.flatMap(_.nlsErrorMessage).mkString("\n"))
}
