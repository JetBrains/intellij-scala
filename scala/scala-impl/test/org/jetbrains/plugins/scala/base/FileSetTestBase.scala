package org.jetbrains.plugins.scala.base

import com.intellij.application.options.CodeStyle
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.{PsiFile, PsiFileFactory}
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import junitparams.naming.TestCaseName
import junitparams.{JUnitParamsRunner, Parameters}
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.{FileSetTests, ScalaLanguage}
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.regex.Pattern
import scala.annotation.unused

/**
 * Use this base class when writing file set tests
 * (tests which create a test case for each test file in a test data directory)
 * which do not need a Scala SDK to be configured to run.
 * This significantly cuts down on test setup time.
 *
 * @note This needs to be an abstract class, otherwise the @RunWith annotation will be ignored,
 *       and the test will be run as a JUnit 3 test, which will fail.
 */
@RunWith(classOf[JUnitParamsRunner])
@Category(Array(classOf[FileSetTests]))
abstract class NoSdkFileSetTestBase extends LightJavaCodeInsightFixtureTestCase with FileSetTestBase {
  override protected def project: Project = getProject

  /**
   * This method needs to be defined in a class, otherwise the @Parameters annotation will not be able to find it.
   */
  @unused("used reflectively by the @Parameters annotation")
  private def testParameters: Array[AnyRef] = baseTestParameters

  /**
   * This @Test method needs to be defined in a class, otherwise the `JUnitParamsRunner` will not be able to find it
   * and run it as a test.
   */
  @Test
  @Parameters(method = "testParameters")
  @TestCaseName(value = "{method}[{0}]")
  def noSdkFileSetTest(@unused("used reflectively by the @TestCaseName annotation") testName: String, testFile: Path): Unit = {
    baseFileSetTest(testFile)
  }
}

/**
 * Use this base class when writing file set tests
 * (tests which create a test case for each test file in a test data directory)
 * which need a Scala SDK to be configured to run.
 *
 * @note This needs to be an abstract class, otherwise the @RunWith annotation will be ignored,
 *       and the test will be run as a JUnit 3 test, which will fail.
 */
@RunWith(classOf[JUnitParamsRunner])
@Category(Array(classOf[FileSetTests]))
abstract class SdkFileSetTestBase extends ScalaLightCodeInsightFixtureTestCase with FileSetTestBase {
  override protected def project: Project = getProject

  /**
   * This method needs to be defined in a class, otherwise the @Parameters annotation will not be able to find it.
   */
  @unused("used reflectively by the @Parameters annotation")
  private def testParameters: Array[AnyRef] = baseTestParameters

  /**
   * This @Test method needs to be defined in a class, otherwise the `JUnitParamsRunner` will not be able to find it
   * and run it as a test.
   */
  @Test
  @Parameters(method = "testParameters")
  @TestCaseName(value = "{method}[{0}]")
  def sdkFileSetTest(@unused("used reflectively by the @TestCaseName annotation") testName: String, testFile: Path): Unit = {
    baseFileSetTest(testFile)
  }
}

sealed trait FileSetTestBase extends FailableTest { self: LightJavaCodeInsightFixtureTestCase =>

  protected def project: Project

  protected def relativeTestDataPath: Path

  protected def baseTestDataPath: Path = TestUtils.getTestDataDir

  protected def language: Language = ScalaLanguage.INSTANCE

  protected def testFileExtensions: Seq[String] = Seq(".test")

  protected def baseTestParameters: Array[AnyRef] = {
    val testDirectoryPath: Path = baseTestDataPath / relativeTestDataPath
    findTestFiles(testDirectoryPath).map { path =>
      val testName = {
        val p = FileUtil.toSystemIndependentName(testDirectoryPath.relativize(path).toString)
        val ext = path.getFileName.toString.split("\\.").lastOption.map(e => s".$e").getOrElse("")
        p.stripSuffix(ext)
      }
      Array(testName, path)
    }
  }

  protected def baseFileSetTest(testFile: Path): Unit = {
    val testName = testFile.getFileName.toString
    val fileText = StringUtil.convertLineSeparators(Files.readString(testFile, StandardCharsets.UTF_8))
    runTest(testName, fileText)
  }

  protected def runTest(testName: String, fileText: String): Unit = {
    val fileParts = parseTestFileText(fileText)

    assertTrue("Test file should have at least two sections separated with ----", fileParts.sizeIs > 1)

    val inputRaw = fileParts.head
    val expectedResultRaw = fileParts.last

    val testNameWithoutDot = testName.split("\\.").head

    val actualResult = transform(testNameWithoutDot, inputRaw).trim
    val expectedResult = transformExpectedResult(expectedResultRaw).trim

    assertEqualsFailable(expectedResult, actualResult)
  }

  protected def transform(testName: String, fileText: String): String

  protected def transformExpectedResult(text: String): String = text

  protected def createLightFile(@NonNls text: String): PsiFile =
    PsiFileFactory.getInstance(project).createFileFromText("dummy.scala", language, text)

  protected def parseTestFileText(text: String): List[String] =
    FileSetTestBase.FilePartsSeparatorPattern.split(text, -1).toList

  private def findTestFiles(path: Path): Array[Path] =
    if (path.isDirectory) path.children().toArray.flatMap(findTestFiles)
    else Array(path).filter(isTestFile)

  private def isTestFile(path: Path): Boolean = {
    val canonicalPath = path.toCanonicalPath.toString
    val name = path.getFileName.toString

    !canonicalPath.contains(".svn") &&
      !canonicalPath.contains(".cvs") &&
      fileExtensionFilter(name) &&
      !name.startsWith("_") &&
      name != "CVS"
  }

  private def fileExtensionFilter(fileName: String): Boolean =
    testFileExtensions.exists(fileName.endsWith)

  protected def scalaCodeStyleSettings: ScalaCodeStyleSettings =
    ScalaCodeStyleSettings.getInstance(project)

  protected def commonCodeStyleSettings: CommonCodeStyleSettings =
    CodeStyle.getSettings(project).getCommonSettings(ScalaLanguage.INSTANCE)
}

private object FileSetTestBase {
  private final val FilePartsSeparatorPattern: Pattern = Pattern.compile("\\n?(?m)^-{4,}\\n?")
}
