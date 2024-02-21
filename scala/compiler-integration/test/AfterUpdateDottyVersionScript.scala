import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.platform.templates.github.{DownloadUtil, ZipUtil => GithubZipUtil}
import com.intellij.pom.java.LanguageLevel
import junit.framework.{TestCase, TestFailure, TestResult, TestSuite}
import org.jetbrains.plugins.scala.compiler.ScalaCompilerTestBase
import org.jetbrains.plugins.scala.lang.parser.scala3.imported.{Scala3ImportedParserTest, Scala3ImportedParserTest_Move_Fixed_Tests}
import org.jetbrains.plugins.scala.lang.resolveSemanticDb.{ComparisonTestBase, ReferenceComparisonTestsGenerator_Scala3, SemanticDbStore}
import org.jetbrains.plugins.scala.project.VirtualFileExt
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.sbt.lang.completion.UpdateScalacOptionsInfo
import org.junit.Assert.{assertEquals, assertTrue, fail}
import org.junit.{FixMethodOrder, Ignore, Test}
import org.junit.runner.JUnitCore
import org.junit.runners.MethodSorters

import java.io.{File, PrintWriter}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import scala.io.Source
import scala.jdk.CollectionConverters.{EnumerationHasAsScala, IteratorHasAsScala, ListHasAsScala}
import scala.sys.process.Process
import scala.util.Using

/**
 * NOTE: tests are used instead of `main` method,
 * because `BasePlatformTestCase` contains logic to run IDEA instance, to which we delegate some logic
 *
 * NOTE: we use `@FixMethodOrder(MethodSorters.NAME_ASCENDING)` to control the order of test execution
 */
@Ignore("for local running only")
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AfterUpdateDottyVersionScript {

  import AfterUpdateDottyVersionScript._

  @Test def test_1_RecompileMacroPrinter3(): Unit =
    runScript(Script.FromTestCase(classOf[RecompileMacroPrinter3]))

  @Test def test_2_CloneDottyRepository(): Unit = {
    // we have to clone the repo because it needs a git history
    //example of release branch: release-3.1.3
    val branch = "release-" + ScalaVersion.Latest.Scala_3.minor
    repoPath = cloneRepository("https://github.com/lampepfl/dotty/", Some(branch)).toPath
  }

  @Test def test_3_Scala3ImportedParserTest_Import_FromDottyDirectory(): Unit =
    runScript(Script.FromTestCase(classOf[Scala3ImportedParserTest_Import_FromDottyDirectory]))

  @Test def test_4_Scala3ImportedParserTest_Move_Fixed_Tests(): Unit =
    runScript(Script.FromTestSuite(new Scala3ImportedParserTest_Move_Fixed_Tests.Scala3ImportedParserTest_Move_Fixed_Tests))

  @Test def test_5_Scala3ImportedSemanticDbTest_Import_FromDottyDirectory(): Unit =
    runScript(Script.FromTestCase(classOf[Scala3ImportedSemanticDbTest_Import_FromDottyDirectory]))

  @Test def test_6_ReferenceComparisonTestsGenerator(): Unit =
    runScript(Script.FromTestCase(classOf[ReferenceComparisonTestsGenerator_Scala3.ScriptTestCase]))

  @Test def test_7_UpdateScalacOptionsInfo(): Unit =
    runScript(Script.FromTestCase(classOf[UpdateScalacOptionsInfo.ScriptTestCase]))
}

object AfterUpdateDottyVersionScript {

  //Is initialized during one of the tests run
  private var repoPath: Path = _
  private lazy val `pos-from-tasty.blacklist` =
    repoPath.resolve("compiler/test/dotc/pos-from-tasty.blacklist")

  private var someTestAlreadyFailed = false

  private def runScript(script: Script): Unit = {
    if (someTestAlreadyFailed) {
      fail("Previous step failed. Skipping current step.")
    }

    try script match {
      case Script.FromTestCase(clazz) =>
        val classSimpleName = clazz.getSimpleName
        val result = new JUnitCore().run(clazz)
        if (result.getIgnoreCount > 0) {
          fail(s"Don't expect ignored tests for $classSimpleName")
        }
        result.getFailures.asScala.headOption match {
          case Some(failure) =>
            throw failure.getException
          case None =>
        }

      case Script.FromTestSuite(suite) =>
        val result = new TestResult
        suite.run(result)
        result.stop()

        val problems = (result.errors().asScala.toList ++ result.failures().asScala.toList)
          .asInstanceOf[List[TestFailure]] // It can't be compiled on TC by some reason. So we need asInstanceOf here.
        problems.headOption match {
          case Some(problem) =>
            throw problem.thrownException()
          case None =>
        }
    } catch {
      case t: Throwable =>
        someTestAlreadyFailed = true
        throw t
    }
  }


