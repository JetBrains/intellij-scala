package org.jetbrains.plugins.scala.conversion.copy.plainText

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.vfs.{VfsUtil, VirtualFile}
import com.intellij.psi.PsiManager
import org.jetbrains.sbt.project.SbtExternalSystemImportingTestLike
import org.junit.Assert.{assertEquals, assertNotNull, fail}

class ScalaFilePasteProviderInSbtProjectTest extends SbtExternalSystemImportingTestLike {

  override protected def getTestProjectPath: String =
    s"scala/conversion/testdata/sbt_projects_for_paste/${getTestName(true)}"

  override def setUp(): Unit = {
    super.setUp()

    importProject(false)
  }

  private val PastedCodeWithAddSbtPlugin =
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
      |)
      |""".stripMargin

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
      |)
      |""".stripMargin

  def testAutoCreatePluginSbtFile(): Unit = {
    assertExpectedFileName(PastedCodeWithAddSbtPlugin, "project", "plugins.sbt")

    val SomeOtherName = "worksheet.sc"
    assertExpectedFileName(PastedCodeWithoutAddSbtPlugin, "project", SomeOtherName)
    assertExpectedFileName(PastedCodeWithAddSbtPlugin, "project/inner", SomeOtherName)
    assertExpectedFileName(PastedCodeWithAddSbtPlugin, "src/main/scala", SomeOtherName)
    assertExpectedFileName(PastedCodeWithAddSbtPlugin, "", SomeOtherName)
  }

  private def assertExpectedFileName(
    pastedCode: String,
    relativeDirPath: String,
    expectedFileName: String
  ): Unit = {
    val pathParts = relativeDirPath.split('/').filter(_.nonEmpty) // findRelativeFile accepts varargs
    val directory: VirtualFile = VfsUtil.findRelativeFile(myProjectRoot, pathParts: _*)
    assertNotNull(s"Can't find directory `$relativeDirPath` in `$myProjectRoot`", directory)

    val psiDirectory = PsiManager.getInstance(getProject).findDirectory(directory)
    assertNotNull(s"Can't find psi directory for directory ${directory.getPath}", psiDirectory)

    val module = ModuleUtilCore.findModuleForPsiElement(psiDirectory)

    val provider = new ScalaFilePasteProvider()
    val nameWithExtension = provider.suggestedScalaFileNameForText(pastedCode, module, Some(psiDirectory)).getOrElse {
      fail("Can't create scala file for pasted code").asInstanceOf[Nothing]
    }
    assertEquals("Suggested file name", expectedFileName, nameWithExtension.fullName)
  }
}