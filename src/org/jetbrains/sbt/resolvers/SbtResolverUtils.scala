package org.jetbrains.sbt
package resolvers

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.sbt.project.module.SbtModule

/**
 * @author Nikolay Obedin
 * @since 8/4/14.
 */
object SbtResolverUtils {

  def getProjectResolversForFile(fileOpt: Option[PsiFile]): Set[SbtResolver] = fileOpt match {
    case Some(file) => getProjectResolvers(file.getProject)
    case _          => Set.empty
  }

  def getProjectResolvers(project: Project): Set[SbtResolver] = {
    val moduleManager = ModuleManager.getInstance(project)
    if (moduleManager == null) return Set.empty
    moduleManager.getModules.toSeq.flatMap(SbtModule.getResolversFrom).toSet
  }

  def joinGroupArtifact(group: String, artifact: String): String = group + ":" + artifact
  def joinGroupArtifact(artifact: ArtifactInfo): String = artifact.groupId + ":" + artifact.artifactId
}