  import Scala3ImportedParserTest_Move_Fixed_Tests.{dottyParserTestsFailDir, dottyParserTestsSuccessDir}
  private val rangesDirectory: String = TestUtils.getTestDataPath + Scala3ImportedParserTest.rangesDirectory

  private def downloadRepository(url: String): File = {
    val repoFile = newTempFile()
    DownloadUtil.downloadAtomically(new EmptyProgressIndicator, url, repoFile)

    val repoDir = newTempDir()
    GithubZipUtil.unzip(null, repoDir, repoFile, null, null, true)
    repoDir
  }

  //noinspection ScalaUnusedSymbol
  //might be used during local tests, e.g. if we use to reuse dotty repository and not clone it every time we run tests
  private def gitStashChanges(repository: File): Unit = {
    //stash any modifications to repository
    val commands: Seq[String] = "git" :: "stash" :: Nil
    val rc = Process(commands, repository).!
    assert(rc == 0, s"Failed to stash changes in repository $repository")
  }

  private def cloneRepository(url: String, branchOpt: Option[String]): File = {
    val cloneDir = newTempDir()
    println(
      s"""Clone repository to: $cloneDir
         |Repository : $url
         |Branch     : ${branchOpt.orNull}
         |""".stripMargin
    )

    val branchOption: List[String] = branchOpt match {
      case Some(branch) => "--branch" :: branch :: Nil
      case None => Nil
    }
    val commands: Seq[String] =
      "git" :: "clone" :: branchOption ::: url :: "." :: "--depth=1" :: Nil

    val rc = Process(commands, cloneDir).!
    assert(rc == 0, s"Failed ($rc) to clone $url into $cloneDir")
    cloneDir
  }

