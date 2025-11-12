import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.platform.templates.github.{DownloadUtil, ZipUtil => GithubZipUtil}
import com.intellij.pom.java.LanguageLevel
import junit.framework.TestCase
import org.jetbrains.plugins.scala.compiler.ScalaCompilerTestBase
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.lang.parser.scala3.imported.{Scala3ImportedParserTestConfig, Scala3ImportedParserTest_Move_Fixed_Tests}
import org.jetbrains.plugins.scala.lang.resolveSemanticDb.ReferenceComparisonTestBase.disambiguatedStoreFileNameForUppercaseNames
import org.jetbrains.plugins.scala.lang.resolveSemanticDb._
import org.jetbrains.plugins.scala.lang.resolveSemanticDb.configurations._
import org.jetbrains.plugins.scala.project.VirtualFileExt
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.sbt.lang.completion.UpdateScalacOptionsInfo
import org.junit.Assert.{assertEquals, assertTrue, fail}
import org.junit.runner.JUnitCore
import org.junit.runners.MethodSorters
import org.junit.{FixMethodOrder, Ignore, Test}

import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import scala.io.Source
import scala.jdk.CollectionConverters.ListHasAsScala
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

  /**
   * NOTE:
   * if it fails because there are compilation errors in [[dotty.tools.dotc.FromTastyTests.posTestFromTasty]]
   * add the failing tests to the patched blacklist file [[AfterUpdateDottyVersionScript.`pos-from-tasty.blacklist`]].
   * See `patchFile` usages.
   */
  @Test def test_2_Scala3ImportedParserTest_Import_FromDottyDirectory_LTS(): Unit =
    runScript(Script.FromTestCase(classOf[Scala3ImportedParserTest_Import_FromDottyDirectory_LTS]))
  @Test def test_3_Scala3ImportedParserTest_Import_FromDottyDirectory_Newest(): Unit =
    runScript(Script.FromTestCase(classOf[Scala3ImportedParserTest_Import_FromDottyDirectory_Newest]))

  @Test def test_4_Scala3ImportedParserTest_Move_Fixed_Tests(): Unit =
    runJUnit4Script(classOf[Scala3ImportedParserTest_Move_Fixed_Tests])

  /**
   * NOTE:
   * if it fails because there are compilation errors in [[dotty.tools.dotc.FromTastyTests.posTestFromTasty]]
   * add the failing tests to the patched blacklist file [[AfterUpdateDottyVersionScript.`pos-from-tasty.blacklist`]].
   * See `patchFile` usages.
   */
  @Test def test_5_Scala3ImportedSemanticDbTest_Import_FromDottyDirectory_LTS(): Unit =
    runScript(Script.FromTestCase(classOf[Scala3ImportedSemanticDbTest_Import_FromDottyDirectory_Scala3_LTS]))

  @Test def test_6_ReferenceComparisonTestsGenerator_LTS(): Unit =
    runScript(Script.FromTestCase(classOf[ReferenceComparisonTestsGenerator_Scala3.TestCase_Scala3_LTS]))

  /**
   * NOTE:
   * if it fails because there are compilation errors in [[dotty.tools.dotc.FromTastyTests.posTestFromTasty]]
   * add the failing tests to the patched blacklist file [[AfterUpdateDottyVersionScript.`pos-from-tasty.blacklist`]].
   * See `patchFile` usages.
   */
  @Test def test_7_Scala3ImportedSemanticDbTest_Import_FromDottyDirectory_Newest(): Unit =
    runScript(Script.FromTestCase(classOf[Scala3ImportedSemanticDbTest_Import_FromDottyDirectory_Scala3_Newest]))

  @Test def test_8_ReferenceComparisonTestsGenerator_Newest(): Unit =
    runScript(Script.FromTestCase(classOf[ReferenceComparisonTestsGenerator_Scala3.TestCase_Scala3_Newest]))



  @Test def test_9_UpdateScalacOptionsInfo(): Unit =
    runScript(Script.FromTestCase(classOf[UpdateScalacOptionsInfo.ScriptTestCase]))
}

