package org.jetbrains.sbt
package resolvers

import com.intellij.openapi.module.ModuleManager
import com.intellij.psi.PsiFile
import org.apache.maven.index.ArtifactInfo
import org.jetbrains.sbt.project.module.SbtModule

/**
 * @author Nikolay Obedin
 * @since 8/4/14.
 */
object SbtResolverUtils {

  def getProjectResolvers(fileOpt: Option[PsiFile]): Seq[SbtResolver] = fileOpt match {
    case Some(file) =>
      val moduleManager = ModuleManager.getInstance(file.getProject)
      if (moduleManager == null) return Seq.empty
      moduleManager.getModules.toSeq.flatMap(SbtModule.getResolversFrom)
    case _ => Seq.empty
  }

  def joinGroupArtifact(group: String, artifact: String) = group + ":" + artifact
  def joinGroupArtifact(artifact: ArtifactInfo) = artifact.getGroupId + ":" + artifact.getArtifactId
}
