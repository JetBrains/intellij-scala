package org.jetbrains.sbt.resolvers

import java.util

import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.indices.{MavenIndex, MavenRepositoryProvider}
import org.jetbrains.idea.maven.model.MavenRemoteRepository

class SbtMavenRepositoryProvider extends MavenRepositoryProvider {
  import scala.collection.JavaConverters._


  override def getRemoteRepositories(project: Project): util.Set[MavenRemoteRepository] = {
    val result = repositories(project)
    result.asJava
  }

  def repositories(project: Project): Set[MavenRemoteRepository] = {
    SbtResolverUtils.getProjectResolvers(project).collect {
      case r:SbtMavenResolver =>
        new MavenRemoteRepository(r.name, null, MavenIndex.normalizePathOrUrl(r.root), null, null, null)
    }
  }
}