object AfterUpdateDottyVersionScript {
  private val scala3_repo_lts_branch = "lts-3.3"
  private val scala3_repo_newest_branch = "release-3.7.1"

  class ScalaRepository private (branch: String) {
    lazy val path: Path =
      Paths.get(System.getProperty("java.io.tmpdir")) / s"aftertupdate-dotty-version-script-repo-download-$branch"

    lazy val `pos-from-tasty.blacklist`: Path = {
      val blackList = path.resolve("compiler/test/dotc/pos-from-tasty.blacklist")
      val excludeList = path.resolve("compiler/test/dotc/pos-from-tasty.excludelist")
      if (Files.isRegularFile(blackList))
        blackList
      else if (Files.isRegularFile(excludeList))
        excludeList
      else
        throw new AssertionError(s"The file $blackList or $excludeList does not exist")
    }

    private def prepare(): Path = {
      if (!Files.isDirectory(path)) {
        cloneRepository()
      } else {
        gitStashChanges(path)
      }
      path
    }

    private def cloneRepository(): Unit = {
      val url = "https://github.com/scala/scala3/"
      Files.createDirectories(path)
      clearDirectory(path)

      println(
        s"""Clone repository to: $path
           |Repository : $url
           |Branch     : $branch
           |""".stripMargin
      )

      val commands: Seq[String] =
        "git" :: "clone" :: "--branch" :: branch :: url :: "." :: "--depth=1" :: Nil

      val rc = Process(commands, path.toFile).!
      assert(rc == 0, s"Failed ($rc) to clone $url into $path")
    }
  }

  object ScalaRepository {
    def prepareBranch(branch: String): ScalaRepository = {
      val repo = new ScalaRepository(branch)
      repo.prepare()
      repo
    }
  }

  private var someTestAlreadyFailed = false

