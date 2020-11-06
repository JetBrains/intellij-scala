import java.io.{File, FileOutputStream, PrintWriter}
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.util
import java.util.Enumeration

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.platform.templates.github.{DownloadUtil, ZipUtil => GithubZipUtil}
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.{TestCase, TestFailure, TestResult, TestSuite}
import org.apache.ivy.osgi.util.ZipUtil
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.debugger.ScalaCompilerTestBase
import org.jetbrains.plugins.scala.lang.parser.scala3.imported.{Scala3ImportedParserTest, Scala3ImportedParserTestBase, Scala3ImportedParserTest_Fail}
import org.jetbrains.plugins.scala.project.VirtualFileExt
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Ignore
import org.junit.runner.JUnitCore

import scala.io.Source
import scala.jdk.CollectionConverters.{EnumerationHasAsScala, ListHasAsScala}
import scala.util.Using

@Ignore("for local running only")
class AfterUpdateDottyVersionScript
  extends TestCase {

  import AfterUpdateDottyVersionScript._

  def testRunAllScripts(): Unit = {
    val tests =
      Script.FromTestCase(classOf[DownloadLatestDottyProjectTemplate]) #::
      Script.FromTestCase(classOf[RecompileMacroPrinter3]) #::
      Script.FromTestCase(classOf[Scala3ImportedParserTest_Import_FromDottyDirectory]) #::
      Script.FromTestSuite(new Scala3ImportedParserTest_Move_Fixed_Tests) #::
        LazyList.empty
    tests.foreach(runScript)
  }

  private def runScript[A](script: Script): Unit = script match {
    case Script.FromTestCase(clazz) =>
      val result = new JUnitCore().run(clazz)
      result.getFailures.asScala.headOption match {
        case Some(failure) =>
          println(s"${clazz.getSimpleName} FAILED")
          throw failure.getException
        case None =>
          println(s"${clazz.getSimpleName} COMPLETED")
      }
    case Script.FromTestSuite(suite) =>
      val result = new TestResult
      suite.run(result)
      result.stop()

      val problems = (result.errors().asScala.toList ++ result.failures().asScala.toList)
        .asInstanceOf[List[TestFailure]] // It can't be compiled on TC by some reason. So we need asInstanceOf here.
      problems.headOption match {
        case Some(problem) =>
          println(s"${suite.getClass.getSimpleName} FAILED")
          throw problem.thrownException()
        case None =>
          println(s"${suite.getClass.getSimpleName} COMPLETED")
      }
  }
}

object AfterUpdateDottyVersionScript {
  private val dottyParserTestsSuccessDir = TestUtils.getTestDataPath + Scala3ImportedParserTest.directory
  private val dottyParserTestsFailDir = TestUtils.getTestDataPath +  Scala3ImportedParserTest_Fail.directory

  private def downloadRepository(url: String): File = {
    val repoFile = newTempFile()
    DownloadUtil.downloadAtomically(new EmptyProgressIndicator, url, repoFile)

    val repoDir = newTempDir()
    GithubZipUtil.unzip(null, repoDir, repoFile, null, null, true)
    repoDir
  }

  /**
   * Downloads the latest Dotty project template
   *
   * @author artyom.semyonov
   */
  private class DownloadLatestDottyProjectTemplate
    extends BasePlatformTestCase {

    def test(): Unit = {
      val resultFile = scalaUltimateProjectDir.resolve(Paths.get(
        "community", "scala", "scala-impl", "resources", "projectTemplates", "dottyTemplate.zip"
      )).toFile

      val repoPath = downloadRepository("https://github.com/lampepfl/dotty.g8/archive/master.zip").toPath
      val dottyTemplateDir = repoPath.resolve(Paths.get("src", "main", "g8")).toFile
      ZipUtil.zip(dottyTemplateDir, new FileOutputStream(resultFile))
    }
  }

