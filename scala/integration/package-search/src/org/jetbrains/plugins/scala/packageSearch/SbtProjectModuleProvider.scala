package org.jetbrains.plugins.scala.packageSearch

import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.extensibility.{ProjectModule, ProjectModuleProvider}
import kotlin.sequences.Sequence

class SbtProjectModuleProvider extends ProjectModuleProvider{
  override def obtainAllProjectModulesFor(project: Project): Sequence[ProjectModule] = {

  }
}
