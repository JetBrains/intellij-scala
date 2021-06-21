package org.jetbrains.sbt.project.template

import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalModuleBuilder
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

  // TODO customize the path in UI when IDEA-122951 will be implemented
  protected def moduleFilePathUpdated(pathname: String): String = {
    val file = new File(pathname) // points to the <projectRoot>/<moduleName>.impl
    file.getParent + "/" + Sbt.ModulesDirectory + "/" + file.getName
  }

}
