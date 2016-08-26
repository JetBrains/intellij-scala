package org.jetbrains.sbt.resolvers.indexes
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project

/**
  * @author Mikhail Mutcianko
  * @since 26.08.16
  * @usecase if maven plugin is not enabled
  */
class FakeMavenIndex (val root: String, val name: String) extends ResolverIndex {
  override def searchGroup(artifactId: String)(implicit project: Project) = Set.empty

  override def searchArtifact(groupId: String)(implicit project: Project) = Set.empty

  override def searchVersion(groupId: String, artifactId: String)(implicit project: Project) = Set.empty

  override def doUpdate(progressIndicator: Option[ProgressIndicator])(implicit project: Project) = ()

  override def getUpdateTimeStamp(implicit project: Project) = ResolverIndex.MAVEN_UNAVALIABLE

  override def close() = ()
}
