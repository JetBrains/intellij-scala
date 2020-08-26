package org.jetbrains.sbt
package resolvers

import java.{util => ju}

import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.indices.{MavenIndex, MavenRepositoryProvider}
import org.jetbrains.idea.maven.model.MavenRemoteRepository

import scala.jdk.CollectionConverters._

final class SbtMavenRepositoryProvider extends MavenRepositoryProvider {

  override def getRemoteRepositories(project: Project): ju.Set[MavenRemoteRepository] =
    SbtResolverUtils.projectResolvers(project).collect {
      case r: SbtMavenResolver =>
        new MavenRemoteRepository(
          r.name,
          null,
          MavenIndex.normalizePathOrUrl(r.root),
          null,
          null,
          null
        )
    }.asJava
}
