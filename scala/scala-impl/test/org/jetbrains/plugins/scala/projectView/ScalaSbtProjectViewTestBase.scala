package org.jetbrains.plugins.scala.projectView

import com.intellij.ide.projectView.impl.nodes.{ProjectViewModuleGroupNode, ProjectViewProjectNode}
import com.intellij.ide.util.treeView.{AbstractTreeNode, PresentableNodeDescriptor}
import com.intellij.projectView.TestProjectTreeStructure
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.SimpleTextAttributes
import junit.framework.TestCase.assertEquals
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.project.SbtExternalSystemImportingTestLike

import scala.jdk.CollectionConverters.ListHasAsScala

abstract class ScalaSbtProjectViewTestBase extends SbtExternalSystemImportingTestLike {

  override protected def enableSeparateModulesForProdTest = true

  override protected def copyTestProjectToTemporaryDir = true

  override protected def getTestDataProjectPath: String =
    s"${TestUtils.getTestDataPath}/projectView/${getTestName(true)}"

  /**
   * This test method expects that inside the copied test directory, there will be two locations:
   * one for the project specified by the `projectDirectory` parameter and another for external sources.
   *
   * @param projectDirectory the name of the directory with the project
   */
  protected def runTestWithOutsideSources(projectDirectory: String, expectedStructure: String): Unit = {
    val projectDirectoryFile = myProjectRoot.findChild(projectDirectory)
    assert(projectDirectoryFile != null, "The project directory is not found")

    myProjectRoot = projectDirectoryFile
    runTest(expectedStructure)
  }

  protected def runtTestWithTwoLinkedProjects(rootProjectDirectory: String, linkedProjectDirectory: String, expectedStructure: String): Unit = {
    val rootProjectFile = myProjectRoot.findChild(rootProjectDirectory)
    assert(rootProjectFile != null, "The root project directory is not found")

    val linkedProjectFile = myProjectRoot.findChild(linkedProjectDirectory)
    assert(linkedProjectFile != null, "The linked project directory is not found")

    myProjectRoot = rootProjectFile
    importProject(false)

    linkSbtProject(linkedProjectFile.getPath, prodTestSourcesSeparated = true, myProject)
    importProject(false)

    assertStructureEqual(expectedStructure)
  }


  protected def runTest(expectedStructure: String): Unit = {
    importProject(false)
    assertStructureEqual(expectedStructure)
  }

  private def assertStructureEqual(expectedStructure: String): Unit = {
    val testProjectStructure = new TestProjectTreeStructure(myProject, getTestRootDisposable)
    testProjectStructure.setShowLibraryContents(false)
    testProjectStructure.hideExcludedFiles()

    PlatformTestUtil.expandAll(testProjectStructure.createPane.getTree)

    def mapBolded(fragments: Seq[PresentableNodeDescriptor.ColoredFragment]): String =
      fragments.map { fragment =>
        val isBold = fragment.getAttributes.getStyle == SimpleTextAttributes.STYLE_BOLD
        if (isBold) s"*${fragment.getText}*"
        else fragment.getText
      }.mkString("")

    val nodePresenter: java.util.function.Function[Object, String] = { o: Any =>
      val node = o.asInstanceOf[AbstractTreeNode[_]]
      node.update()
      val presentation = node.getPresentation
      val presentationText = {
        val fragments = presentation.getColoredText
        if (fragments.isEmpty) presentation.getPresentableText
        else mapBolded(fragments.asScala.toSeq)
      }
      node match {
        case _: ProjectViewModuleGroupNode => s"GroupNode: $presentationText"
        case _: ProjectViewProjectNode => s"Project: $presentationText"
        case _ => presentationText
      }
    }

    val treeAsText = PlatformTestUtil.print(testProjectStructure, testProjectStructure.getRootElement, nodePresenter)
    assertEquals("The expected tree structure is not equal to the current tree structure", expectedStructure, treeAsText)
  }
}
