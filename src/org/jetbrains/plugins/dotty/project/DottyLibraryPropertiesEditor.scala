package org.jetbrains.plugins.dotty.project

import java.awt.event.{ActionEvent, ActionListener}

import com.intellij.openapi.roots.libraries.ui.{LibraryEditorComponent, LibraryPropertiesEditor}
import com.intellij.openapi.ui.Messages
import org.jetbrains.plugins.dotty.project.template.DottyDownloader
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.project.{ScalaLanguageLevel, ScalaLibraryProperties}

import scala.runtime.BoxedUnit
import scala.util.Failure

/**
  * @author Nikolay.Tropin
  */
class DottyLibraryPropertiesEditor(editorComponent: LibraryEditorComponent[ScalaLibraryProperties]) extends LibraryPropertiesEditor {

  private val updateSnapshotAction = new ActionListener {

    override def actionPerformed(e: ActionEvent): Unit = {
      val version: String = ScalaLanguageLevel.Dotty.version
      val result = extensions.withProgressSynchronouslyTry(s"Downloading Dotty $version (via SBT)") {
        case listener =>
          DottyDownloader.downloadDotty(version, s => listener(s))
          BoxedUnit.UNIT
      }

      result match {
        case Failure(exc) =>
          Messages.showErrorDialog(editorComponent.getProject, exc.getMessage, s"Error downloading Dotty $version")
        case _ =>
      }
    }
  }

  private val form = new DottyLibraryEditorForm()
  form.setUpdateSnapshotAction(updateSnapshotAction)

  def createComponent() = form.getComponent

  def isModified = form.getState != properties.getState

  def reset() {
    form.setState(properties.getState)
  }

  def apply() {
    properties.loadState(form.getState)
  }

  private def properties = editorComponent.getProperties
}
