package org.jetbrains.sbt.project.template

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalModuleBuilder
import com.intellij.openapi.module.{JavaModuleType, ModuleType}
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.settings.SbtProjectSettings

import java.io.File

abstract class SbtModuleBuilderBase
  extends AbstractExternalModuleBuilder[SbtProjectSettings](
    SbtProjectSystem.Id,
    new SbtProjectSettings
  ) {

  locally {
    val settings = getExternalProjectSettings
    settings.setResolveJavadocs(false)
  }

  //TODO: why is it JavaModuleType and not SbtModuleType?
  override def getModuleType: ModuleType[_ <: ModuleBuilder] = JavaModuleType.getModuleType

  // TODO customize the path in UI when IDEA-122951 will be implemented
  protected def moduleFilePathUpdated(pathname: String): String = {
    val file = new File(pathname) // points to the <projectRoot>/<moduleName>.impl
    file.getParent + "/" + Sbt.ModulesDirectory + "/" + file.getName
  }
}
