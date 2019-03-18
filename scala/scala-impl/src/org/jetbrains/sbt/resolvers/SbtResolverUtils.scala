package org.jetbrains.sbt
package resolvers

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.sbt.project.module.SbtModule

/**
  * @author Nikolay Obedin
  * @since 8/4/14.
  */
object SbtResolverUtils {

  def projectResolvers(place: PsiElement): Set[SbtResolver] =
    ScalaPsiUtil.fileContext(place) match {
      case null => Set.empty
      case file => projectResolvers(file.getProject)
    }

  def projectResolvers(implicit project: Project): Set[SbtResolver] = ModuleManager.getInstance(project) match {
    case null => Set.empty
    case manager => manager.getModules.toSet.flatMap(SbtModule.Resolvers.apply)
  }

  def joinGroupArtifact(group: String, artifact: String): String = group + ":" + artifact

  def joinGroupArtifact(artifact: ArtifactInfo): String = artifact.groupId + ":" + artifact.artifactId
}
