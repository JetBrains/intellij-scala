package org.jetbrains.plugins.scala.project.maven.resolvers

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.indices.{MavenIndexUtils, MavenRepositoryProvider}
import org.jetbrains.idea.maven.model.MavenRemoteRepository
import org.jetbrains.sbt.project.module.SbtModule
import org.jetbrains.sbt.resolvers.{SbtMavenResolver, SbtResolver}

import scala.jdk.CollectionConverters.*

final class SbtMavenRepositoryProvider extends MavenRepositoryProvider:
  override def getRemoteRepositories(project: Project): java.util.Set[MavenRemoteRepository] =
    projectResolvers(project).collect:
      case r: SbtMavenResolver =>
        MavenRemoteRepository(
          /*              id = */ r.name,
          /*            name = */ null,
          /*             url = */ MavenIndexUtils.normalizePathOrUrl(r.root),
          /*          layout = */ null,
          /*  releasesPolicy = */ null,
          /* snapshotsPolicy = */ null
        )
    .asJava

  private def projectResolvers(project: Project): Set[SbtResolver] =
    ModuleManager.getInstance(project).getModules.toSet.flatMap(SbtModule.Resolvers.apply)
