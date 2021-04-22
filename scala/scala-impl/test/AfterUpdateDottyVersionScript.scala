import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.platform.templates.github.{DownloadUtil, ZipUtil => GithubZipUtil}
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.io.ZipUtil
import junit.framework.{TestCase, TestFailure, TestResult, TestSuite}
import org.jetbrains.plugins.scala.debugger.ScalaCompilerTestBase
import org.jetbrains.plugins.scala.lang.parser.scala3.imported.{Scala3ImportedParserTest, Scala3ImportedParserTest_Move_Fixed_Tests}
import org.jetbrains.plugins.scala.project.VirtualFileExt
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Ignore
import org.junit.runner.JUnitCore

import java.io.{File, FileOutputStream, PrintWriter}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.util.zip.ZipOutputStream
import scala.io.Source
import scala.jdk.CollectionConverters.{EnumerationHasAsScala, ListHasAsScala, MapHasAsScala}
import scala.sys.process.Process
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
      Script.FromTestSuite(new Scala3ImportedParserTest_Move_Fixed_Tests.Scala3ImportedParserTest_Move_Fixed_Tests) #::
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
  import Scala3ImportedParserTest_Move_Fixed_Tests.{dottyParserTestsFailDir, dottyParserTestsSuccessDir}
  private val rangesDirectory: String = TestUtils.getTestDataPath + Scala3ImportedParserTest.rangesDirectory

  private def downloadRepository(url: String): File = {
    val repoFile = newTempFile()
    DownloadUtil.downloadAtomically(new EmptyProgressIndicator, url, repoFile)

    val repoDir = newTempDir()
    GithubZipUtil.unzip(null, repoDir, repoFile, null, null, true)
    repoDir
  }

  private def cloneRepository(url: String): File = {
    val cloneDir = newTempDir()
    val sc = Process("git" :: "clone" :: url :: "." :: "--depth=1" :: Nil, cloneDir).!
    assert(sc == 0, s"Failed ($sc) to clone $url into $cloneDir")
    cloneDir
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

      val repoPath = downloadRepository("https://github.com/lampepfl/dotty.g8/archive/main.zip").toPath
      assertTrue("repository folder doesn't exist", repoPath.toFile.exists())

      val dottyTemplateDir = repoPath.resolve(Paths.get("src", "main", "g8")).toFile
      assertTrue("template folder doesn't exist", dottyTemplateDir.exists())

      // no need it it, it doesn't contain any useful info
      val g8ProjectDescriptionFile = new File(dottyTemplateDir, "default.properties")
      g8ProjectDescriptionFile.delete()

      // ATTENTION !!! Ensure created zip archive is correctly unzipped on all OS. Especially if the script is run
      // on Windows, check it on Linux: it shouldn't contain backslashes archive entries paths.
      Using.resource(new ZipOutputStream(new FileOutputStream(resultFile))) { zipOutput =>
        ZipUtil.addDirToZipRecursively(zipOutput, null, dottyTemplateDir, "", null, null)
      }
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
      version == LatestScalaVersions.Scala_3_0 // TODO: ATTENTION! ENSURE VERSION IS UPDATED ON RUN

    override def testProjectJdkVersion = LanguageLevel.JDK_1_8

    private def log(msg: String): Unit =
      println(s"${this.getClass.getSimpleName}: $msg")

    def test(): Unit = {
      log("start")

      val resourcesPath = scalaUltimateProjectDir.resolve(Paths.get(
        "community", "scala", "runners", "resources"
      ))
      val packagePath = Paths.get("org", "jetbrains", "plugins", "scala", "worksheet")
      val sourceFileName = "MacroPrinter3_sources.scala"
      val targetDir = resourcesPath.resolve(packagePath)
      val sourceFile = targetDir.resolve(Paths.get("src", sourceFileName))
      assertTrue(new File(sourceFile.toUri).exists())

      log("reading source file")
      val sourceContent = readFile(sourceFile)
      addFileToProjectSources(sourceFileName, sourceContent)
      log("compiling")
      compiler.make().assertNoProblems()

      val compileOutput = CompilerModuleExtension.getInstance(getModule).getCompilerOutputPath
      assertTrue("compilation output not found", compileOutput.exists())

      val folderWithClasses = compileOutput.toFile.toPath.resolve(packagePath).toFile
      assertTrue(folderWithClasses.exists())

      val classes = folderWithClasses.listFiles.toSeq
      assertEquals(
        classes.map(_.getName).toSet,
        Set("MacroPrinter3$.class", "MacroPrinter3.class", "MacroPrinter3.tasty")
      )

      log(
        s"""copying ${classes.length} classes: $targetDir
           |    from : $folderWithClasses
           |    to   : $targetDir""".stripMargin
      )

      classes.foreach { compiledFile =>
        val resultFile = targetDir.resolve(compiledFile.getName)
        Files.copy(compiledFile.toPath, resultFile, StandardCopyOption.REPLACE_EXISTING)
      }
      log("end")
    }
  }

  /**
   * Imports Tests from the dotty repositiory
   *
   * @author tobias.kahlert
   */
  private class Scala3ImportedParserTest_Import_FromDottyDirectory
    extends TestCase {

    def test(): Unit = {
      // we have to clone the repo because it needs a git history
      val repoPath = cloneRepository("https://github.com/lampepfl/dotty/").toPath
      val srcDir = repoPath.resolve(Paths.get("tests", "pos")).toAbsolutePath.toString

      clearDirectory(dottyParserTestsSuccessDir)
      clearDirectory(dottyParserTestsFailDir)

      println("srcdir =  " + srcDir)
      println("faildir = " + dottyParserTestsFailDir)

      new File(dottyParserTestsSuccessDir).mkdirs()
      new File(dottyParserTestsFailDir).mkdirs()

      //val tempRangeSourceDir = Path.of("/home/tobi/desktop/testing/pos")
      val tempRangeSourceDir = newTempDir().toPath.resolve("pos")
      tempRangeSourceDir.toFile.mkdirs()

      var atLeastOneFileProcessed = false
      for (file <- allFilesIn(srcDir) if file.toString.toLowerCase.endsWith(".scala"))  {
        val target = dottyParserTestsFailDir + file.toString.substring(srcDir.length).replace(".scala", "++++test")
        val content = readFile(file.toPath)
          .replaceAll("[-]{5,}", "+") // <- some test files have comment lines with dashes which confuse junit

        if (!content.contains("import language.experimental")) {
          val targetFile = new File(target)

          val outputFileName = Iterator
            .iterate(targetFile)(_.getParentFile)
            .takeWhile(_ != null)
            .takeWhile(!_.isDirectory)
            .map(_.getName.replace('.', '_').replace("++++", "."))
            .toSeq
            .reverse
            .mkString("_")
          val outputPath = dottyParserTestsFailDir + File.separator + outputFileName
          val outputInRangeDir = tempRangeSourceDir.resolve(outputFileName.replaceFirst("test$", "scala"))
          println(file.toString + " -> " + outputPath)

          {
            val pw = new PrintWriter(outputPath)
            pw.write(content)
            if (content.last != '\n')
              pw.write('\n')
            pw.println("-----")
            pw.close()
          }

          // print it into a temporary directory which we can use to run sbt tests on
          {
            val pw = new PrintWriter(outputInRangeDir.toFile)
            pw.write(content)
            pw.close()
          }
          atLeastOneFileProcessed = true
        }
      }
      if (!atLeastOneFileProcessed)
        throw new AssertionError("No files were processed")

      extractRanges(repoPath, tempRangeSourceDir, rangesDirectory)
    }
  }

  private def scalaUltimateProjectDir: Path = {
    val file = new File(getClass.getProtectionDomain.getCodeSource.getLocation.getPath)
    file
      .getParentFile.getParentFile.getParentFile
      .getParentFile.getParentFile.getParentFile
      .toPath
  }

  //noinspection MutatorLikeMethodIsParameterless
  private def deleteTempFileOnExit = true
  private def newTempFile(): File =
    FileUtilRt.createTempFile("imported-dotty-tests", "", deleteTempFileOnExit)

  private def newTempDir(): File =
    FileUtilRt.createTempDirectory("imported-dotty-tests", "", deleteTempFileOnExit)

  private def allFilesIn(path: String): Iterator[File] =
    allFilesIn(new File(path))

  private def allFilesIn(path: File): Iterator[File] = {
    if (!path.exists) Iterator.empty
    else if (!path.isDirectory) Iterator(path)
    else path.listFiles.iterator.flatMap(allFilesIn)
  }

  private def clearDirectory(path: String): Unit = {
    val file = new File(path)
    if (file.exists()) {
      assert(file.isDirectory)
      val files = new File(path).listFiles()
      assert(files != null)
      files.foreach(_.delete())
    }
    else {
      // probably the folder is already deleted in the previous script run
    }
  }

  sealed trait Script
  object Script {
    final case class FromTestCase(clazz: Class[_ <: TestCase]) extends Script
    final case class FromTestSuite(suite: TestSuite) extends Script
  }

  /**
   * Runs the dotty test suite on the imported files and extracts ranges of syntax elements for each test file
   * This is done by patching multiple files in the dotty compiler/test source.
   * Most importantly we hook into the main parse function and traverse trees that were created there.
   *
   * @param repoPath path to the complete dotty source code
   * @param testFilePath path to a directory that contains all test files
   * @param targetRangeDirectory path where the resulting range files are put into
   */
  private def extractRanges(repoPath: Path, testFilePath: Path, targetRangeDirectory: String): Unit = {
    /* not needed anymore?
    // patch test source to not delete tasty files
    patchFile(
      repoPath.resolve("compiler/test/dotty/tools/vulpix/ParallelTesting.scala"),
      "shouldDelete = true",
      "shouldDelete = false"
    )*/

    // patch test source to take our own source files
    patchFile(
      repoPath.resolve("compiler/test/dotty/tools/dotc/FromTastyTests.scala"),
      "compileTastyInDir(s\"tests${JFile.separator}pos\"",
      s"compileTastyInDir(${"\"" + normalisedPathSeparator1(testFilePath) + "\""}"
    )

    /* not needed anymore?
    // patch away an assertion that prevents tree traversal in the parser.
    // This is like setting the mode to Mode.Interactive, just easier :D
    patchFile(
      repoPath.resolve("compiler/src/dotty/tools/dotc/ast/Trees.scala"),
      "assert(ctx.reporter.errorsReported || ctx.mode.is(Mode.Interactive), tree)",
      "assert(true || ctx.reporter.errorsReported || ctx.mode.is(Mode.Interactive), tree)"
    )*/

    // patch the parse function to output the ranges of the parsed tree
    patchFile(
      repoPath.resolve("compiler/src/dotty/tools/dotc/parsing/Parsers.scala"),
      """    def parse(): Tree = {
        |      val t = compilationUnit()
        |      accept(EOF)
        |      t
        |    }
        |""".stripMargin,
      s"""
         |def parse(): Tree = {
         |  val t = compilationUnit()
         |  accept(EOF)
         |  // we need to test if the files are actually our test files
         |  // because this function is also used to compile some bootstrap libraries
         |  if (!source.path.contains("${normalisedPathSeparator1(testFilePath)}") &&
         |      !source.path.contains("${normalisedPathSeparator2(testFilePath)}"))
         |    return t
         |  val w = new java.io.PrintWriter("${normalisedPathSeparator1(targetRangeDirectory)}/" + source.name.replace(".scala", ".ranges"), java.nio.charset.StandardCharsets.UTF_8)
         |  val traverser = new dotty.tools.dotc.ast.untpd.UntypedTreeTraverser {
         |    def traverse(tree: Tree)(using Context) = {
         |      val span = tree.span
         |      if (span.exists) {
         |        val s = tree.toString
         |        val endOfName = s.indexOf("(")
         |        val name =
         |          if endOfName == -1
         |          then s
         |          else s.substring(0, endOfName)
         |        w.println(s"[$${span.start},$${span.end}]: $$name")
         |      }
         |      traverseChildren(tree)
         |    }
         |  }
         |  traverser.traverse(t)
         |  w.close()
         |  EmptyTree  // <- prevent rest of the tests from failing
         |}
         |""".stripMargin.replaceAll("\n", "\n    ")
    )

    {
      new File(rangesDirectory).mkdirs()
      clearDirectory(rangesDirectory)
    }

    val isWindows = System.getProperty("os.name").toLowerCase.contains("win")
    val sbtExecutable = if (isWindows) "sbt.bat" else "sbt"
    val sc2 = Process(sbtExecutable :: "testCompilation --from-tasty pos" :: Nil, repoPath.toFile).!
    assert(sc2 == 0, s"sbt failed with exit code $sc2")

    val blacklisted = linesInFile(repoPath.resolve("compiler/test/dotc/pos-from-tasty.blacklist"))
      .filterNot(_.isBlank)
      .filterNot(_.startsWith("#"))
      .size
    assert(allFilesIn(dottyParserTestsFailDir).size - blacklisted == allFilesIn(rangesDirectory).size)
  }

  // We need to replace `\` with `/` (or escape `\` to `\\`) to make files patching work on Windows,
  // otherwise source file will interpret backslash as an invalid escape sequence in `C:\Users\user`
  private def normalisedPathSeparator1(path: Path): String = normalisedPathSeparator1(path.toString)
  private def normalisedPathSeparator1(path: String)(implicit d: DummyImplicit): String = path.replace("\\", "/")
  private def normalisedPathSeparator2(path: Path): String = normalisedPathSeparator2(path.toString)
  private def normalisedPathSeparator2(path: String)(implicit d: DummyImplicit): String = path.replace("\\", "\\\\")

  private def patchFile(path: Path, searchString0: String, replacement0: String): Unit = {
    val searchString = searchString0.replace("\r", "")
    val replacement = replacement0.replace("\r", "")
    val content = readFile(path)
    if (!content.contains(searchString) && !content.contains(replacement)) {
      throw new Exception(s"Couldn't patch file $path because $searchString was not found in the content")
    }
    val newContent = content.replace(searchString, replacement)
    val w = new PrintWriter(path.toFile, StandardCharsets.UTF_8)
    try w.write(newContent)
    finally w.close()
  }

  private def linesInFile(path: Path): Seq[String] =
    Using.resource(Source.fromFile(path.toFile))(_.getLines().toSeq)

  private def readFile(path: Path): String =
    Using.resource(Source.fromFile(path.toFile))(_.mkString)

  /*
  def main(args: Array[String]): Unit = {
    //val tempRangeSourceDir = newTempDir().toPath.resolve("pos").toFile
    //tempRangeSourceDir.mkdirs()
    extractRanges(
      Path.of("/home/tobi/desktop/blub"),
      Path.of("/home/tobi/desktop/testing/pos"),
      "/home/tobi/desktop/testing/ranges"
    )
  } // */
}
