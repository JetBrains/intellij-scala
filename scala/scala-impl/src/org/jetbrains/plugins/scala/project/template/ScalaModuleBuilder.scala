package org.jetbrains.plugins.scala.project.template

import com.intellij.facet.impl.ui.libraries.LibraryCompositionSettings
import com.intellij.ide.util.projectWizard.JavaModuleBuilder
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VirtualFile

import java.{util => ju}

class ScalaModuleBuilder extends JavaModuleBuilder {

  var libraryCompositionSettings: LibraryCompositionSettings = _
  var packagePrefix = Option.empty[String]
  var openFileEditorAfterProjectOpened: Seq[VirtualFile] = Nil

  locally {
    addModuleConfigurationUpdater((_: Module, rootModel: ModifiableRootModel) => {
      val mutableEmptyList = new ju.ArrayList[Library]()
      libraryCompositionSettings.addLibraries(rootModel, mutableEmptyList, null)
      packagePrefix.foreach(prefix => rootModel.getContentEntries.foreach(_.getSourceFolders.foreach(_.setPackagePrefix(prefix))))
    })
  }

  override def setupModule(module: Module): Unit = {
    //execute when current dialog is closed
    openEditorForCodeSample(module.getProject)
    super.setupModule(module)
  }

  //open code sample or buildSbt
  private def openEditorForCodeSample(project: Project): Unit =
    ModuleBuilderUtil.openFilesInEditor(openFileEditorAfterProjectOpened, project)
}
