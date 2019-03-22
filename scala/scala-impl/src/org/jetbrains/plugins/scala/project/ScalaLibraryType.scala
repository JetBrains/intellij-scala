package org.jetbrains.plugins.scala
package project

import java.{util => ju}

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries._
import com.intellij.openapi.roots.ui.configuration._
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.{Icon, JComponent}
import org.jetbrains.plugins.scala.project.template.{SdkChoice, SdkSelectionDialog}

/**
 * @author Pavel Fatin
 */
final class ScalaLibraryType extends LibraryType[ScalaLibraryProperties](ScalaLibraryType.Kind) {

  override def getIcon: Icon = icons.Icons.SCALA_SDK

  override def getCreateActionName = "Scala SDK"

  override def createNewLibrary(parent: JComponent,
                                contextDirectory: VirtualFile,
                                project: Project): NewLibraryConfiguration =
    ScalaLibraryType.Description.createNewLibrary(parent, contextDirectory)

  //noinspection TypeAnnotation
  override def createPropertiesEditor(editorComponent: ui.LibraryEditorComponent[ScalaLibraryProperties]) =
    new ui.LibraryPropertiesEditor {

      private val form = new ScalaLibraryEditorForm()

      override def createComponent: JComponent = form.getComponent

      override def isModified: Boolean = formState != propertiesState

      override def apply(): Unit = {
        properties.loadState(formState)
      }

      override def reset(): Unit = {
        val state = propertiesState
        form.setLanguageLevel(state.getLanguageLevel)
        form.setClasspath(state.getCompilerClasspath)
      }

      private def properties = editorComponent.getProperties

      private def propertiesState = properties.getState

      private def formState = new ScalaLibraryPropertiesState(
        form.getLanguageLevel,
        form.getClasspath
      )
    }

}

//noinspection TypeAnnotation
object ScalaLibraryType {

  def apply() =
    LibraryType.findByKind(Kind).asInstanceOf[ScalaLibraryType]

  object Kind extends PersistentLibraryKind[ScalaLibraryProperties]("Scala") {

    override def createDefaultProperties() = ScalaLibraryProperties()
  }

  object Description extends libraries.CustomLibraryDescription {

    override def getSuitableLibraryKinds = ju.Collections.singleton(Kind)

    override def createNewLibrary(parent: JComponent,
                                  contextDirectory: VirtualFile) =
      new SdkSelectionDialog(parent, sdksProvider(contextDirectory)).open() match {
        case null => null
        case description => description.createNewLibraryConfiguration
      }

    override def getDefaultLevel = projectRoot.LibrariesContainer.LibraryLevel.GLOBAL

    private def sdksProvider(contextDirectory: VirtualFile) = () => {
      import scala.collection.JavaConverters._
      SdkChoice.findSdks(contextDirectory).asJava
    }
  }

}