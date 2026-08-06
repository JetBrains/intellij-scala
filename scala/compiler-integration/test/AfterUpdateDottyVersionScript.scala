import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.util.io.NioFiles
import junit.framework.TestCase
import junitparams.JUnitParamsRunner
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.lang.parser.scala3.imported.{Scala3ImportedParserTestConfig, Scala3ImportedParserTest_Move_Fixed_Tests_LTS, Scala3ImportedParserTest_Move_Fixed_Tests_LTS_3_9, Scala3ImportedParserTest_Move_Fixed_Tests_Newest}
import org.jetbrains.plugins.scala.lang.resolveSemanticDb.ReferenceComparisonTestBase.disambiguatedStoreFileNameForUppercaseNames
import org.jetbrains.plugins.scala.lang.resolveSemanticDb._
import org.jetbrains.plugins.scala.lang.resolveSemanticDb.configurations._
import org.jetbrains.plugins.scala.util.{Annotations, TestUtils}
import org.jetbrains.sbt.lang.completion.UpdateScalacOptionsInfo
import org.junit.Assert.fail
import org.junit.runner.{Computer, JUnitCore, RunWith, Runner}
import org.junit.runners.MethodSorters
import org.junit.runners.model.{FrameworkMethod, RunnerBuilder}
import org.junit.{FixMethodOrder, Ignore, Test}

import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption}
import scala.jdk.CollectionConverters.ListHasAsScala
import scala.jdk.StreamConverters.StreamHasToScala
import scala.sys.process.Process
import scala.util.Using

/**
 * Try running this script soon after a new Scala LTS or Scala Next version has been made public.
 *
 * NOTE: tests are used instead of `main` method,
 * because `BasePlatformTestCase` contains logic to run IDEA instance, to which we delegate some logic
 *
 * NOTE: we use `@FixMethodOrder(MethodSorters.NAME_ASCENDING)` to control the order of test execution
 */
@Ignore("for local running only")
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AfterUpdateDottyVersionScript {

  import AfterUpdateDottyVersionScript._

  /**
   * NOTE:
   * if it fails because there are compilation errors in [[dotty.tools.dotc.FromTastyTests.posTestFromTasty]]
   * add the failing tests to the patched blacklist file [[AfterUpdateDottyVersionScript.`pos-from-tasty.blacklist`]].
   * See `patchFile` usages.
   */
  @Test def test_1_Scala3ImportedParserTest_Import_FromDottyDirectory_LTS(): Unit =
    runScript(Script.FromTestCase(classOf[Scala3ImportedParserTest_Import_FromDottyDirectory_LTS]))

  @Test def test_2_Scala3ImportedParserTest_Import_FromDottyDirectory_Newest(): Unit =
    runScript(Script.FromTestCase(classOf[Scala3ImportedParserTest_Import_FromDottyDirectory_Newest]))

  @Test def test_3_Scala3ImportedParserTest_Import_FromDottyDirectory_LTS_3_9(): Unit =
    runScript(Script.FromTestCase(classOf[Scala3ImportedParserTest_Import_FromDottyDirectory_LTS_3_9]))

  @Test def test_4_Scala3ImportedParserTest_Move_Fixed_Tests(): Unit = {
    runJUnit4ParameterizedScript(classOf[Scala3ImportedParserTest_Move_Fixed_Tests_LTS])
    runJUnit4ParameterizedScript(classOf[Scala3ImportedParserTest_Move_Fixed_Tests_Newest])
    runJUnit4ParameterizedScript(classOf[Scala3ImportedParserTest_Move_Fixed_Tests_LTS_3_9])
  }

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

  /**
   * NOTE:
   * if it fails because there are compilation errors in [[dotty.tools.dotc.FromTastyTests.posTestFromTasty]]
   * add the failing tests to the patched blacklist file [[AfterUpdateDottyVersionScript.`pos-from-tasty.blacklist`]].
   * See `patchFile` usages.
   */
  @Test def test_9_Scala3ImportedSemanticDbTest_Import_FromDottyDirectory_LTS_3_9(): Unit =
    runScript(Script.FromTestCase(classOf[Scala3ImportedSemanticDbTest_Import_FromDottyDirectory_Scala3_LTS_3_9]))

  @Test def test_10_ReferenceComparisonTestsGenerator_LTS_3_9(): Unit =
    runScript(Script.FromTestCase(classOf[ReferenceComparisonTestsGenerator_Scala3.TestCase_Scala3_LTS_3_9]))

  @Test def test_11_UpdateScalacOptionsInfo(): Unit =
    runScript(Script.FromTestCase(classOf[UpdateScalacOptionsInfo.ScriptTestCase]))
}

