package org.jetbrains.plugins.scala.actions

import com.intellij.ide.IdeView
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.util.EditorHelper
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.actionSystem.{ActionManager, ActionUiKind, AnActionEvent, LangDataKeys}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDirectory, PsiElement, PsiManager}
import com.intellij.testFramework.UsefulTestCase.assertInstanceOf
import org.jetbrains.annotations.{NotNull, Nullable}
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.extensions.{StringExt, inWriteCommandAction}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

abstract class NewPackageObjectActionTestBase extends ScalaFileTemplateTestBase {

  protected def doTest(directory: String, expectedText: String, withFileHeaderTemplate: Boolean = false): Unit = {
    val view = runAction(directory, withFileHeaderTemplate)
    val selectedElement = view.getSelectedElement

    assertInstanceOf(selectedElement, classOf[ScalaFile])
    val file = selectedElement.asInstanceOf[ScalaFile]

    myFixture.openFileInEditor(file.getVirtualFile)
    myFixture.checkResult(expectedText.withNormalizedSeparator)
  }

  private def runAction(directory: String, withFileHeaderTemplate: Boolean): TestIdeView = {
    implicit val project: Project = getProject
    val dir = myFixture.getTempDirFixture.findOrCreateDir(directory)
    val psiDirectory = PsiManager.getInstance(project).findDirectory(dir)
    val view = new TestIdeView(psiDirectory)
    val dataContext = SimpleDataContext.getSimpleContext(LangDataKeys.IDE_VIEW, view, SimpleDataContext.getProjectContext(project))
    val action = ActionManager.getInstance().getAction(NewPackageObjectAction.ID)
    assertInstanceOf(action, classOf[NewPackageObjectAction])
    val event = AnActionEvent.createEvent(dataContext, null, "", ActionUiKind.NONE, null)
    initFileHeaderTemplate(isEmpty = !withFileHeaderTemplate)
    inWriteCommandAction(action.actionPerformed(event))
    view
  }

  protected def fileHeader: String = {
    val props = FileTemplateManager.getInstance(getProject).getDefaultProperties
    s"""/**
       | * Created by ${props.get("USER")} on ${props.get("DATE")}.
       | */""".stripMargin
  }
}

final class NewPackageObjectActionTest_Scala2 extends NewPackageObjectActionTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

  def testCreatePackageObject(): Unit = doTest(
    directory = "example",
    expectedText =
      s"""package object example$CARET {
         |
         |}
         |""".stripMargin
  )

  def testCreatePackageObjectWithFileHeader(): Unit = doTest(
    directory = "example",
    expectedText =
      s"""
         |$fileHeader
         |package object example$CARET {
         |
         |}
         |""".stripMargin,
    withFileHeaderTemplate = true
  )

  def testCreatePackageObjectInNestedPackage(): Unit = doTest(
    directory = "com/example",
    expectedText =
      s"""package com
         |
         |package object example$CARET {
         |
         |}
         |""".stripMargin
  )

  def testCreatePackageObjectWithFileHeaderInNestedPackage(): Unit = doTest(
    directory = "com/example",
    expectedText =
      s"""package com
         |
         |$fileHeader
         |package object example$CARET {
         |
         |}
         |""".stripMargin,
    withFileHeaderTemplate = true
  )
}

final class NewPackageObjectActionTest_Scala3 extends NewPackageObjectActionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  override protected def setUp(): Unit = {
    super.setUp()

    getScalaCodeStyleSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX = true
  }

  def testCreatePackageObject(): Unit = doTest(
    directory = "example",
    expectedText =
      s"""package object example$CARET
         |""".stripMargin
  )

  def testCreatePackageObject_WithoutUsingIndentationBasedSyntax(): Unit = {
    getScalaCodeStyleSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX = false

    doTest(
      directory = "example",
      expectedText =
        s"""package object example$CARET {
           |
           |}
           |""".stripMargin
    )
  }

  def testCreatePackageObjectWithFileHeader(): Unit = doTest(
    directory = "example",
    expectedText =
      s"""
         |$fileHeader
         |package object example$CARET
         |""".stripMargin,
    withFileHeaderTemplate = true
  )

  def testCreatePackageObjectInNestedPackage(): Unit = doTest(
    directory = "com/example",
    expectedText =
      s"""package com
         |
         |package object example$CARET
         |""".stripMargin
  )

  def testCreatePackageObjectWithFileHeaderInNestedPackage(): Unit = doTest(
    directory = "com/example",
    expectedText =
      s"""package com
         |
         |$fileHeader
         |package object example$CARET
         |""".stripMargin,
    withFileHeaderTemplate = true
  )
}

private final class TestIdeView(@Nullable private val dir: PsiDirectory) extends IdeView {
  @Nullable
  private var selectedElement: PsiElement = _

  override def getDirectories: Array[PsiDirectory] = Array(dir)

  @Nullable
  override def getOrChooseDirectory(): PsiDirectory = dir

  override def selectElement(@NotNull element: PsiElement): Unit = {
    selectedElement = element
    EditorHelper.openInEditor(element, false, true)
  }

  @Nullable
  def getSelectedElement: PsiElement = selectedElement
}
