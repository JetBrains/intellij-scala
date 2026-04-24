package org.jetbrains.plugins.scala.projectHighlighting.base

case class GithubRepositoryWithRevision(
  userName: String,
  repositoryName: String,
  revision: String,
) {
  def repositoryUrl: String = s"https://github.com/$userName/$repositoryName"

  def revisionDownloadUrl: String = s"$repositoryUrl/archive/$revision.zip"
}

object GithubRepositoryWithRevision {
  val ScalaCommunityGithubRepo: GithubRepositoryWithRevision = GithubRepositoryWithRevision(
    "JetBrains",
    "intellij-scala",
    "a9ac902e8930c520b390095d9e9346d9ae546212"
  )
}