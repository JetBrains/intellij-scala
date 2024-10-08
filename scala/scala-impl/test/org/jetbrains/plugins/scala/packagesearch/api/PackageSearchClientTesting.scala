package org.jetbrains.plugins.scala.packagesearch.api

import org.jetbrains.packagesearch.api.v3.ApiMavenPackage.MavenVersion
import org.jetbrains.packagesearch.api.v3.{ApiMavenPackage, ApiMavenVersion, VersionWithRepositories, VersionsContainer}

import java.util.Collections.{emptyList, emptySet}
import scala.jdk.CollectionConverters.SeqHasAsJava

trait PackageSearchClientTesting {
  protected def mavenVersion(version: String): ApiMavenVersion = new MavenVersion(
    /*normalized*/ NormalizedVersionCompanionProxy.from(version),
    /*repositoryIds*/ emptySet(),
    /*vulnerability*/ VulnerabilityCompanionProxy.notVulnerable(),
    /*dependencies*/ emptyList(),
    /*artifacts*/ emptyList(),
    /*name*/ null,
    /*description*/ null,
    /*authors*/ emptyList(),
    /*scmUrl*/ null,
  )

  protected def versionWithRepositories(version: String) = new VersionWithRepositories(
    /*normalizedVersion*/ NormalizedVersionCompanionProxy.from(version),
    /*repositoryIds*/ emptySet(),
  )

  protected def emptyVersionsContainer(): VersionsContainer[ApiMavenVersion] =
    versionsContainer("")

  protected def versionsContainer(latestVersion: String): VersionsContainer[ApiMavenVersion] =
    versionsContainer(latestVersion, Some(latestVersion))

  protected def versionsContainer(latestVersion: String, latestStableVersion: Option[String]): VersionsContainer[ApiMavenVersion] =
    versionsContainer(latestVersion, latestStableVersion, Seq(latestVersion))

  protected def versionsContainer(latestVersion: String, allVersions: Seq[String]): VersionsContainer[ApiMavenVersion] =
    versionsContainer(latestVersion, Some(latestVersion), allVersions)

  protected def versionsContainer(latestVersion: String,
                                  latestStableVersion: Option[String],
                                  allVersions: Seq[String]) =
    new VersionsContainer(
      /*latestStable*/ latestStableVersion.map(mavenVersion).orNull,
      /*latest*/ mavenVersion(latestVersion),
      /*all*/ allVersions.map(versionWithRepositories).asJava,
    )

  protected def apiMavenPackage(groupId: String, artifactId: String, versions: VersionsContainer[ApiMavenVersion]) = new ApiMavenPackage(
    /*id*/ "",
    /*idHash*/ "",
    /*rankingMetric*/ null,
    /*versions*/ versions,
    /*licenses*/ null,
    groupId,
    artifactId,
    /*scm*/ null,
  )
}
