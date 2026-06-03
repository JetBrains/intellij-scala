package org.jetbrains.sbt.project

import com.intellij.openapi.module.Module
import org.jetbrains.sbt.settings.SbtSettings

final class SbtSbtVersionProvider extends SbtVersionProvider {
  override def getSbtVersion(module: Module): Option[String] = {
    val sbtProjectSettings = SbtSettings.getInstance(module.getProject).getLinkedProjectSettings(module)
    sbtProjectSettings.flatMap(s => Option(s.sbtVersion))
  }
}
