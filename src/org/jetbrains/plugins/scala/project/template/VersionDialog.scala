package org.jetbrains.plugins.scala.project.template

import java.awt.event.{ActionEvent, ActionListener}
import javax.swing.JComponent

import com.intellij.openapi.ui.Messages
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.project.{Platform, Versions}

/**
  * @author Pavel Fatin
  */
class VersionDialog(parent: JComponent) extends VersionDialogBase(parent) {
  init()

  setTitle("Download")

  myPlatform.setItems(Platform.Values)

  myPlatform.addActionListener(new ActionListener() {
    def actionPerformed(e: ActionEvent) {
      updateVersions()
    }
  })

  updateVersions()

  override def createCenterPanel(): JComponent = myContent

  private def updateVersions() {
    val platform = myPlatform.getSelectedItem.asInstanceOf[Platform]

    val versions = extensions.withProgressSynchronously(s"Fetching available ${platform.name} versions") { _ =>
      platform match {
        case Platform.Scala => Versions.loadScalaVersions
        case Platform.Dotty => Versions.loadDottyVersions
      }
    }

    if (versions.length == 0) {
      Messages.showErrorDialog(myContent, "No versions available for download", s"Error Downloading ${platform.name} libraries")
    } else {
      myVersion.setItems(versions)
    }
  }

  def selectedPlatform: Platform = myPlatform.getSelectedItem.asInstanceOf[Platform]

  def selectedVersion: String = myVersion.getSelectedItem.asInstanceOf[String]
}
