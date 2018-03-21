package org.jetbrains.plugins.scala.project.template

import com.intellij.openapi.ui.Messages
import javax.swing.JComponent
import org.jetbrains.plugins.scala.extensions.{withProgressSynchronously, withProgressSynchronouslyTry}
import org.jetbrains.plugins.scala.project.template.Downloader._
import org.jetbrains.plugins.scala.project.{Platform, Version, Versions}

import scala.util.{Failure, Success}

/**
  * @author Pavel Fatin
  */
class VersionDialog(parent: JComponent) extends VersionDialogBase(parent) {

  import VersionDialog._

  {
    init()

    setTitle("Download")
    myVersion.setTextRenderer(Version.abbreviate)

    val platform = Platform.Scala

    val versions = withProgressSynchronously(s"Fetching available ${platform.getName} versions") {
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
      val platformName = platform.getName
      val version = myVersion.getSelectedItem.asInstanceOf[String]

      val result = withProgressSynchronouslyTry(s"Downloading $platformName $version") { manager =>
        createTempSbtProject(version, new DownloadProcessAdapter(manager), setCommand(platform, version), "updateClassifiers")
      }

      result match {
        case Success(_) =>
          Some((platformName, version))
        case Failure(exception) =>
          Messages.showErrorDialog(parent, exception.getMessage, s"Error downloading $platformName $version")
          None
      }
    } else None
}

object VersionDialog {

  private def setCommand(platform: Platform, version: String) = platform match {
    case Platform.Scala => setScalaSBTCommand(version)
    case Platform.Dotty => setDependenciesSBTCommand(s""""ch.epfl.lamp" % "dotty_2.11" % "$version" % "scala-tool"""")
  }
}
