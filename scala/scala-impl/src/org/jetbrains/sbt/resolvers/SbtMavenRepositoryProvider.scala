package org.jetbrains.sbt
package resolvers

import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.indices.{MavenIndexUtils, MavenRepositoryProvider}
import org.jetbrains.idea.maven.model.MavenRemoteRepository

import java.{util => ju}
import scala.jdk.CollectionConverters._

final class SbtMavenRepositoryProvider extends MavenRepositoryProvider {

  override def getRemoteRepositories(project: Project): ju.Set[MavenRemoteRepository] =
    SbtResolverUtils.projectResolvers(project).collect {
      case r: SbtMavenResolver =>
        new MavenRemoteRepository(
          r.name,
          null,
          MavenIndexUtils.normalizePathOrUrl(r.root),
          null,
          null,
          null
        )
    }.asJava
}