  /**
   * Recompile some classes needed in tests
   */
  class RecompileMacroPrinter3
    extends ScalaCompilerTestBase {

    /** For now looks like MacroPrinter3 compiled for Scala 3.0 works for Scala 3.1 automatically */
    override protected def supportedIn(version: ScalaVersion): Boolean =
      version == LatestScalaVersions.Scala_3_0

    override protected val includeCompilerAsLibrary: Boolean = true

    override def testProjectJdkVersion = LanguageLevel.JDK_17

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
   */
  class Scala3ImportedParserTest_Import_FromDottyDirectory
    extends TestCase {

    def test(): Unit = {
      gitStashChanges(repoPath.toFile)

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

      // No help.ranges is generated for the source file help.scala.
      // https://github.com/lampepfl/dotty/blob/release-3.4.0/tests/pos/help.scala
      def acceptFile(file: File): Boolean = {
        val fileName = file.getName.toLowerCase
        fileName.endsWith(".scala") && fileName != "help.scala"
      }

      var atLeastOneFileProcessed = false
      for (file <- allFilesIn(srcDir) if acceptFile(file))  {
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


  /**
   * Imports semanticdb tests from the dotty repositiory
   */
  class Scala3ImportedSemanticDbTest_Import_FromDottyDirectory
    extends TestCase {

    def test(): Unit = {
      gitStashChanges(repoPath.toFile) //stash changes from previous tests run (it modifies some files)

      clearDirectory(ComparisonTestBase.sourcePath.toString)
      clearDirectory(ComparisonTestBase.outPath.toString)

      Files.createDirectories(ComparisonTestBase.sourcePath)
      Files.createDirectories(ComparisonTestBase.outPath)

      // we want synthetic symbols and setter symbols as well
      patchFile(
        repoPath.resolve("compiler/src/dotty/tools/dotc/semanticdb/ExtractSemanticDB.scala"),
        """    private def excludeDef(sym: Symbol)(using Context): Boolean =
          |      !sym.exists
          |      || sym.isLocalDummy
          |      // basically do not register synthetic symbols, except anonymous class
          |      // `new Foo { ... }`
          |      || (sym.is(Synthetic) && !sym.isAnonymousClass)
          |      || sym.isSetter
          |      || sym.isOldStyleImplicitConversion(forImplicitClassOnly = true)
          |      || sym.owner.isGivenInstanceSummoner
          |      || excludeDefOrUse(sym)
          |""".stripMargin,
        """    private def excludeDef(sym: Symbol)(using Context): Boolean =
          |      !sym.exists
          |      || sym.isLocalDummy
          |      // basically do not register synthetic symbols, except anonymous class
          |      // `new Foo { ... }`
          |      //|| (sym.is(Synthetic) && !sym.isAnonymousClass)
          |      //|| sym.isSetter
          |      //|| sym.isOldStyleImplicitConversion(forImplicitClassOnly = true)
          |      //|| sym.owner.isGivenInstanceSummoner
          |      || excludeDefOrUse(sym)
          |""".stripMargin
      )

      // do not delete test output files
      patchFile(
        repoPath.resolve("compiler/test/dotty/tools/vulpix/ParallelTesting.scala"),
        """    val generateClassFiles = compileFilesInDir(f, flags0, fromTastyFilter)
          |
          |    new TastyCompilationTest(
          |      generateClassFiles.keepOutput,
          |      new CompilationTest(targets).keepOutput,
          |      shouldDelete = true
          |    )
          |""".stripMargin,
        """    val generateClassFiles = compileFilesInDir(f, flags0, fromTastyFilter)
          |
          |    new TastyCompilationTest(
          |      generateClassFiles.keepOutput,
          |      new CompilationTest(targets).keepOutput,
          |      shouldDelete = false // <- changes here
          |    )
          |""".stripMargin
      )

      // no need to run the run-tests... posTestFromTasty already creates the semanticdb files
      patchFile(
        repoPath.resolve("compiler/test/dotty/tools/dotc/FromTastyTests.scala"),
        """
          |  @Test def runTestFromTasty: Unit = {
          |    // Can be reproduced with
          |    // > sbt
          |    // > scalac -Ythrough-tasty -Ycheck:all <source>
          |    // > scala Test
          |
          |    implicit val testGroup: TestGroup = TestGroup("runTestFromTasty")
          |    compileTastyInDir(s"tests${JFile.separator}run", defaultOptions,
          |      fromTastyFilter = FileFilter.exclude(TestSources.runFromTastyBlacklisted)
          |    ).checkRuns()
          |  }
          |""".stripMargin,
        """
          |  @Test def runTestFromTasty: Unit = {
          |    // Can be reproduced with
          |    // > sbt
          |    // > scalac -Ythrough-tasty -Ycheck:all <source>
          |    // > scala Test
          |
          |    //implicit val testGroup: TestGroup = TestGroup("runTestFromTasty")
          |    //compileTastyInDir(s"tests${JFile.separator}run", defaultOptions,
          |    //  fromTastyFilter = FileFilter.exclude(TestSources.runFromTastyBlacklisted)
          |    //).checkRuns()
          |  }
          |""".stripMargin
      )

      // these files fail in dotty repository since 3.2
      patchFile(
        `pos-from-tasty.blacklist`,
        """# Tree is huge and blows stack for printing Text
          |i7034.scala""".stripMargin,
        """# Tree is huge and blows stack for printing Text
          |i7034.scala
          |
          |# class i15274.orig$package cannot be unpickled because no class file was found for denot: val <none>
          |i15274.orig.scala
          |
          |# class i15743.moregadt$package cannot be unpickled because no class file was found for denot: val <none>
          |i15743.moregadt.scala
          |
          |# class i15991.orig$package cannot be unpickled because no class file was found for denot: val <none>
          |i15991.orig.scala
          |
          |# EnumValue[E] is not a class
          |i15155.scala
          |
          |#class i15523.avoid$package cannot be unpickled because no class file was found for denot: val <none>
          |i15523.avoid.scala
          |
          |#class i15029.orig$package cannot be unpickled because no class file was found for denot: val <none>
          |i15029.orig.scala
          |
          |#Fatal compiler crash when compiling: tests\pos\i16785.scala:
          |i16785.scala
          |
          |#Fatal compiler crash when compiling: tests\pos\i15827.scala:
          |i15827.scala
          |""".stripMargin.trim
      )

      runSbt("testCompilation --from-tasty pos", repoPath)

      copyRecursively(repoPath.resolve("tests/pos"), ComparisonTestBase.sourcePath)

      val posOutDir = repoPath.resolve("out/posTestFromTasty/pos")
      assert(Files.isDirectory(posOutDir))

      for (testOutPath <- Files.list(posOutDir).iterator().asScala) {
        val dirName = testOutPath.getFileName.toString
        val storePath = ComparisonTestBase.outPath.resolve(dirName + ".semdb")

        val store = SemanticDbStore.fromSemanticDbPath(testOutPath)

        if (store.files.nonEmpty)
          Files.writeString(storePath, store.serialized)
      }
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
  private def needDeleteTempFileOnExit = true

  private def newTempFile(): File =
    FileUtilRt.createTempFile("imported-dotty-tests", "", needDeleteTempFileOnExit)

  private def newTempDir(): File =
    FileUtilRt.createTempDirectory("imported-dotty-tests", "", needDeleteTempFileOnExit)

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
      files.map(_.toPath).foreach(deleteRecursively)
    }
    else {
      // probably the folder is already deleted in the previous script run
    }
  }

  private def deleteRecursively(path: Path): Unit = {
    if (Files.isDirectory(path))
      Files.list(path).forEach(deleteRecursively)
    Files.delete(path)
  }

  private def copyRecursively(source: Path, target: Path): Unit =
    Using.resource(Files.walk(source))(
      _.forEachOrdered { sourcePath =>
        Files.copy(sourcePath, target.resolve(source.relativize(sourcePath)), StandardCopyOption.REPLACE_EXISTING)
      }
    )

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
      """compileTastyInDir(s"tests${JFile.separator}pos"""",
      s"""compileTastyInDir(${s""""${normalisedPathSeparator1(testFilePath)}""""}"""
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
         |  val fileName = "${normalisedPathSeparator1(targetRangeDirectory)}/" + source.name.replace(".scala", ".ranges")
         |  val w = new java.io.PrintWriter(fileName, java.nio.charset.StandardCharsets.UTF_8)
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
      println(s"# Ranges directory: $rangesDirectory")
      val file = new File(rangesDirectory)
      if (!file.exists()) {
        assert(file.mkdirs() && file.exists(), "Can't create ranges directory")
      }
      clearDirectory(rangesDirectory)
    }

    runSbt("testCompilation --from-tasty pos", repoPath)

    val allFilesInFailed = allFilesIn(dottyParserTestsFailDir).toSet
    val allFilesInRanges = allFilesIn(rangesDirectory).toSet
    val blacklistedFileNames = linesInFile(`pos-from-tasty.blacklist`)
      .filterNot(_.isBlank)
      .filterNot(_.startsWith("#"))

    val allFilesInFailedSize = allFilesInFailed.size
    val allFilesInRangesSize = allFilesInRanges.size
    val blacklistedSize = blacklistedFileNames.size

    val diff = allFilesInFailedSize - blacklistedSize - allFilesInRangesSize
    if (diff != 0) {
      fail(
        s"""Condition failed
           |allFilesInFailedSize : $allFilesInFailedSize
           |allFilesInRangesSize : $allFilesInRangesSize
           |blacklisted          : $blacklistedSize
           |diff                 : $diff (${if (diff < 0) "Failed less then expected" else "Failed more then expected"})
           |Blacklisted files:
           |  ${blacklistedFileNames.mkString("\n  ")}
           |""".stripMargin.trim)
    }
  }

  private def runSbt(cmdline: String, dir: Path): Unit = {
    println(
      s"""### Running sbt command: $cmdline
         |### in directory: $dir""".stripMargin
    )
    val isWindows = System.getProperty("os.name").toLowerCase.contains("win")
    val sbtExecutable = if (isWindows) "sbt.bat" else "sbt"
    val process = Process(sbtExecutable :: cmdline :: Nil, dir.toFile)
    val sc2 = process.!
    assert(sc2 == 0, s"sbt failed with exit code $sc2")
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
    val content = readFile(path).replace("\r", "")
    if (!content.contains(searchString) && !content.contains(replacement)) {
      throw new Exception(
        s"""Couldn't patch file $path because expected string was not found in the content
           |Expected string: `$searchString`
           |Alternative expected string: `$replacement`
           |""".stripMargin.trim)
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
