package org.jetbrains.plugins.scala.projectHighlighting.base

import com.intellij.lang.javascript.boilerplate.GithubDownloadUtil
import com.intellij.platform.templates.github.ZipUtil
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.sbt.SbtTestDataUtils
import org.junit.Assert

import java.nio.file.{Files, Path}
import java.util.zip.ZipInputStream
import scala.util.Using

abstract class SbtProjectHighlightingDownloadingFromGithubTestBase extends SbtProjectHighlightingTestBase {

  override protected def rootProjectsDirPath: String =
    SbtTestDataUtils.resolveRelativePath(
      "sbt-project-highlighting-tests/testdata/projectsForHighlightingTests/downloaded"
    )

  override protected def projectName: String = githubRepositoryWithRevision.repositoryName

  protected def githubRepositoryWithRevision: GithubRepositoryWithRevision

  override def setUp(): Unit = {
    downloadAndExtractProject()
    super.setUp()
  }

  private def downloadAndExtractProject(): Unit = {
    val outputZipFile = Path.of(outputZipFileName)
    val projectDir = getTestProjectPath

    reporter.notify(s"Project output zip file: $outputZipFile")
    reporter.notify(s"Project directory: $projectDir")

    if (outputZipFile.exists) {
      reporter.notify("Skipping download: project zip file already exists")
    }
    else if (projectDir.exists && projectDir.children().nonEmpty) {
      reporter.notify("Skipping download: project directory already exist")
    }
    else {
      //don't download if zip file is already there
      reporter.notify(s"Starting download")
      // GithubDownloadUtil.downloadAtomically only accepts a java.io.File target; there is no nio.Path-based alternative.
      //noinspection SSBasedInspection
      GithubDownloadUtil.downloadAtomically(
        reporter.progressIndicator,
        githubRepositoryWithRevision.revisionDownloadUrl,
        outputZipFile.toFile,
        githubRepositoryWithRevision.userName,
        githubRepositoryWithRevision.repositoryName
      )
    }

    if (projectDir.exists) {
      //don't unpack if the project is already unpacked
      reporter.notify("Project files already extracted")
    } else {
      reporter.notify("Finished download, extracting")
      Using.resource(new ZipInputStream(Files.newInputStream(outputZipFile))) { stream =>
        ZipUtil.unzip(null, projectDir, stream, null, null, true)
      }
    }

    Assert.assertTrue("Project dir does not exist. Download or unpack failed!", projectDir.exists)
    reporter.notify("Finished extracting, starting sbt setup")
  }

  private def outputZipFileName: String = {
    val GithubRepositoryWithRevision(userName, repoName, revision) = githubRepositoryWithRevision
    s"$rootProjectsDirPath/zipFiles/$repoName-$userName-$revision"
  }
}
