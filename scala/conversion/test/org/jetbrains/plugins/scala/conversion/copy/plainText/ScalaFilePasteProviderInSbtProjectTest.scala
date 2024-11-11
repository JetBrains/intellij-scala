package org.jetbrains.plugins.scala.conversion.copy.plainText

import com.intellij.ide.IdeView
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.actionSystem.{LangDataKeys, PlatformCoreDataKeys}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.vfs.{VfsUtil, VirtualFile}
import com.intellij.psi.{PsiDirectory, PsiManager}
import com.intellij.util.ui.TextTransferable
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals
import org.jetbrains.sbt.project.SbtExternalSystemImportingTestLike
import org.junit.Assert.{assertEquals, assertNotNull, assertTrue}
import org.junit.experimental.categories.Category

@Category(Array(classOf[SlowTests]))
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
    doPasteToDirectoryTest(PastedCodeWithAddSbtPlugin, "project", "plugins.sbt")

    val SomeOtherName = "worksheet.sc"
    doPasteToDirectoryTest(PastedCodeWithoutAddSbtPlugin, "project", SomeOtherName)
    doPasteToDirectoryTest(PastedCodeWithAddSbtPlugin, "project/inner", SomeOtherName)
    doPasteToDirectoryTest(PastedCodeWithAddSbtPlugin, "src/main/scala", SomeOtherName)
    doPasteToDirectoryTest(PastedCodeWithAddSbtPlugin, "", SomeOtherName)
  }

  def testAutoCreatePluginSbtFileWithAlreadyExistingPluginsSbt(): Unit = {
    doPasteToDirectoryTest(PastedCodeWithAddSbtPlugin, "project", "plugins_1.sbt")
  }

  private def doPasteToDirectoryTest(
    pastedCode: String,
    relativeDirPath: String,
    expectedFileName: String
  ): Unit = {
    val psiDirectory = findPsiDirectory(relativeDirPath)
    val module = ModuleUtilCore.findModuleForPsiElement(psiDirectory)

    // Prepare context before invoking paste action
    val dataContext = SimpleDataContext.builder()
      .add(PlatformCoreDataKeys.MODULE, module)
      .add(LangDataKeys.IDE_VIEW, new IdeView {
        override def getDirectories: Array[PsiDirectory] = Array(psiDirectory)

        override def getOrChooseDirectory(): PsiDirectory = psiDirectory
      })
      .build()
    CopyPasteManager.getInstance.setContents(new TextTransferable(pastedCode))

    // Invoke paste action
    val pasteProvider = new ScalaFilePasteProvider()
    assertTrue(
      "Paste action is not enabled in the context",
      pasteProvider.isPasteEnabled(dataContext)
    )

    val filesBeforePaste = psiDirectory.getVirtualFile.getChildren
    inWriteAction {
      pasteProvider.performPaste(dataContext)
    }
    val filesAfterPaste = psiDirectory.getVirtualFile.getChildren
    val newFiles = (filesAfterPaste.toSet -- filesBeforePaste.toSet).toSeq

    //We need to save documents to files to test their contents
    inWriteAction {
      saveDocumentContentsToDisk(newFiles)
    }

    val newFileNames = newFiles.map(_.getName)
    assertCollectionEquals(
      "Wrong file names are created after pasting to directory",
      Seq(expectedFileName),
      newFileNames
    )

    val fileContent = new String(newFiles.head.contentsToByteArray()).trim
    assertEquals(
      "Newly created file content should equal to the pasted content",
      pastedCode.trim,
      fileContent
    )
  }

  private def saveDocumentContentsToDisk(newFiles: Seq[VirtualFile]): Unit = {
    val fileDocumentManager = FileDocumentManager.getInstance
    val newDocuments = newFiles.map(FileDocumentManager.getInstance().getDocument)
    newDocuments.foreach(fileDocumentManager.saveDocument)
  }

  private def findPsiDirectory(relativeDirPath: String): PsiDirectory = {
    val pathParts = relativeDirPath.split('/').filter(_.nonEmpty) // findRelativeFile accepts varargs
    val directory: VirtualFile = VfsUtil.findRelativeFile(myProjectRoot, pathParts: _*)
    assertNotNull(s"Can't find directory `$relativeDirPath` in `$myProjectRoot`", directory)

    val psiDirectory = PsiManager.getInstance(getProject).findDirectory(directory)
    assertNotNull(s"Can't find psi directory for directory ${directory.getPath}", psiDirectory)
    psiDirectory
  }
}