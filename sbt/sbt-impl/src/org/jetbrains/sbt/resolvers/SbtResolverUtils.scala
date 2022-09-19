package org.jetbrains.sbt
package resolvers

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.sbt.project.module.SbtModule

object SbtResolverUtils {
  def projectResolvers(implicit project: Project): Set[SbtResolver] = ModuleManager.getInstance(project) match {
    case null => Set.empty
    case manager => manager.getModules.toSet.flatMap(SbtModule.Resolvers.apply)
  }
}
