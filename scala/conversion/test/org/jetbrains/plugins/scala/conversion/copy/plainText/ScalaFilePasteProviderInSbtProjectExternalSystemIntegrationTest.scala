package org.jetbrains.plugins.scala.conversion.copy.plainText

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.sbt.project.ScalaExternalSystemImportingTestBase.{ExternalSystemImportRootOptions, TestProjectCopyOptions}
import org.jetbrains.sbt.project.SbtExternalSystemImportingTestLike
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * For lightweight unit-like tests for the same functionality see [[ScalaFilePasteProviderInSbtProjectTest]]
 */
@Category(Array(classOf[SlowTests2]))
@RunWith(classOf[JUnit4])
class ScalaFilePasteProviderInSbtProjectExternalSystemIntegrationTest
  extends SbtExternalSystemImportingTestLike
  with ScalaFilePasteProviderInSbtProjectTestLike {

  private var TestProjectName: String = _

  // To avoid java.lang.IllegalAccessError
  override def getProject: Project = super.getMyProject

  override protected def getTestDataProjectPath: String = {
    assertNotNull("Test project name is not set in `TestProjectName`", TestProjectName)
    val basePath = "scala/conversion/testdata/sbt_projects_for_paste/"
    basePath + TestProjectName
  }

  override protected def getTestProjectCopyOptions: TestProjectCopyOptions =
    super.getTestProjectCopyOptions.copy(copyToTemporaryDir = true)

  override protected def getExternalSystemImportRootOptions: ExternalSystemImportRootOptions =
    super.getExternalSystemImportRootOptions.copy(setUpDuringSetUp = false)

  //language=Scala
  private val PastedSimpleCodeWithAddSbtPlugin: String =
    """addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.12.0")
      |addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.13.0")""".stripMargin

  //language=Scala
  private val PastedComplexCodeWithAddSbtPlugin =
    """//line comment
      |/*
      | block comment
      | */
      |/**
      | * Doc Comment
      | */
      |addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.12.0")
      |
      |resolvers ++= Seq(
      |  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
      |)
      |
      |libraryDependencies ++= Seq(
      |  "org.scalatest" %% "scalatest" % "3.2.16" % Test
      |)""".stripMargin

  //language=Scala
  private val PastedCodeWithoutAddSbtPlugin =
    """//line comment
      |/*
      | block comment
      | */
      |/**
      | * Doc Comment
      | */
      |
      |resolvers ++= Seq(
      |  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
      |)
      |
      |libraryDependencies ++= Seq(
      |  "org.scalatest" %% "scalatest" % "3.2.16" % Test
      |)""".stripMargin

  private def doPasteToDirectoryTest(
    relativeDirPath: String,
    pastedCode: String,
    expectedNewFileName: String
  ): Unit = {
    val directory = getMyProjectRoot.findFileByRelativePath(relativeDirPath)
    assertNotNull(s"Can't find directory $relativeDirPath in $getMyProjectRoot", directory)

    doPasteToDirectoryAndCreateNewFileTest(
      directory = directory,
      pastedCode = pastedCode,
      expectedNewFileName = expectedNewFileName
    )
  }

  @Test
  def testAutoCreatePluginSbtFile_MainTestEnabled(): Unit =
    autoCreatePluginSbtFile(isMainTestEnabled = true)

  @Test
  def testAutoCreatePluginSbtFile_MainTestDisabled(): Unit = {
    autoCreatePluginSbtFile(isMainTestEnabled = false)
    // This assertion cannot be run when separateProdAndTestSources are enabled due to https://youtrack.jetbrains.com/issue/SCL-24317
    doPasteToDirectoryTest("", PastedComplexCodeWithAddSbtPlugin, expectedNewFileName = "worksheet.sc")
  }

  private def autoCreatePluginSbtFile(isMainTestEnabled: Boolean): Unit = {
    TestProjectName = "autoCreatePluginSbtFile"
    getCurrentExternalProjectSettings.separateProdAndTestSources = isMainTestEnabled
    importProject(false)

    doPasteToDirectoryTest("project", PastedComplexCodeWithAddSbtPlugin, "plugins.sbt")

    val SomeOtherName = "worksheet.sc"
    doPasteToDirectoryTest("project", PastedCodeWithoutAddSbtPlugin, SomeOtherName)
    doPasteToDirectoryTest("project/inner", PastedComplexCodeWithAddSbtPlugin, SomeOtherName)
    doPasteToDirectoryTest("src/main/scala", PastedComplexCodeWithAddSbtPlugin, SomeOtherName)
  }

  @Test
  def testUpdateExistingPluginsSbtFile_SimpleCode_To_EmptyFile(): Unit = {
    doPasteToDirectoryAndUpdateExistingFileTest(
      testProjectName = "autoCreatePluginSbtFileWithAlreadyExistingPluginsSbt",
      relativeDirectoryPath = "project",
      fileName = "plugins.sbt",
      fileTextBefore =
        """""",
      pastedText = PastedSimpleCodeWithAddSbtPlugin,
      expectedFileTextAfter =
        s"""$Caret$PastedSimpleCodeWithAddSbtPlugin"""
    )
  }

  @Test
  def testUpdateExistingPluginsSbtFile_SimpleCode_To_FileWithAddSbtPluginsStatements(): Unit =
    doPasteToDirectoryAndUpdateExistingFileTest(
      testProjectName = "autoCreatePluginSbtFileWithAlreadyExistingPluginsSbt",
      relativeDirectoryPath = "project",
      fileName = "plugins.sbt",
      fileTextBefore =
        """addSbtPlugin("com.github.sbt" % "sbt-release" % "1.1.0")
          |addSbtPlugin("org.scala-js"   % "sbt-scalajs" % "1.12.0")
          |""".stripMargin,
      pastedText = PastedSimpleCodeWithAddSbtPlugin,
      expectedFileTextAfter =
        s"""addSbtPlugin("com.github.sbt" % "sbt-release" % "1.1.0")
           |addSbtPlugin("org.scala-js"   % "sbt-scalajs" % "1.12.0")
           |$Caret$PastedSimpleCodeWithAddSbtPlugin
           |""".stripMargin
    )

  private def doPasteToDirectoryAndUpdateExistingFileTest(
    testProjectName: String,
    relativeDirectoryPath: String,
    fileName: String,
    fileTextBefore: String,
    pastedText: String,
    expectedFileTextAfter: String,
  ): Unit = {
    TestProjectName = testProjectName
    importProject(false)

    val directory = getMyProjectRoot.findFileByRelativePath(relativeDirectoryPath)
    assertNotNull(s"Can't find directory $relativeDirectoryPath in $getMyProjectRoot", directory)

    doPasteToDirectoryAndUpdateExistingFileTest(
      directory = directory,
      existingFileName = fileName,
      fileTextBefore = fileTextBefore,
      pastedText = pastedText,
      expectedFileTextAfter = expectedFileTextAfter
    )
  }
}
