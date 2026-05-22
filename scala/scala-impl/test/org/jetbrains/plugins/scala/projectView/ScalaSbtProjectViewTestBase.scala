package org.jetbrains.plugins.scala.projectView

import com.intellij.ide.projectView.impl.nodes.{ProjectViewModuleGroupNode, ProjectViewProjectNode}
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.PresentableNodeDescriptor.ColoredFragment
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.projectView.TestProjectTreeStructure
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.SimpleTextAttributes
import org.jetbrains.plugins.scala.projectView.ScalaSbtProjectViewTestBase.ProjectViewUtils
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.project.ScalaExternalSystemImportingTestBase.TestProjectCopyOptions
import org.jetbrains.sbt.project.{SbtExternalSystemImportingTestLike, SbtProjectImportTestUtils}
import org.junit.Assert.{assertEquals, assertTrue}

import java.nio.file.{Files, Path}
import java.util.function
import scala.jdk.CollectionConverters.ListHasAsScala

abstract class ScalaSbtProjectViewTestBase extends SbtExternalSystemImportingTestLike {

  override protected def getTestProjectCopyOptions: TestProjectCopyOptions =
    super.getTestProjectCopyOptions.copy(copyToTemporaryDir = true)

  override protected def getTestDataProjectPath: String =
    s"${TestUtils.getTestDataPath}/projectView/${getTestName(true)}"

  /**
   * Expects that inside the copied test directory, there will be two locations:
   * one for the project specified by the `projectDirectory` parameter and another for external sources.
   *
   * @param projectDirectory the name of the directory with the project
   */
  protected def setProjectRootToTestProjectDirectory(projectDirectory: String): Unit = {
    val projectPath = findTestProjectDirectory(projectDirectory)
    setProjectRoot(projectPath)
  }

  protected def prepareTwoLinkedProjects(rootProjectDirectory: String, linkedProjectDirectory: String): Unit = {
    setProjectRootToTestProjectDirectory(rootProjectDirectory)
    importProject(false)
    linkSecondExternalProject(linkedProjectDirectory)
  }

  private def linkSecondExternalProject(linkedProjectDirectory: String): Unit = {
    val linkedProjectPath = findTestProjectDirectory(linkedProjectDirectory)
    SbtProjectImportTestUtils.linkSbtProjectWithNewSettingsToProject(
      getMyProject,
      externalProjectPath = linkedProjectPath.toString,
      prodTestSourcesSeparated = true,
      jdkName = getJdkConfiguredForTestCase.getName
    )

    // We need to import the project once again after linking settings for hte second project
    importProject(false)
  }

  protected def importProjectAndCheckStructure(expectedStructure: String): Unit = {
    importProject(false)
    assertStructureEqual(expectedStructure)
  }

  private def findTestProjectDirectory(directoryName: String): Path = {
    val path = getTestProjectPath.resolve(directoryName)
    assertTrue(s"The test project directory is not found: $path", Files.isDirectory(path))
    path
  }

  protected def assertStructureEqual(expectedStructure: String): Unit = {
    val treeAsText: String = ProjectViewUtils.getActualProjectViewStructure(getMyProject, getTestRootDisposable)
    assertEquals("The expected tree structure is not equal to the current tree structure", expectedStructure, treeAsText)
  }
}

object ScalaSbtProjectViewTestBase {

  // TODO: Project view structure assertions shouldn't be the concern of the current test.
  //  We should unify it with project view test primitives/utilities.
  private object ProjectViewUtils {

    def getActualProjectViewStructure(project: Project, disposable: Disposable): String = {
      val testProjectStructure = new TestProjectTreeStructure(project, disposable)
      testProjectStructure.setShowLibraryContents(false)
      testProjectStructure.hideExcludedFiles()

      PlatformTestUtil.expandAll(testProjectStructure.createPane.getTree)

      PlatformTestUtil.print(
        testProjectStructure,
        testProjectStructure.getRootElement,
        NodePresenter
      )
    }


    private val NodePresenter: function.Function[Object, String] = {
      case node: AbstractTreeNode[_] =>
        presentNodeWithNodeType(node)
    }

    private def presentNodeWithNodeType(node: AbstractTreeNode[_]): String = {
      val presentationText = presentNode(node)
      node match {
        case _: ProjectViewModuleGroupNode => s"GroupNode: $presentationText"
        case _: ProjectViewProjectNode => s"Project: $presentationText"
        case _ => presentationText
      }
    }

    private def presentNode(node: AbstractTreeNode[_]): String = {
      node.update()

      val presentation = node.getPresentation
      val fragments = presentation.getColoredText
      if (fragments.isEmpty)
        presentation.getPresentableText
      else
        mapBolded(fragments.asScala.toSeq)
    }

    private def mapBolded(fragments: Seq[ColoredFragment]): String =
      fragments.map(formatFragmentText).mkString("")

    private def formatFragmentText(fragment: ColoredFragment) =
      addBoldMarkerIfNeeded(fragment)

    private def addBoldMarkerIfNeeded(fragment: ColoredFragment): String = {
      val isBold = fragment.getAttributes.getStyle == SimpleTextAttributes.STYLE_BOLD
      if (isBold)
        s"*${fragment.getText}*"
      else
        fragment.getText
    }
  }
}
