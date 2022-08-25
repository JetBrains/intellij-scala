package org.jetbrains.plugins.scala.projectHighlighting.base

import com.intellij.lang.javascript.boilerplate.GithubDownloadUtil
import com.intellij.platform.templates.github.ZipUtil
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert

import java.io.File

abstract class SbtProjectHighlightingDownloadingFromGithubTestBase extends SbtProjectHighlightingTestBase {

  override protected def rootProjectsDirPath: String = s"${TestUtils.getTestDataPath}/projectsForHighlightingTests/downloaded"

  override protected def projectName: String = githubRepositoryWithRevision.repositoryName

  protected def githubRepositoryWithRevision: GithubRepositoryWithRevision

  protected def outputZipFileName: String = {
    val GithubRepositoryWithRevision(userName, repoName, revision) = githubRepositoryWithRevision
    s"$rootProjectsDirPath/zipFiles/$repoName-$userName-$revision"
  }

  override protected def doBeforeImport(): Unit = downloadAndExtractProject()

  private def downloadAndExtractProject(): Unit = {
    val outputZipFile = new File(outputZipFileName)
    val projectDir = new File(projectDirPath)

    reporter.notify(s"Project output zip file: $outputZipFile")
    reporter.notify(s"Project directory: $projectDir")

    if (outputZipFile.exists() || projectDir.exists()) {
      reporter.notify("Project files already exist, skipping download")
    } else {
      //don't download if zip file is already there
      reporter.notify(s"Starting download")
      GithubDownloadUtil.downloadAtomically(
        reporter.progressIndicator,
        githubRepositoryWithRevision.revisionDownloadUrl,
        outputZipFile,
        githubRepositoryWithRevision.userName,
        githubRepositoryWithRevision.repositoryName
      )
    }

    if (projectDir.exists()) {
      //don't unpack if the project is already unpacked
      reporter.notify("Project files already extracted")
    } else {
      reporter.notify("Finished download, extracting")
      ZipUtil.unzip(null, projectDir, outputZipFile, null, null, true)
    }

    Assert.assertTrue("Project dir does not exist. Download or unpack failed!", projectDir.exists())
    reporter.notify("Finished extracting, starting sbt setup")
  }
}