  private def runJUnit4Script(testClass: Class[?]): Unit = {
    val result = JUnitCore.runClasses(testClass)
    result.getFailures.asScala.headOption match {
      case Some(failure) =>
        throw failure.getException
      case None =>
    }
  }

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
    } catch {
      case t: Throwable =>
        someTestAlreadyFailed = true
        throw t
    }
  }

  private val testDataPath: Path = Paths.get(TestUtils.getTestDataPath)

  private def downloadRepository(url: String): Path = {
    val repoFile = newTempFile()
    DownloadUtil.downloadAtomically(new EmptyProgressIndicator, url, repoFile.toFile)

    val repoDir = newTempDir()
    GithubZipUtil.unzip(null, repoDir.toFile, repoFile.toFile, null, null, true)
    repoDir
  }

  //noinspection ScalaUnusedSymbol
  //might be used during local tests, e.g. if we use to reuse dotty repository and not clone it every time we run tests
  private def gitStashChanges(repository: Path): Unit = {
    //stash any modifications to repository
    val commands: Seq[String] = "git" :: "stash" :: Nil
    val rc = Process(commands, repository.toFile).!
    assert(rc == 0, s"Failed to stash changes in repository $repository")
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
      assertTrue(Path.of(sourceFile.toUri).exists)

      log("reading source file")
      val sourceContent = readFile(sourceFile)
      addFileToProjectSources(sourceFileName, sourceContent)
      log("compiling")
      compiler.make().assertNoProblems()

      val compileOutput = CompilerModuleExtension.getInstance(getModule).getCompilerOutputPath
      assertTrue("compilation output not found", compileOutput.exists())

      val folderWithClasses = compileOutput.toPath.resolve(packagePath).toFile
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
  class Scala3ImportedParserTest_Import_FromDottyDirectory_LTS
    extends Scala3ImportedParserTest_Import_FromDottyDirectory(Scala3ImportedParserTestConfig.LTS, scala3_repo_lts_branch)
  class Scala3ImportedParserTest_Import_FromDottyDirectory_Newest
    extends Scala3ImportedParserTest_Import_FromDottyDirectory(Scala3ImportedParserTestConfig.Newest, scala3_repo_newest_branch)

  abstract class Scala3ImportedParserTest_Import_FromDottyDirectory(config: Scala3ImportedParserTestConfig, branch: String)
    extends TestCase {

    val successDataPath = testDataPath / config.successDataDirectory
    val failDataPath = testDataPath / config.failDataDirectory
    val rangesPath = testDataPath / config.rangesDirectory

    def test(): Unit = {
      val repo = ScalaRepository.prepareBranch(branch)

      val srcDir = repo.path.resolve(Paths.get("tests", "pos")).toAbsolutePath

      clearDirectory(successDataPath)
      clearDirectory(failDataPath)
      clearDirectory(rangesPath)

      println("srcdir =  " + srcDir)
      println("faildir = " + failDataPath)

      Files.createDirectories(successDataPath)
      Files.createDirectories(failDataPath)
      Files.createDirectories(rangesPath)

      //val tempRangeSourceDir = Path.of("/home/tobi/desktop/testing/pos")
      val tempRangeSourceDir = newTempDir().resolve("pos")
      tempRangeSourceDir.toFile.mkdirs()

      patchTestBlacklist(repo)

      // No help.ranges is generated for the source file help.scala.
      // https://github.com/scala/scala3/blob/release-3.4.0/tests/pos/help.scala
      // TODO: Understand the problems with the help.scala and widen-union.scala tests.
      //       Adding them to the blacklist file fails the script.
      //       If we do not ignore the widen-union.scala test, it fails during the import from the Scala 3 repository.
      //       It gets categorized as a failing test. But then, when running `Scala3ImportedParserTest_Fail`, it
      //       complains that it doesn't fail and needs to be moved to the successful category of tests. When it is
      //       finally moved using `Scala3ImportedParserTest_Move_Fixed_Tests`, at the end `Scala3ImportedParserTest`
      //       that the test fails and needs to be moved back.
      def acceptFile(file: Path): Boolean = {
        val fileName = file.getFileName.toString.toLowerCase
        fileName.endsWith(".scala") && fileName != "help.scala" && fileName != "widen-union.scala"
      }

      val ignoreFilesWithContent = Seq(
        "-language:experimental",
        "import language.experimental",
        "import scala.language.experimental"
      )

      val blacklist = loadBlacklist(repo)
      var atLeastOneFileProcessed = false
      for (file <- allFilesIn(srcDir) if acceptFile(file) if !blacklist.contains(file.getFileName.toString))  {
        val target = failDataPath / file.toString.substring(srcDir.toString.length).replace(".scala", "++++test")
        val content = readFile(file)
          .replaceAll("[-]{5,}", "+") // <- some test files have comment lines with dashes which confuse junit

        if (!ignoreFilesWithContent.exists(content.contains)) {
          val targetFile = target.toFile

          val outputFileName = Iterator
            .iterate(targetFile)(_.getParentFile)
            .takeWhile(_ != null)
            .takeWhile(!_.isDirectory)
            .map(_.getName.replace('.', '_').replace("++++", "."))
            .toSeq
            .reverse
            .mkString("_")
          val outputPath = failDataPath / outputFileName
          val outputInRangeDir = tempRangeSourceDir.resolve(outputFileName.replaceFirst("test$", "scala"))
          println(file.toString + " -> " + outputPath)

          {
            val pw = new PrintWriter(outputPath.toFile)
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

      extractRanges(repo, tempRangeSourceDir)
    }

    /**
     * Runs the dotty test suite on the imported files and extracts ranges of syntax elements for each test file
     * This is done by patching multiple files in the dotty compiler/test source.
     * Most importantly we hook into the main parse function and traverse trees that were created there.
     *
     * @param repoPath path to the complete dotty source code
     * @param testFilePath path to a directory that contains all test files
     */
    private def extractRanges(repo: ScalaRepository, testFilePath: Path): Unit = {
      /* not needed anymore?
      // patch test source to not delete tasty files
      patchFile(
        repoPath.resolve("compiler/test/dotty/tools/vulpix/ParallelTesting.scala"),
        "shouldDelete = true",
        "shouldDelete = false"
      )*/

      // patch test source to take our own source files
      patchFile(
        repo.path.resolve("compiler/test/dotty/tools/dotc/FromTastyTests.scala"),
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
        repo.path.resolve("compiler/src/dotty/tools/dotc/parsing/Parsers.scala"),
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
           |  val fileName = "${normalisedPathSeparator1(rangesPath)}/" + source.name.replace(".scala", ".ranges")
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

      patchTestBlacklist(repo)

      {
        println(s"# Ranges directory: $rangesPath")
        Files.createDirectories(rangesPath)
        clearDirectory(rangesPath)
      }

      runSbt("testCompilation --from-tasty pos", repo.path)

      val allFilesInFailed = allFilesIn(failDataPath).toSet
      val allFilesInRanges = allFilesIn(rangesPath).toSet
      val blacklistedFileNames = loadBlacklist(repo)

      val allFilesInFailedSize = allFilesInFailed.size
      val allFilesInRangesSize = allFilesInRanges.size
      val blacklistedSize = blacklistedFileNames.size

      val diff = allFilesInFailedSize - allFilesInRangesSize
      if (diff != 0) {
        val namesInAllFilesInFailed = allFilesInFailed.map(_.getFileName.toString.stripSuffix(".test"))
        val namesInAllFilesInRanges = allFilesInRanges.map(_.getFileName.toString.stripSuffix(".ranges"))
        fail(
          s"""Condition failed
             |allFilesInFailedSize : $allFilesInFailedSize
             |allFilesInRangesSize : $allFilesInRangesSize
             |blacklisted          : $blacklistedSize
             |diff                 : $diff (${if (diff < 0) "Failed less then expected" else "Failed more then expected"})
             |
             |Files that are in allFilesInFailed but not in allFilesInRanges:
             |  ${(namesInAllFilesInFailed -- namesInAllFilesInRanges).mkString("\n  ")}
             |
             |Files that are in allFilesInRanges but not in allFilesInFailed:
             |  ${(namesInAllFilesInRanges -- namesInAllFilesInFailed).mkString("\n  ")}
             |
             |Blacklisted files:
             |  ${blacklistedFileNames.mkString("\n  ")}
             |""".stripMargin.trim)
      }
    }
  }


  /**
   * Imports semanticdb tests from the dotty repositiory
   */
  class Scala3ImportedSemanticDbTest_Import_FromDottyDirectory_Scala3_LTS
    extends Scala3ImportedSemanticDbTest_Import_FromDottyDirectory(ReferenceComparisonTestConfig_Scala3_LTS, scala3_repo_lts_branch)
  class Scala3ImportedSemanticDbTest_Import_FromDottyDirectory_Scala3_Newest
    extends Scala3ImportedSemanticDbTest_Import_FromDottyDirectory(ReferenceComparisonTestConfig_Scala3_Newest, scala3_repo_newest_branch)
  abstract class Scala3ImportedSemanticDbTest_Import_FromDottyDirectory(config: ReferenceComparisonTestConfig, branch: String)
    extends TestCase {

    def test(): Unit = {
      val repo = ScalaRepository.prepareBranch(branch)

      clearDirectory(config.sourcePath)
      clearDirectory(config.outPath)

      Files.createDirectories(config.sourcePath)
      Files.createDirectories(config.outPath)

      // we want synthetic symbols and setter symbols as well
      patchFile(
        repo.path.resolve("compiler/src/dotty/tools/dotc/semanticdb/ExtractSemanticDB.scala"),
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
        repo.path.resolve("compiler/test/dotty/tools/vulpix/ParallelTesting.scala"),
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
      try {
        patchFile(
          repo.path.resolve("compiler/test/dotty/tools/dotc/FromTastyTests.scala"),
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
      } catch {
        case _: Exception =>
          println(s"Failed to patch file: ${repo.path.resolve("compiler/test/dotty/tools/dotc/FromTastyTests.scala")}")
          println("Try again with excludelisted instead of blacklisted")
          patchFile(
            repo.path.resolve("compiler/test/dotty/tools/dotc/FromTastyTests.scala"),
            """
              |  @Test def runTestFromTasty: Unit = {
              |    // Can be reproduced with
              |    // > sbt
              |    // > scalac -Ythrough-tasty -Ycheck:all <source>
              |    // > scala Test
              |
              |    implicit val testGroup: TestGroup = TestGroup("runTestFromTasty")
              |    compileTastyInDir(s"tests${JFile.separator}run", defaultOptions,
              |      fromTastyFilter = FileFilter.exclude(TestSources.runFromTastyExcludelisted)
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
              |    //  fromTastyFilter = FileFilter.exclude(TestSources.runFromTastyExcludelisted)
              |    //).checkRuns()
              |  }
              |""".stripMargin
          )
      }

      patchTestBlacklist(repo)

      runSbt("testCompilation --from-tasty pos", repo.path)

      copyRecursively(repo.path.resolve("tests/pos"), config.sourcePath)

      val posOutDir = repo.path.resolve("out/posTestFromTasty/pos")
      assert(Files.isDirectory(posOutDir))

      val allOutputDirs = posOutDir.children()
      val duplicatedLowercaseNames = allOutputDirs
        .map(_.getFileName.toString.toLowerCase)
        .groupBy(identity)
        .flatMap {
          case (_, Seq(_)) => None
          case (name, _) => Some(name)
        }
        .toSet

      for (testOutPath <- allOutputDirs) {
        val dirName = testOutPath.getFileName.toString
        val disambiguatedName =
          if (duplicatedLowercaseNames.contains(dirName.toLowerCase)) {
            disambiguatedStoreFileNameForUppercaseNames(dirName)
          } else None
        val storeName = disambiguatedName.getOrElse(dirName + ".semdb")
        val storePath = config.outPath.resolve(storeName)
        val store = SemanticDbFromScalaMeta.fromSemanticDbPath(testOutPath)

        if (store.files.nonEmpty)
          Files.writeString(storePath, store.serialized)
      }
    }
  }

  private def scalaUltimateProjectDir: Path = {
    val file = Path.of(getClass.getProtectionDomain.getCodeSource.getLocation.getPath)
    file
      .getParent.getParent.getParent
      .getParent.getParent.getParent
  }

  //noinspection MutatorLikeMethodIsParameterless
  private def needDeleteTempFileOnExit = true

  private def newTempFile(): Path =
    FileUtilRt.createTempFile("imported-dotty-tests", "", needDeleteTempFileOnExit).toPath

  private def newTempDir(): Path =
    FileUtilRt.createTempDirectory("imported-dotty-tests", "", needDeleteTempFileOnExit).toPath

  private def allFilesIn(path: Path): Iterator[Path] = {
    if (!path.exists) Iterator.empty
    else if (!path.isDirectory) Iterator(path)
    else path.children().iterator.flatMap(allFilesIn)
  }

  private def clearDirectory(path: Path): Unit = {
    val file = path.toFile
    if (file.exists()) {
      assert(file.isDirectory)
      val files = file.listFiles()
      assert(files != null)
      files.map(_.toPath).foreach(deleteRecursively)
    }
    else {
      // probably the folder is already deleted in the previous script run
    }
  }

  private def deleteRecursively(path: Path): Unit = {
    if (Files.isDirectory(path))
      path.children().foreach(deleteRecursively)
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

  private def loadBlacklist(repo: ScalaRepository): Set[String] =
    linesInFile(repo.`pos-from-tasty.blacklist`)
      .filterNot(_.isBlank)
      .filterNot(_.startsWith("#"))
      .toSet
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

  private def patchTestBlacklist(repo: ScalaRepository): Unit = {
    // these files fail in dotty repository but are not added to the blacklist for some reason
    patchFile(
      repo.`pos-from-tasty.blacklist`,
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
        |# also very long
        |jzon
        |i19907_slow_1000_3.scala
        |i19907_slow_1000_4.scala
        |
        |#Fatal compiler crash when compiling: tests\pos\i15827.scala:
        |i15827.scala
        |
        |# update from 3.3.1 to 3.3.2:
        |extend-java-enum.scala
        |i13044.scala
        |i17230.bootstrap.scala
        |i7445b.scala
        |refinements.scala
        |typeclass-scaling.scala
        |
        |## update for 3.6.2
        |#Doesn't generate ranges for some reason
        |B_2.scala
        |i7045.scala
        |i8715.scala
        |
        |i18699.scala
        |i10929-new-syntax.scala
        |i20135.scala
        |cc-poly-source.scala
        |i19955a.scala
        |i18626.min1.scala
        |mt-scrutinee-widen3.scala
        |i21682.2.scala
        |i15029.orig.scala
        |10747-shapeless-min.scala
        |gears-probem-1.scala
        |i19001.case1.scala
        |reach-problem.scala
        |invariant-cc.scala
        |i18263.orig.scala
        |8647.scala
        |i15155.scala
        |i19009.case2.scala
        |i19001.case2.scala
        |i15827.scala
        |Tuple.Elem.scala
        |i15991.orig.scala
        |with-type-operator-3.4-migration.scala
        |i10242.scala
        |i9804.scala
        |parsercombinators-arrow.scala
        |precise-ctx-bound.scala
        |gears-probem.scala
        |typeclasses-this.scala
        |i15743.moregadt.scala
        |i19942.1.scala
        |i15926.contra.scala
        |i19570.orig.scala
        |singleton-ctx-bound.scala
        |dotty-experimental.scala
        |21400b.scala
        |i15926.min.scala
        |hylolib-cb-extract.scala
        |mt-redux-norm.perspective.scala
        |cc-poly-1.scala
        |i20237.scala
        |i16596.scala
        |i18867-3.3.scala
        |i19955b.scala
        |with-type-operator-3.3.scala
        |i19009.case3.scala
        |i20053b.scala
        |i18253.orig.scala
        |i10929.scala
        |i19570.min1.scala
        |i15926.extract.scala
        |i18867-3.4.scala
        |i21239.orig.scala
        |cc-ex-unpack.scala
        |cb-companion-joins.scala
        |parsercombinators-ctx-bounds.scala
        |reach-capability.scala
        |Buffer.scala
        |deferredSummon.scala
        |extend-java-enum.scala
        |parsercombinators-new-syntax.scala
        |i16596.orig.scala
        |i19009.case1.scala
        |i15177.hylolib.scala
        |i17257.min.scala
        |typeclasses-arrow.scala
        |polycap.scala
        |i13580.scala
        |parsercombinators-this.scala
        |i17395.scala
        |9757.scala
        |deferred-givens-singletons.scala
        |i21390.zio.scala
        |i21682.1.scala
        |infer-exists.scala
        |i16706.scala
        |boxmap-paper.scala
        |parsercombinators-givens.scala
        |Tuple.Drop.scala
        |i15523.avoid.scala
        |i17395-spec.ordered.scala
        |hylolib-extract.scala
        |cc-poly-source-capability.scala
        |i15274.orig.scala
        |cc-experimental.scala
        |i19001.case3.scala
        |given-syntax.scala
        |alphanumeric-infix-operator-compat
        |
        |i7851.scala
        |FromString-cb-companion.scala
        |hylolib-deferred-given-extract.scala
        |i18097.1.scala
        |typeclass-aggregates.scala
        |erased-soft-keyword.scala
        |parsercombinators-givens-2.scala
        |i18097.orig.scala
        |i21558.orig.scala
        |FromString-named.scala
        |experimental-flag.scala
        |erased-class-as-args.scala
        |TupleReverse.scala
        |cbproxy-expansion.scala
        |parent-refinement.scala
        |depclass-1.scala
        |precise-indexof.scala
        |i18097.2.scala
        |tailrec.scala
        |TupleReverseOnto.scala
        |FromString.scala
        |typeclasses.scala
        |i10848a.scala
        |i2941.scala
        |i17222.4.scala
        |preview-flag.scala
        |i14477.scala
        |packageObjectValues.scala
        |i17222.5.scala
        |i6199c.scala
        |i6199b.scala
        |i21981.orig.scala
        |""".stripMargin.trim
    )
  }
}
