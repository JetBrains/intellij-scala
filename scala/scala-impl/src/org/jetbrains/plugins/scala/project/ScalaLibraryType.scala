package org.jetbrains.plugins.scala.project

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries._
import com.intellij.openapi.roots.ui.configuration._
import com.intellij.openapi.roots.{JavadocOrderRootType, OrderRootType}
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.project.external.ScalaSdkUtils

import java.io.File
import java.{util => ju}
import javax.swing.{Icon, JComponent}

final class ScalaLibraryType extends LibraryType[ScalaLibraryProperties](ScalaLibraryType.Kind) {

  override def getIcon(properties: ScalaLibraryProperties): Icon = Icons.SCALA_SDK

  override def getCreateActionName: String = ScalaBundle.message("library.type.scala.sdk")

  override def createNewLibrary(parent: JComponent,
                                contextDirectory: VirtualFile,
                                project: Project): NewLibraryConfiguration =
    ScalaLibraryType.Description.createNewLibrary(parent, contextDirectory)

  //noinspection TypeAnnotation
  override def createPropertiesEditor(editorComponent: ui.LibraryEditorComponent[ScalaLibraryProperties]) =
    new ui.LibraryPropertiesEditor {
      private val form = new ScalaLibraryEditorForm(editorComponent)

      override def createComponent: JComponent = form.contentPanel

      override def isModified: Boolean = formState != propertiesState

      override def apply(): Unit = {
        if (form.isValid) {
          properties.loadState(formState)
        }
      }

      override def reset(): Unit = {
        val state = propertiesState
        form.languageLevel = state.getLanguageLevel
        form.classpath = state.getCompilerClasspath
        form.compilerBridgeBinaryJar = state.getCompilerBridgeBinaryJar
      }

      private def properties = editorComponent.getProperties

      private def propertiesState = properties.getState

      private def formState: ScalaLibraryPropertiesState = new ScalaLibraryPropertiesState(
        form.languageLevel,
        form.classpath,
        // NOTE: currently do not support editing scaladocExtraClasspathFrom UI, so just propagate old settings
        properties.scaladocExtraClasspath.map(ScalaLibraryProperties.fileToUrl).toArray,
        form.compilerBridgeBinaryJar.orNull,
      )
    }
}

//noinspection TypeAnnotation
object ScalaLibraryType {

  import template._

  def apply() =
    LibraryType.findByKind(Kind).asInstanceOf[ScalaLibraryType]

  object Kind extends PersistentLibraryKind[ScalaLibraryProperties]("Scala") {

    override def createDefaultProperties() = ScalaLibraryProperties()
  }

  object Description extends libraries.CustomLibraryDescription {

    override def getSuitableLibraryKinds = ju.Collections.singleton(Kind)

    override def createNewLibrary(parent: JComponent,
                                  contextDirectory: VirtualFile): NewLibraryConfiguration = {
      val dialogWrapper = new SdkSelectionDialogWrapper(contextDirectory)
      val selectedSdkDescriptor = dialogWrapper.open()
      val scalaLibrary = selectedSdkDescriptor.map(createNewScalaLibrary)
      scalaLibrary.orNull
    }

    override def getDefaultLevel = projectRoot.LibrariesContainer.LibraryLevel.GLOBAL

    def createNewScalaLibrary(descriptor: ScalaSdkDescriptor): NewLibraryConfiguration = {
      val ScalaSdkDescriptor(version, _, compilerClasspath, scaladocExtraClasspath, libraryFiles, sourceFiles, docFiles, compilerBridgeJar, _) = descriptor

      val compilerBridge = compilerBridgeJar.orElse {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
          () => version.flatMap(ScalaSdkUtils.resolveCompilerBridgeJar),
          ScalaBundle.message("resolving.compiler.bridge.progress.message"),
          true,
          null
        )
      }

      new NewLibraryConfiguration(
        "scala-sdk-" + version.getOrElse(Version.Default),
        ScalaLibraryType(),
        ScalaLibraryProperties(version, compilerClasspath, scaladocExtraClasspath, compilerBridge)
      ) {
        override def addRoots(editor: libraryEditor.LibraryEditor): Unit = {
          addRootsInner(libraryFiles, OrderRootType.CLASSES)(editor)
          addRootsInner(sourceFiles, OrderRootType.SOURCES)(editor)
          addRootsInner(docFiles, JavadocOrderRootType.getInstance)(editor)

          if (sourceFiles.isEmpty && docFiles.isEmpty) editor.addRoot(
            s"https://www.scala-lang.org/api/${version.getOrElse("current")}/",
            JavadocOrderRootType.getInstance
          )
        }

        private def addRootsInner(files: Iterable[File], rootType: OrderRootType)
                                 (implicit editor: libraryEditor.LibraryEditor): Unit =
          for {
            file <- files
            url = file.toLibraryRootURL
          } editor.addRoot(url, rootType)
      }
    }
  }

}