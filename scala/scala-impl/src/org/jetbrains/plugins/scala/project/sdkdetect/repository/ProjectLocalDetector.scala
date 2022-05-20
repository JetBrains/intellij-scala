package org.jetbrains.plugins.scala.project.sdkdetect.repository

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vfs.{VfsUtilCore, VirtualFile}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.project.template.{PathExt, _}

import java.nio.file.Path
import java.util.stream.{Stream => JStream}

private[repository] class ProjectLocalDetector(contextDirectory: VirtualFile) extends ScalaSdkDetectorDependencyManagerBase {

  override def friendlyName: String = ScalaBundle.message("local.project.libraries")

  override protected def buildSdkChoice(descriptor: ScalaSdkDescriptor): SdkChoice = ProjectSdkChoice(descriptor)

  override protected def buildJarStream(implicit indicator: ProgressIndicator): JStream[Path] = {
    if (contextDirectory == null)
      return JStream.empty()

    VfsUtilCore.virtualToIoFile(contextDirectory).toOption
      .map(_.toPath / "lib")
      .filter(_.exists)
      .map(collectJarFiles)
      .getOrElse(JStream.empty())
  }
}

