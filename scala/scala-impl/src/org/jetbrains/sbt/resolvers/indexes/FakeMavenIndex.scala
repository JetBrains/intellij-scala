package org.jetbrains.sbt.resolvers.indexes
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.sbt.resolvers.ArtifactInfo

/**
  * @author Mikhail Mutcianko
  * @since 26.08.16
  * @usecase if maven plugin is not enabled
  */
class FakeMavenIndex (val root: String, val name: String) extends ResolverIndex {
  override def searchGroup(artifactId: String)(implicit project: ProjectContext) = Set.empty

  override def searchArtifact(groupId: String)(implicit project: ProjectContext) = Set.empty

  override def searchVersion(groupId: String, artifactId: String)(implicit project: ProjectContext) = Set.empty

  override def doUpdate(progressIndicator: Option[ProgressIndicator])(implicit project: ProjectContext) = ()

  override def getUpdateTimeStamp(implicit project: ProjectContext) = ResolverIndex.MAVEN_UNAVALIABLE

  override def close() = ()

  override def searchArtifactInfo(fqName: String)(implicit project: ProjectContext): Set[ArtifactInfo] = Set.empty
}
