package org.jetbrains.plugins.scala.uiTests.backend

import com.intellij.configurationStore.StoreUtil
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.{Library, LibraryProperties, LibraryTable, LibraryTablesRegistrar, LibraryType, NewLibraryConfiguration}
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryEditingUtil
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor
import com.intellij.openapi.roots.{ModifiableRootModel, ModuleRootModificationUtil}
import org.apache.ivy.util.MessageLogger
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.{DependencyManagerBase, ScalaBundle, ScalaVersion}
import org.jetbrains.plugins.scala.components.libextensions.ProgressIndicatorLogger
import org.jetbrains.plugins.scala.extensions.withProgressSynchronouslyTry
import org.jetbrains.plugins.scala.project.ScalaLibraryType
import org.jetbrains.plugins.scala.project.template.ScalaVersionDownloadingDialog.{ScalaVersionResolveResult, createScalaVersionResolveResult}
import org.jetbrains.plugins.scala.project.template.{Artifact, ScalaSdkDescriptor, ScalaVersionDownloadingDialog}

import scala.util.Try

private[backend] object ScalaSdkUtilImpl {
  def setupScalaSdk(@NotNull scalaVersionString: String): Unit = {
    val project = ProjectManager.getInstance.getOpenProjects()(0)
    val module = ModuleManager.getInstance(project).getModules()(0)

    val newLibraryConfiguration = createNewScalaLibraryConfiguration(scalaVersionString)
    ModuleRootModificationUtil.updateModel(module, (model: ModifiableRootModel) => {
      addProjectLibrary(model, newLibraryConfiguration)
    })

    StoreUtil.saveSettings(project, true)
  }

  private def createNewScalaLibraryConfiguration(scalaVersionString: String): NewLibraryConfiguration = {
    val scalaVersion = ScalaVersion.fromString(scalaVersionString).getOrElse {
      throw new IllegalArgumentException(s"Invalid scala version: $scalaVersionString")
    }
    val resolvedScalaVersion = tryDownloadScalaWithProgress(scalaVersion).get
    val scalaSdkDescriptor = convertScalaResolveResultToScalaSdkDescriptor(resolvedScalaVersion)

    ScalaLibraryType.Description.createNewScalaLibrary(scalaSdkDescriptor)
  }

  //Copied from:
  // com.intellij.testFramework.PsiTestUtil.addProjectLibrary(com.intellij.openapi.roots.ModifiableRootModel, java.lang.String, java.util.List<? extends com.intellij.openapi.vfs.VirtualFile>, java.util.List<? extends com.intellij.openapi.vfs.VirtualFile>, java.util.List<? extends com.intellij.openapi.vfs.VirtualFile>, java.util.List<? extends com.intellij.openapi.vfs.VirtualFile>)
  private def addProjectLibrary(
    model: ModifiableRootModel,
    libraryConfiguration: NewLibraryConfiguration,
  ): Library = {
    WriteAction.computeAndWait(() => {
      val libraryTableModel: LibraryTable.ModifiableModel = model.getModuleLibraryTable.getModifiableModel

      val projectLibraryTable: LibraryTable = LibraryTablesRegistrar.getInstance.getLibraryTable(model.getProject)

      val libraryName = LibraryEditingUtil.suggestNewLibraryName(libraryTableModel, libraryConfiguration.getDefaultLibraryName)
      val library: Library = projectLibraryTable.createLibrary(libraryName)
      val libraryModel = library.getModifiableModel.asInstanceOf[LibraryEx.ModifiableModelEx]

      try {
        //NOTE: the red code comes from SCL-23078
        val libraryType: LibraryType[_ <: LibraryProperties[_]] = libraryConfiguration.getLibraryType
        val libraryKind = if (libraryType != null) libraryType.getKind else null
        libraryModel.setKind(libraryKind)

        val editor = new NewLibraryEditor(libraryType, libraryConfiguration.getProperties)
        libraryConfiguration.addRoots(editor)
        editor.applyTo(libraryModel)
      } catch {
        case t: Throwable =>
          //noinspection SSBasedInspection
          libraryTableModel.dispose()
          throw t
      }

      libraryModel.commit()
      libraryTableModel.commit()

      model.addLibraryEntry(library)

      library
    })
  }

  private def tryDownloadScalaWithProgress(scalaVersion: ScalaVersion): Try[ScalaVersionResolveResult] = {
    withProgressSynchronouslyTry(ScalaBundle.message("downloading.scala.version", scalaVersion.minor), canBeCanceled = true) { manager =>
      val indicator = manager.getProgressIndicator
      val dependencyManager = new DependencyManagerBase {
        override protected def progressIndicator: Option[ProgressIndicator] = Some(indicator)
        override def createLogger: MessageLogger = new ProgressIndicatorLogger(indicator)
      }
      createScalaVersionResolveResult(scalaVersion, dependencyManager)
    }
  }

  private val ScalaLibraryFileNames = Artifact.ScalaLibraryAndModulesArtifacts.map(_.prefix)

  private def convertScalaResolveResultToScalaSdkDescriptor(scalaVersionResolveResult: ScalaVersionResolveResult): ScalaSdkDescriptor = {
    val compilerJars = scalaVersionResolveResult.compilerClassPathJars
    val libraryJars = compilerJars.filter(f => ScalaLibraryFileNames.exists(f.getFileName.toString.startsWith(_)))
    val scaladocExtraClasspath = Nil // TODO SCL-17219
    ScalaSdkDescriptor(
      version = Some(scalaVersionResolveResult.scalaVersion),
      label = None,
      compilerClasspath = compilerJars,
      scaladocExtraClasspath = scaladocExtraClasspath,
      libraryFiles = libraryJars,
      sourceFiles = scalaVersionResolveResult.librarySourcesJars,
      docFiles = Nil, // docs are not downloaded
      compilerBridgeJar = scalaVersionResolveResult.compilerBridgeJar,
      replClasspath = None
    )
  }
}