  /**
   * Recompile some classes needed in tests
   *
   * @author artyom.semyonov
   */
  private class RecompileMacroPrinter3
    extends ScalaCompilerTestBase {

    override protected def supportedIn(version: ScalaVersion): Boolean =
      version == LatestScalaVersions.Dotty

    def test(): Unit = {
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

    private def readFile(path: Path): String =
      Using.resource(Source.fromFile(path.toFile))(_.mkString)
  }

  /**
   * Imports Tests from the dotty repositiory
   *
   * @author tobias.kahlert
   */
  private class Scala3ImportedParserTest_Import_FromDottyDirectory
    extends TestCase {

    def test(): Unit = {
      val repoPath = downloadRepository("https://github.com/lampepfl/dotty/archive/master.zip").toPath
      val srcDir = repoPath.resolve(Paths.get("tests", "pos")).toAbsolutePath.toString

      clearDirectory(dottyParserTestsSuccessDir)
      clearDirectory(dottyParserTestsFailDir)

      println("srcdir =  " + srcDir)
      println("faildir = " + dottyParserTestsFailDir)

      new File(dottyParserTestsSuccessDir).mkdirs()
      new File(dottyParserTestsFailDir).mkdirs()

      var atLeastOneFileProcessed = false
      for (file <- allFilesIn(srcDir) if file.toString.toLowerCase.endsWith(".scala"))  {
        val target = dottyParserTestsFailDir + file.toString.substring(srcDir.length).replace(".scala", "++++test")
        val content = {
          val src = Source.fromFile(file)
          try {
            val content = src.mkString
            content.replaceAll("[-]{5,}", "+") // <- some test files have comment lines with dashes which confuse junit
          } finally src.close()
        }

        val targetFile = new File(target)

        val targetWithDirs = dottyParserTestsFailDir + "/" + Iterator
          .iterate(targetFile)(_.getParentFile)
          .takeWhile(_ != null)
          .takeWhile(!_.isDirectory)
          .map(_.getName.replace('.', '_').replace("++++", "."))
          .toSeq
          .reverse
          .mkString("_")
        println(file.toString + " -> " + targetWithDirs)

        val pw = new PrintWriter(targetWithDirs)
        pw.write(content)
        if (content.last != '\n')
          pw.write('\n')
        pw.println("-----")
        pw.close()
        atLeastOneFileProcessed = true
      }
      if (!atLeastOneFileProcessed)
        throw new AssertionError("No files were processed")
    }

    private def allFilesIn(path: String): Iterator[File] =
      allFilesIn(new File(path))

    private def allFilesIn(path: File): Iterator[File] = {
      if (!path.exists) Iterator.empty
      else if (!path.isDirectory) Iterator(path)
      else path.listFiles.iterator.flatMap(allFilesIn)
    }

    private def clearDirectory(path: String): Unit =
      new File(path).listFiles().foreach(_.delete())
  }

  /**
   * Run this main method to move all scala 3 test files that generate no PsiErrorElements anymore to
   * the succeeding directory
   *
   * Use this if you have made progress in the parser and fixed files that produced PsiErrorElement
   * and, now, make Scala3ImportedParserTest_Fail fail. In this case this method will move those
   * into the succeeding folder, so they can fail if someone screws anything up in the parser, that
   * had previously worked.
   *
   * @author tobias.kahlert
   */
  private class Scala3ImportedParserTest_Move_Fixed_Tests
    extends Scala3ImportedParserTestBase(Scala3ImportedParserTest_Fail.directory) {

    protected override def transform(testName: String, fileText: String, project: Project): String = {
      val (errors, _) = findErrorElements(fileText, project)

      if (errors.isEmpty) {
        val from = dottyParserTestsFailDir + "/" + testName + ".test"
        val to = dottyParserTestsSuccessDir + "/" + testName + ".test"

        println("Move " + from)
        println("  to " + to)
        Files.move(
          Paths.get(from),
          Paths.get(to),
          StandardCopyOption.REPLACE_EXISTING
        )
      }
      // all files of failing test have no ast to test against, so return an empty string here
      ""
    }

    override protected def shouldHaveErrorElements: Boolean = throw new UnsupportedOperationException
  }

  private def scalaUltimateProjectDir: Path =
    Paths.get(getClass.getProtectionDomain.getCodeSource.getLocation.getPath)
      .getParent.getParent.getParent
      .getParent.getParent.getParent

  private def newTempFile(): File =
    FileUtilRt.createTempFile(getClass.getName, "", true)

  private def newTempDir(): File =
    FileUtilRt.createTempDirectory(getClass.getName, "", true)

  sealed trait Script
  object Script {
    final case class FromTestCase(clazz: Class[_ <: TestCase]) extends Script
    final case class FromTestSuite(suite: TestSuite) extends Script
  }
}
