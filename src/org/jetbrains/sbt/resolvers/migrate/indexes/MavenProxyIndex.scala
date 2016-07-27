package org.jetbrains.sbt.resolvers.migrate.indexes

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.indices.{MavenArtifactSearcher, MavenIndex, MavenIndicesManager, MavenProjectIndicesManager}

import scala.collection.JavaConversions._

/**
  * @author Mikhail Mutcianko
  * @since 26.07.16
  */
class MavenProxyIndex(val root: String, val name: String) extends ResolverIndex {

  private val MAX_RESULTS = 1000

  override def doUpdate(progressIndicator: Option[ProgressIndicator] = None)(implicit project: Project): Unit = {
    findPlatformMavenResolver(project)
      .foreach(i=>MavenProjectIndicesManager.getInstance(project).scheduleUpdate(List(i)))
  }

  override def getUpdateTimeStamp(implicit project: Project): Long = {
    findPlatformMavenResolver(project).map(_.getUpdateTimestamp).getOrElse(ResolverIndex.NO_TIMESTAMP)
  }

  override def close(): Unit = ()

  override def searchGroup(artifactId: String)(implicit project: Project): Set[String] = {
    (new MavenArtifactSearcher)
      .search(project, artifactId, MAX_RESULTS).flatMap(_.versions.map(_.getGroupId)).toSet
  }

  override def searchArtifact(groupId: String)(implicit project: Project): Set[String] = {
    (new MavenArtifactSearcher)
      .search(project, groupId, MAX_RESULTS).flatMap(_.versions.map(_.getArtifactId)).toSet
  }

  override def searchVersion(groupId: String, artifactId: String)(implicit project: Project): Set[String] = {
    (new MavenArtifactSearcher)
      .search(project, s"$groupId:$artifactId", MAX_RESULTS).flatMap(_.versions.map(_.getVersion)).toSet
  }

  private def findPlatformMavenResolver(project: Project): Option[MavenIndex] = {
    MavenProjectIndicesManager.getInstance(project)
      .getIndices.find(_.getRepositoryPathOrUrl == MavenIndex.normalizePathOrUrl(root))
  }
}

