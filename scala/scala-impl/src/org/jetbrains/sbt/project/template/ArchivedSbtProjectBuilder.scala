package org.jetbrains.sbt.project.template

import java.io.File
import java.util.zip.ZipInputStream

import com.intellij.ide.util.projectWizard.{ModuleWizardStep, SdkSettingsStep, SettingsStep}
import com.intellij.openapi.module.{ModifiableModuleModel, Module}
import com.intellij.openapi.projectRoots.{JavaSdk, SdkTypeId}
import com.intellij.platform.templates.github.ZipUtil
import org.jetbrains.plugins.scala.extensions.using

class ArchivedSbtProjectBuilder(archiveTemplate: ArchivedSbtProjectTemplate) extends SbtModuleBuilder {

  override def modifySettingsStep(settingsStep: SettingsStep): ModuleWizardStep =
    new SdkSettingsStep(settingsStep, this, (_: SdkTypeId).isInstanceOf[JavaSdk])

  override def createModule(moduleModel: ModifiableModuleModel): Module = {
    new File(getModuleFileDirectory) match {
      case root if root.exists() =>

        using(new ZipInputStream(archiveTemplate.url.openStream)) { stream =>
          ZipUtil.unzip(null, root, stream, null, null, false)
        }

        setModuleFilePath(updateModuleFilePath(getModuleFilePath))
      case _ =>
    }

    super.createModule(moduleModel)
  }

}
