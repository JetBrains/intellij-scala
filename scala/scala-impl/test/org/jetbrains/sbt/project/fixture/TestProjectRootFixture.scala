package org.jetbrains.sbt.project.fixture

import com.intellij.openapi.util.io.NioFiles
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.sbt.project.ScalaExternalSystemImportingTestBase.TestProjectCopyOptions

import java.nio.file.{Files, Path}

final class TestProjectRootFixture(
  originalTestDataProjectDir: Path,
  copyOptions: TestProjectCopyOptions,
) {

  private var isTestDataProjectCopiedToTempDir: Boolean = false

  /**
   * Holds a path of the actual project that is used in the tests (not just test data).<br>
   *  - it points to `originalTestDataProjectDir` when [[TestProjectCopyOptions.copyToTemporaryDir]] is `false`
   *  - it points to the project copy in a temporary directory when [[TestProjectCopyOptions.copyToTemporaryDir]] is `true`
   *
   * The actual project copying is done in [[copyTestDataProjectToTempDirIfNeeded]].
   */
  lazy val testProjectPath: Path = {
    if (!copyOptions.copyToTemporaryDir)
      originalTestDataProjectDir
    else
      createTemporaryTestProjectPath()
  }

  /**
   * Copies the test data project to the runtime test project root.
   * For tests using the runtime project as IDEA project root, this must happen before the IDEA project fixture is opened.
   */
  def copyTestDataProjectToTempDirIfNeeded(): Unit = {
    if (copyOptions.copyToTemporaryDir && !isTestDataProjectCopiedToTempDir) {
      val to = testProjectPath
      println(s"Test project copied to the temporary directory: $to")
      NioFiles.copyRecursively(originalTestDataProjectDir, to)
      isTestDataProjectCopiedToTempDir = true
    }
  }

  private def createTemporaryTestProjectPath(): Path = {
    val projectName = originalTestDataProjectDir.getFileName.toString
    val tmpPath = Files.createTempDirectory(projectName).toRealPath()

    // Delete temporary project location on JVM exit.
    if (copyOptions.deleteTempDirectoryOnTestProcessShutDown) {
      Runtime.getRuntime.addShutdownHook(new Thread(() => {
        NioFiles.deleteRecursively(tmpPath)
      }))
    }

    // Files.createTempDirectory uses projectName as a prefix only;
    // by appending extra `projectName` we keep the actual project root dir name stable inside it.
    tmpPath / projectName
  }
}
