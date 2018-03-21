package org.jetbrains.plugins.scala.project.template

import com.intellij.openapi.ui.Messages
import javax.swing.JComponent
import org.jetbrains.plugins.scala.extensions.{withProgressSynchronously, withProgressSynchronouslyTry}
import org.jetbrains.plugins.scala.project.{Platform, Version, Versions}

import scala.util.{Failure, Success}

/**
  * @author Pavel Fatin
  */
class VersionDialog(parent: JComponent) extends VersionDialogBase(parent) {

  {
    init()

    setTitle("Download")
    myVersion.setTextRenderer(Version.abbreviate)

    val platform = Platform.Scala

    val versions = withProgressSynchronously(s"Fetching available ${platform.getName} versions") { _ =>
      Versions.loadScalaVersions(platform)
    }

    if (versions.length == 0) {
      Messages.showErrorDialog(createCenterPanel(), "No versions available for download", s"Error Downloading ${platform.getName} libraries")
    } else {
      myVersion.setItems(versions)
    }
  }

  def downloadVersionWithProgress(): Option[(String, String)] =
    if (showAndGet()) {
      val platform = Platform.Scala
      val version = myVersion.getSelectedItem.asInstanceOf[String]

      val result = withProgressSynchronouslyTry(s"Downloading ${platform.getName} $version") {
        Downloader.downloadScala(platform, version, _)
      }

      result match {
        case Success(_) =>
          Some((platform.getName, version))
        case Failure(exception) =>
          Messages.showErrorDialog(parent, exception.getMessage, s"Error downloading ${platform.getName} $version")
          None
      }
    } else None
}