object AfterUpdateDottyVersionScript {
  private val scala3_repo_lts_branch = "release-3.3.8"
  private val scala3_repo_newest_branch = "release-3.8.4"
  private val scala3_repo_lts_39_branch = "release-3.9.0"

  private val scala3_bootstrapped_module_name = "scala3-bootstrapped"

  class ScalaRepository private (branch: String) {
    lazy val path: Path = {
      val res = Path.of(System.getProperty("java.io.tmpdir")).toRealPath() / s"after-update-dotty-version-script-repo-download-$branch"
      NioFiles.deleteRecursively(res)
      res
    }

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

    lazy val `run-from-tasty.excludelist`: Path =
      path.resolve("compiler/test/dotc/run-from-tasty.excludelist")

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

      // .toFile must be used because of the Scala Process API
      //noinspection SSBasedInspection
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

  private def runJUnit4ParameterizedScript(testClass: Class[?]): Unit = {
    Annotations.findAnnotation(testClass, classOf[RunWith]) match {
      case Some(annotation) =>
        val correct = annotation.value() == classOf[JUnitParamsRunner]
        if (!correct) {
          sys.error(s"The test class ${testClass.getName} must be annotated with `@RunWith(classOf[JUnitParamsRunner])`")
        }
      case None =>
        sys.error(s"The test class ${testClass.getName} must be annotated with `@RunWith(classOf[JUnitParamsRunner])`")
    }

    // A hack to ignore the @Ignore annotation, otherwise the test class would not be executed by the JUnit 4 machinery.
    val computer = new Computer() {
      override def getRunner(builder: RunnerBuilder, testClass: Class[?]): Runner = new JUnitParamsRunner(testClass) {
        override def isIgnored(child: FrameworkMethod): Boolean = false
      }
    }

    val result = JUnitCore.runClasses(computer, testClass)
    result.getFailures.asScala.headOption match {
      case Some(failure) => throw failure.getException
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

  private val testDataPath: Path = Path.of(TestUtils.getTestDataPath)

  //noinspection ScalaUnusedSymbol
  //might be used during local tests, e.g. if we use to reuse dotty repository and not clone it every time we run tests
  private def gitStashChanges(repository: Path): Unit = {
    //stash any modifications to repository
    val commands: Seq[String] = "git" :: "stash" :: Nil
    // .toFile must be used because of the Scala Process API
    //noinspection SSBasedInspection
    val rc = Process(commands, repository.toFile).!
    assert(rc == 0, s"Failed to stash changes in repository $repository")
  }

  /**
   * Imports Tests from the dotty repositiory
   */
  class Scala3ImportedParserTest_Import_FromDottyDirectory_LTS
    extends Scala3ImportedParserTest_Import_FromDottyDirectory(Scala3ImportedParserTestConfig.LTS, scala3_repo_lts_branch, scala3_bootstrapped_module_name)
  class Scala3ImportedParserTest_Import_FromDottyDirectory_Newest
    extends Scala3ImportedParserTest_Import_FromDottyDirectory(Scala3ImportedParserTestConfig.Newest, scala3_repo_newest_branch, scala3_bootstrapped_module_name)
  class Scala3ImportedParserTest_Import_FromDottyDirectory_LTS_3_9
    extends Scala3ImportedParserTest_Import_FromDottyDirectory(Scala3ImportedParserTestConfig.LTS_3_9, scala3_repo_lts_39_branch, scala3_bootstrapped_module_name)

  abstract class Scala3ImportedParserTest_Import_FromDottyDirectory(config: Scala3ImportedParserTestConfig, branch: String, sbtTestModule: String)
    extends TestCase {

    val successDataPath = testDataPath / config.successDataDirectory
    val failDataPath = testDataPath / config.failDataDirectory
    val rangesPath = testDataPath / config.rangesDirectory

    def test(): Unit = {
      val repo = ScalaRepository.prepareBranch(branch)

      val srcDir = repo.path.resolve(Path.of("tests", "pos")).toAbsolutePath

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
      Files.createDirectories(tempRangeSourceDir)

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
      for (file <- allFilesIn(srcDir) if acceptFile(file) if !blacklist.contains(file.getFileName.toString)) {
        val target = failDataPath / replaceLast(file.toString.substring(srcDir.toString.length), ".scala", "++++test")
        val content = readFile(file)
          .replaceAll("[-]{5,}", "+") // <- some test files have comment lines with dashes which confuse junit

        if (!ignoreFilesWithContent.exists(content.contains)) {
          val outputFileName = Iterator
            .iterate(target)(_.getParent)
            .takeWhile(_ != null)
            .takeWhile(!_.isDirectory)
            .map(_.getFileName.toString.replace('.', '_').replace("++++", "."))
            .toSeq
            .reverse
            .mkString("_")
          val outputPath = failDataPath / outputFileName
          val outputInRangeDir = tempRangeSourceDir.resolve(outputFileName.replaceFirst("test$", "scala"))
          println(file.toString + " -> " + outputPath)

          {
            Using.resource(new PrintWriter(Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8))) { pw =>
              pw.write(content)
              if (content.last != '\n')
                pw.write('\n')
              pw.println("-----")
            }
          }

          // print it into a temporary directory which we can use to run sbt tests on
          {
            Files.writeString(outputInRangeDir, content, StandardCharsets.UTF_8)
          }
          atLeastOneFileProcessed = true
        }
      }
      if (!atLeastOneFileProcessed)
        throw new AssertionError("No files were processed")

      extractRanges(repo, tempRangeSourceDir)
    }

    private def replaceLast(string: String, target: String, replacement: String): String = {
      val index = string.lastIndexOf(target)
      if (index == -1) return string
      string.substring(0, index) ++ replacement
    }

    /**
     * Runs the dotty test suite on the imported files and extracts ranges of syntax elements for each test file
     * This is done by patching multiple files in the dotty compiler/test source.
     * Most importantly we hook into the main parse function and traverse trees that were created there.
     *
     * @param repo path to the complete dotty source code
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

      runSbt(s"$sbtTestModule/testCompilation --from-tasty pos", repo.path)

      val allFilesInFailed = allFilesIn(failDataPath).toSet.filterNot(p => config.extraFilesInFailedIgnore(p.getFileName.toString))
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
    extends Scala3ImportedSemanticDbTest_Import_FromDottyDirectory(ReferenceComparisonTestConfig_Scala3_LTS, scala3_repo_lts_branch, scala3_bootstrapped_module_name)
  class Scala3ImportedSemanticDbTest_Import_FromDottyDirectory_Scala3_Newest
    extends Scala3ImportedSemanticDbTest_Import_FromDottyDirectory(ReferenceComparisonTestConfig_Scala3_Newest, scala3_repo_newest_branch, scala3_bootstrapped_module_name)
  class Scala3ImportedSemanticDbTest_Import_FromDottyDirectory_Scala3_LTS_3_9
    extends Scala3ImportedSemanticDbTest_Import_FromDottyDirectory(ReferenceComparisonTestConfig_Scala3_LTS_3_9, scala3_repo_lts_39_branch, scala3_bootstrapped_module_name)
  abstract class Scala3ImportedSemanticDbTest_Import_FromDottyDirectory(config: ReferenceComparisonTestConfig, branch: String, sbtTestModule: String)
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

      runSbt(s"$sbtTestModule/testCompilation --from-tasty pos", repo.path)

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

  private def newTempDir(): Path = {
    val dir = Files.createTempDirectory("imported-dotty-tests")
    if (needDeleteTempFileOnExit) {
      Runtime.getRuntime.addShutdownHook(new Thread(() => NioFiles.deleteRecursively(dir)))
    }
    dir
  }

  private def allFilesIn(path: Path): Iterator[Path] = {
    if (!path.exists) Iterator.empty
    else if (!path.isDirectory) Iterator(path)
    else path.children().iterator.flatMap(allFilesIn)
  }

  private def clearDirectory(path: Path): Unit = {
    if (path.exists) {
      assert(path.isDirectory)
      val files = path.children()
      assert(files != null)
      files.foreach(NioFiles.deleteRecursively)
    }
    else {
      // probably the folder is already deleted in the previous script run
    }
  }

  private def copyRecursively(source: Path, target: Path): Unit =
    Using.resource(Files.walk(source))(
      _.forEachOrdered { sourcePath =>
        Files.copy(sourcePath, target.resolve(source.relativize(sourcePath)), StandardCopyOption.REPLACE_EXISTING)
      }
    )

  sealed trait Script
  object Script {
    final case class FromTestCase(clazz: Class[? <: TestCase]) extends Script
  }

  private def runSbt(cmdline: String, dir: Path): Unit = {
    val jdkDirectory = SmartJDKLoader.discoverJDK(JavaSdkVersion.JDK_21) match {
      case Some(directory) => directory
      case None => sys.error("JDK 21 must be installed on the machine")
    }

    println(
      s"""### Running sbt command: $cmdline
         |### in directory: $dir""".stripMargin
    )
    val isWindows = System.getProperty("os.name").toLowerCase.contains("win")
    val sbtExecutable = if (isWindows) "sbt.bat" else "sbt"
    // .toFile must be used because of the Scala Process API
    //noinspection SSBasedInspection
    val process = Process(sbtExecutable :: "--java-home" :: jdkDirectory.toCanonicalPath.toString :: cmdline :: Nil, dir.toFile)
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
    Files.writeString(path, newContent, StandardCharsets.UTF_8)
  }

  private def linesInFile(path: Path): Seq[String] =
    Files.lines(path, StandardCharsets.UTF_8).toScala(List)

  private def readFile(path: Path): String =
    Files.readString(path, StandardCharsets.UTF_8)

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
        |# also very long
        |jzon
        |i19907_slow_1000_3.scala
        |i19907_slow_1000_4.scala
        |
        |# lts-3.3 and 3.8.2
        |i18263.orig.scala
        |i15827.scala
        |i15743.moregadt.scala
        |i19955b.scala
        |extend-java-enum.scala
        |i13044.scala
        |i24543.scala
        |i23225.scala
        |i17230.bootstrap.scala
        |i21390.zio.scala
        |i19955a.scala
        |i21682.1.scala
        |i15523.avoid.scala
        |i15274.orig.scala
        |i21682.2.scala
        |i15029.orig.scala
        |refinements.scala
        |i15155.scala
        |i15991.orig.scala
        |typeclass-scaling.scala
        |i20053b.scala
        |i18253.orig.scala
        |i21558.orig.scala
        |i7445b.scala
        |alphanumeric-infix-operator-compat
        |i18533
        |t12396
        |i20078
        |
        |i7851.scala
        |singleton-ctx-bound.scala
        |typeclasses-arrow.scala
        |FromString-named.scala
        |deferred-givens-singletons.scala
        |i11064.scala
        |i16706.scala
        |parsercombinators-givens.scala
        |hylolib-extract.scala
        |mt-scrutinee-widen3.scala
        |10747-shapeless-min.scala
        |TupleReverse.scala
        |cbproxy-expansion.scala
        |i23489.scala
        |t8367.scala
        |8647.scala
        |FromString-cb-companion.scala
        |preview-flag.scala
        |hylolib-deferred-given-extract.scala
        |depclass-1.scala
        |into-bigint.scala
        |parent-refinement.scala
        |erased-pathdep-1.scala
        |i10242.scala
        |i9804.scala
        |parsercombinators-arrow.scala
        |precise-ctx-bound.scala
        |typeclasses-this.scala
        |21400b.scala
        |typeclass-aggregates.scala
        |hylolib-cb-extract.scala
        |precise-indexof.scala
        |i16596.scala
        |tailrec.scala
        |i10929-new-syntax.scala
        |packageObjectValues.scala
        |into-class.scala
        |t5031_2.scala
        |TupleReverseOnto.scala
        |i23398.scala
        |i10929.scala
        |cb-companion-joins.scala
        |parsercombinators-ctx-bounds.scala
        |erased-soft-keyword.scala
        |deferredSummon.scala
        |parsercombinators-new-syntax.scala
        |parsercombinators-givens-2.scala
        |i15177.hylolib.scala
        |into-sam.scala
        |i13580.scala
        |parsercombinators-this.scala
        |i17395.scala
        |into-expr.scala
        |9757.scala
        |FromString.scala
        |typeclasses.scala
        |i10848a.scala
        |experimental-flag.scala
        |cc-use-alternatives.scala
        |i10256.scala
        |given-syntax.scala
        |
        |# release-3.8.4
        |i23317.scala
        |i24990.scala
        |
        |# release-3.9.0
        |i25644.scala
        |i18234.scala
        |t8244d
        |""".stripMargin.trim
    )

    //patchFile(
    //  repo.`run-from-tasty.excludelist`,
    //  """# CI only: cannot reduce summonFrom with
    //    |sip23-valueof.scala""".stripMargin,
    //  """# CI only: cannot reduce summonFrom with
    //    |sip23-valueof.scala
    //    |
    //    |# 3.8.0
    //    |backwardsCompat-implicitParens
    //    |""".stripMargin
    //)
  }
}
