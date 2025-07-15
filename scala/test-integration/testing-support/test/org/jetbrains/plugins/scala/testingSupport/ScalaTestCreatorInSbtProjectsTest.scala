package org.jetbrains.plugins.scala.testingSupport

import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.{VfsUtil, VirtualFile}
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiClass, PsiFile}
import com.intellij.testIntegration.TestFramework
import org.jetbrains.plugins.scala.actions.FileTemplateTestUtils
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestTestFramework
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals
import org.jetbrains.sbt.project.SbtExternalSystemImportingTestLike
import org.junit.Assert.assertNotNull

import scala.jdk.CollectionConverters.ListHasAsScala

class ScalaTestCreatorInSbtProjectsTest extends SbtExternalSystemImportingTestLike {

  override protected def getTestDataProjectPath: String =
    s"scala/test-integration/testing-support/testData/testCreationProjects/${getTestName(true)}"

  override protected def copyTestProjectToTemporaryDir: Boolean = true

  override protected def enableSeparateModulesForProdTest = true

  override def setUp(): Unit = {
    super.setUp()

    // the header is used under the hood in the file templates =/
    FileTemplateTestUtils.initFileHeaderTemplate(getProject, getTestRootDisposable)
  }

  // SCL-24058
  // NOTE: we have to jeep multiple tests inside the same test class to reuse the same sbt project and do not run project reimport every time.
  // Unfortunately current ExternalSystemTestCase doesn't provide another convenient way to reuse the project in multiple test classes
  def testCreateNewTestInMultiModuleProjectWithMultipleExistingTestDirectories(): Unit = {
    importProject(null)

    doTestCreateNewTest(
      "com.example.level1.Dummy11",
      "project1/src/test/scala/com/example/level1/MyDummy11Test.scala",
      "com.example.level1.MyDummy11Test"
    )
    doTestCreateNewTest(
      "com.example.level2.Dummy12",
      "project1/src/test/scala/com/example/level2/MyDummy12Test.scala",
      "com.example.level2.MyDummy12Test"
    )

    doTestCreateNewTest(
      "com.example.level1.Dummy21",
      "project2/src/test/scala/com/example/level1/MyDummy21Test.scala",
      "com.example.level1.MyDummy21Test"
    )
    doTestCreateNewTest(
      "com.example.level2.Dummy22",
      "project2/src/test/scala/com/example/level2/MyDummy22Test.scala",
      "com.example.level2.MyDummy22Test"
    )

    doTestCreateNewTest(
      "com.example.level1.Dummy31",
      "project3/src/test/scala/com/example/level1/MyDummy31Test.scala",
      "com.example.level1.MyDummy31Test"
    )
    doTestCreateNewTest(
      "com.example.level2.Dummy32",
      "project3/src/test/scala/com/example/level2/MyDummy32Test.scala",
      "com.example.level2.MyDummy32Test"
    )
  }

  //noinspection DfaNullableToUnannotatedParam,DfaNullableToNotNullParam
  private def doTestCreateNewTest(
    mainClassFqn: String,
    expectedTestFileRelativePath: String,
    expectedTestClassFqn: String,
    testFramework: TestFramework = new ScalaTestTestFramework
  ): Unit = {
    val (psiFile, editor) = findFileForClassAndOpenEditor(mainClassFqn)

    val projectRoot = TestUtils.guessProjectDir(getProject)

    val allSourceFilesBefore = getAllSourceFiles(projectRoot)

    createTest(editor, psiFile, testFramework, testClassName = expectedTestClassFqn.split('.').last)

    val allSourceFilesAfter = getAllSourceFiles(projectRoot)

    val createdSourceFiles = allSourceFilesAfter.diff(allSourceFilesBefore)
    val createdSourceFilesPaths = createdSourceFiles.map(TestUtils.getPathRelativeToProject(_, getProject))

    assertCollectionEquals(
      s"Expected single test file to be created at '$expectedTestFileRelativePath', but got these new source files",
      Seq(expectedTestFileRelativePath),
      createdSourceFilesPaths.toSeq
    )

    val testClass = findClass(getProject, expectedTestClassFqn)
    assertNotNull(s"Expected test class '$expectedTestClassFqn' to be created", testClass)

    // Cleanup just in case to avoid strange test exceptions in tearDown
    closeAllOpenEditors(getProject)
  }

  private def findFileForClassAndOpenEditor(mainClassFqn: String): (PsiFile, Editor) = {
    val project = getProject

    val psiClass = findClass(project, mainClassFqn)
    val psiFile = psiClass.getContainingFile
    openFileInEditor(project, psiFile)

    val editor = getSelectedEditor(project)

    // Set the caret at the class definition position
    editor.getCaretModel.moveToOffset(psiClass.getTextOffset)

    (psiFile, editor)
  }

  private def createTest(
    editor: Editor,
    psiFile: PsiFile,
    testFramework: TestFramework,
    testClassName: String
  ): Unit = {
    // Set up mock test dialog data
    val testDialogMockData = ScalaTestCreator.MockTestDialogData(
      selectedTestFramework = testFramework,
      testClassName = testClassName
    )
    getProject.putUserData(ScalaTestCreator.MockTestDialogDataKey, testDialogMockData)

    try {
      val testCreator = new ScalaTestCreator()
      testCreator.createTest(getProject, editor, psiFile)
    } finally {
      // Cleanup mock data
      getProject.putUserData(ScalaTestCreator.MockTestDialogDataKey, null)
    }
  }

  private def findClass(project: Project, classFqn: String): PsiClass = {
    val scope = GlobalSearchScope.projectScope(project)
    ScalaPsiManager.instance(project).getCachedClass(scope, classFqn).getOrElse {
      throw new RuntimeException(s"Can't find class '$classFqn")
    }
  }

  private def openFileInEditor(project: Project, psiFile: PsiFile): Unit = {
    invokeAndWait {
      PsiNavigationSupport.getInstance
        .createNavigatable(project, psiFile.getVirtualFile, psiFile.getTextOffset)
        .navigate(true)
    }
  }

  private def getSelectedEditor(project: Project): Editor = {
    val editor = FileEditorManager.getInstance(project).getSelectedTextEditor
    assertNotNull("There should be an open editor", editor)
    editor
  }

  private def closeAllOpenEditors(project: Project): Unit = {
    FileEditorManager.getInstance(project).getSelectedEditors.foreach { fileEditor =>
      FileEditorManager.getInstance(project).closeFile(fileEditor.getFile)
    }
  }

  private def getAllSourceFiles(projectRoot: VirtualFile): Set[VirtualFile] = {
    val projectFileIndex = ProjectFileIndex.getInstance(getProject)
    VfsUtil.collectChildrenRecursively(projectRoot)
      .asScala
      .filter(file => !file.isDirectory && projectFileIndex.isInSourceContent(file))
      .toSet
  }
}