package org.jetbrains.sbt.resolvers.indexes
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.sbt.resolvers.ArtifactInfo

/**
  * @author Mikhail Mutcianko
  * @since 26.08.16
  * @usecase if maven plugin is not enabled
  */
class FakeMavenIndex (val root: String, val name: String, implicit val project: ProjectContext) extends ResolverIndex {
  override def searchGroup(artifactId: String): Set[String] = Set.empty

  override def searchArtifact(groupId: String): Set[String] = Set.empty

  override def searchVersion(groupId: String, artifactId: String): Set[String] = Set.empty

  override def doUpdate(progressIndicator: Option[ProgressIndicator]): Unit = ()

  override def getUpdateTimeStamp: Long = ResolverIndex.MAVEN_UNAVALIABLE

  override def close(): Unit = ()

  override def searchArtifactInfo(fqName: String)(implicit project: ProjectContext): Set[ArtifactInfo] = Set.empty
}
