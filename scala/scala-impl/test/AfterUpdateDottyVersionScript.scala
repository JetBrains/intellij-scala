import java.io.{File, FileOutputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.platform.templates.github.{DownloadUtil, ZipUtil => GithubZipUtil}
import org.apache.ivy.osgi.util.ZipUtil
import org.jetbrains.plugins.scala.debugger.ScalaCompilerTestBase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion, extensions}
import org.jetbrains.plugins.scala.project.VirtualFileExt
import org.junit.Ignore

import scala.io.Source

/**
 * You should run this script after updating the version of Dotty.
 */
@Ignore
class AfterUpdateDottyVersionScript
  extends ScalaCompilerTestBase {

  def scalaUltimateProjectDir: Path =
    Paths.get(getClass.getProtectionDomain.getCodeSource.getLocation.getPath)
      .getParent.getParent.getParent
      .getParent.getParent.getParent

  /**
   * Runs all stuff needed after update dotty version
   */
  def testRunAllStuffNeededAfterUpdateDottyVersion(): Unit = {
    downloadLatestDottyProjectTemplate()
    recompileMacroPrinter3()
    replaceDottyVersionInTastyReadmeFiles()
  }

  /**
   * Downloads the latest Dotty project template
   */
  def downloadLatestDottyProjectTemplate(): Unit = {
    val resultFile = scalaUltimateProjectDir.resolve(Paths.get(
      "community", "scala", "scala-impl", "resources", "projectTemplates", "dottyTemplate.zip"
    )).toFile

    val url = "https://github.com/lampepfl/dotty.g8/archive/master.zip"
    val repoFile = newTempFile()
    DownloadUtil.downloadAtomically(new EmptyProgressIndicator, url, repoFile)

    val repoDir = newTempDir()
    GithubZipUtil.unzip(null, repoDir, repoFile, null, null, true)

    val dottyTemplateDir = repoDir.toPath.resolve(Paths.get("src", "main", "g8")).toFile
    ZipUtil.zip(dottyTemplateDir, new FileOutputStream(resultFile))
  }

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == LatestScalaVersions.Dotty

  /**
   * Recompile some classes needed in tests
   */
  def recompileMacroPrinter3(): Unit = {
    val resourcesPath = scalaUltimateProjectDir.resolve(Paths.get(
      "community", "scala", "runners", "resources"
    ))
    val packagePath = Paths.get("org", "jetbrains", "plugins", "scala", "worksheet")
    val sourceFileName = "MacroPrinter3_sources.scala"
    val targetDir = resourcesPath.resolve(packagePath)
    val sourceFile = targetDir.resolve(Paths.get("src", sourceFileName))

    val sourceContent = readFile(sourceFile)
    addFileToProjectSources(sourceFileName, sourceContent)
    compiler.make().assertNoProblems()

    CompilerModuleExtension.getInstance(getModule).getCompilerOutputPath
      .toFile.toPath.resolve(packagePath)
      .toFile.listFiles
      .foreach { compiledFile =>
        val resultFile = targetDir.resolve(compiledFile.getName).toFile
        compiledFile.renameTo(resultFile)
      }
  }

  def replaceDottyVersionInTastyReadmeFiles(): Unit = {
    val actualVersion = LatestScalaVersions.Dotty.minor
    val regex = "(lampepfl/dotty/blob/)(.+)(/library/src/scala/tasty/Reflection)".r
    val replacement = s"$$1$actualVersion$$3"
    Seq(
      Paths.get("community", "tasty", "compile", "README.md"),
      Paths.get("community", "tasty", "provided", "README.md")
    ).foreach { relativePath =>
      val readmeFile = scalaUltimateProjectDir.resolve(relativePath)
      val content = readFile(readmeFile)
      val fixedContent = content.replaceAll(regex.toString, replacement)
      Files.write(readmeFile, fixedContent.getBytes(StandardCharsets.UTF_8))
    }
  }

  private def readFile(path: Path): String =
    extensions.using(Source.fromFile(path.toFile))(_.mkString)

  private def newTempFile(): File =
    FileUtilRt.createTempFile(getClass.getName, "", true)

  private def newTempDir(): File =
    FileUtilRt.createTempDirectory(getClass.getName, "", true)
}
