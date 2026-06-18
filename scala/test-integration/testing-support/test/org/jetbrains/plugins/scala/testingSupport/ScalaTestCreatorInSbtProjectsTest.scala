package org.jetbrains.plugins.scala.testingSupport

import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.{FileDocumentManager, FileEditorManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.{VfsUtil, VirtualFile}
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiClass, PsiFile}
import com.intellij.testIntegration.TestFramework
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.plugins.scala.actions.FileTemplateTestUtils
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestTestFramework
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals
import org.jetbrains.sbt.project.ScalaExternalSystemImportingTestBase.TestProjectCopyOptions
import org.jetbrains.sbt.project.SbtExternalSystemImportingTestLike
import org.junit.Assert.{assertEquals, assertNotNull}
import org.junit.experimental.categories.Category

import scala.jdk.CollectionConverters.ListHasAsScala

//noinspection SameParameterValue,DfaNullableToUnannotatedParam
@Category(Array(classOf[SlowTests2]))
class ScalaTestCreatorInSbtProjectsTest extends SbtExternalSystemImportingTestLike {

  override protected def getTestDataProjectPath: String =
    s"scala/test-integration/testing-support/testData/testCreationProjects/${getTestName(true)}"

  override protected def getTestProjectCopyOptions: TestProjectCopyOptions =
    super.getTestProjectCopyOptions.copy(copyToTemporaryDir = true)

  override def setUp(): Unit = {
    super.setUp()

    // the header is used under the hood in the file templates =/
    FileTemplateTestUtils.initFileHeaderTemplate(getMyProject, getTestRootDisposable)
  }

  // SCL-24058
  // NOTE: we have to keep multiple tests inside the same test class to reuse the same sbt project and do not run project reimport every time.
  // Unfortunately current ExternalSystemTestCase doesn't provide another convenient way to reuse the project in multiple test classes
  def testCreateNewTestInMultiModuleProjectWithMultipleExistingTestDirectories(): Unit = {
    importProject(null)

    // This can be an arbitrary test framework as the primary purpose of thie test is not test-framework-specific
    val testFramework = ScalaTestTestFramework()

    doTestCreateNewTest(
      "com.example.level1.Dummy11",
      testFramework,
      "project1/src/test/scala/com/example/level1/Dummy11Test.scala",
      "com.example.level1.Dummy11Test"
    )
    doTestCreateNewTest(
      "com.example.level2.Dummy12",
      testFramework,
      "project1/src/test/scala/com/example/level2/Dummy12Test.scala",
      "com.example.level2.Dummy12Test"
    )

    doTestCreateNewTest(
      "com.example.level1.Dummy21",
      testFramework,
      "project2/src/test/scala/com/example/level1/Dummy21Test.scala",
      "com.example.level1.Dummy21Test"
    )
    doTestCreateNewTest(
      "com.example.level2.Dummy22",
      testFramework,
      "project2/src/test/scala/com/example/level2/Dummy22Test.scala",
      "com.example.level2.Dummy22Test"
    )

    doTestCreateNewTest(
      "com.example.level1.Dummy31",
      testFramework,
      "project3/src/test/scala/com/example/level1/Dummy31Test.scala",
      "com.example.level1.Dummy31Test"
    )
    doTestCreateNewTest(
      "com.example.level2.Dummy32",
      testFramework,
      "project3/src/test/scala/com/example/level2/Dummy32Test.scala",
      "com.example.level2.Dummy32Test"
    )
  }

  def testUTest08(): Unit = {
    importProject(null)

    doTestCreateNewTest(
      "org.example.MyClass",
      "src/test/scala/org/example/MyClassTest.scala",
      "org.example.MyClassTest",
      """package org.example
        |
        |import utest._
        |
        |object MyClassTest extends TestSuite {
        |  override val tests: Tests = Tests {
        |
        |  }
        |}""".stripMargin
    )
  }

  def testScalatestBeforeAfter(): Unit = {
    importProject()

    def doTest(
      testClassName: String,
      generateBefore: Boolean,
      generateAfter: Boolean,
      @Language("Scala") expectedTestFileText: String
    ): Unit =
      doTestCreateNewTest(
        mainClassFqn = "org.example.Foo",
        testDialogMockData = ScalaTestCreator.MockTestDialogData(
          selectedTestFramework = Some(ScalaTestTestFramework()),
          testClassName = Some(testClassName),
          selectedTestedMethodsNames = Some(Seq("bar")),
          superClassName = Some("org.scalatest.funsuite.AnyFunSuite"),
          generateBefore = generateBefore,
          generateAfter = generateAfter
        ),
        expectedTestResult = ExpectedTestResult(
          createdTestFileRelativePath = s"src/test/scala/org/example/$testClassName.scala",
          createdTestClassFqn = s"org.example.$testClassName",
          createdTestFileText = expectedTestFileText
        )
      )

    doTest(
      testClassName = "FooBeforeAndAfterTest",
      generateBefore = true,
      generateAfter = true,
      expectedTestFileText =
        """package org.example
          |
          |import org.scalatest.BeforeAndAfterEach
          |import org.scalatest.funsuite.AnyFunSuite
          |
          |class FooBeforeAndAfterTest extends AnyFunSuite with BeforeAndAfterEach {
          |
          |  override def beforeEach(): Unit = {
          |
          |  }
          |
          |  override def afterEach(): Unit = {
          |
          |  }
          |
          |  test("testBar") {
          |
          |  }
          |
          |}
          |""".stripMargin
    )

    doTest(
      testClassName = "FooBeforeOnlyTest",
      generateBefore = true,
      generateAfter = false,
      expectedTestFileText =
        """package org.example
          |
          |import org.scalatest.BeforeAndAfterEach
          |import org.scalatest.funsuite.AnyFunSuite
          |
          |class FooBeforeOnlyTest extends AnyFunSuite with BeforeAndAfterEach {
          |
          |  override def beforeEach(): Unit = {
          |
          |  }
          |
          |  test("testBar") {
          |
          |  }
          |
          |}
          |""".stripMargin
    )

    doTest(
      testClassName = "FooAfterOnlyTest",
      generateBefore = false,
      generateAfter = true,
      expectedTestFileText =
        """package org.example
          |
          |import org.scalatest.BeforeAndAfterEach
          |import org.scalatest.funsuite.AnyFunSuite
          |
          |class FooAfterOnlyTest extends AnyFunSuite with BeforeAndAfterEach {
          |
          |  override def afterEach(): Unit = {
          |
          |  }
          |
          |  test("testBar") {
          |
          |  }
          |
          |}
          |""".stripMargin
    )

    doTest(
      testClassName = "FooPlainTest",
      generateBefore = false,
      generateAfter = false,
      expectedTestFileText =
        """package org.example
          |
          |import org.scalatest.funsuite.AnyFunSuite
          |
          |class FooPlainTest extends AnyFunSuite {
          |
          |  test("testBar") {
          |
          |  }
          |
          |}
          |""".stripMargin
    )
  }

  def testUTest09(): Unit = {
    importProject(null)

    doTestCreateNewTest(
      "org.example.MyClass",
      "src/test/scala/org/example/MyClassTest.scala",
      "org.example.MyClassTest",
      """package org.example
        |
        |import utest._
        |
        |class MyClassTest extends TestSuite {
        |  override val tests: Tests = Tests {
        |
        |  }
        |}""".stripMargin
    )

    doTestCreateNewTest(
      "org.example.MyClass",
      ScalaTestCreator.MockTestDialogData(
        testClassName = Some("MyClassTest2"),
        selectedTestedMethodsNames = Some(Seq(
          "myMethod1",
          "myMethod2",
        ))
      ),
      ExpectedTestResult(
        "src/test/scala/org/example/MyClassTest2.scala",
        "org.example.MyClassTest2",
        """package org.example
          |
          |import utest._
          |
          |class MyClassTest2 extends TestSuite {
          |  override val tests: Tests = Tests {
          |    test("myMethod1") {}
          |
          |    test("myMethod2") {}
          |  }
          |}""".stripMargin
      )
    )
  }

  private def doTestCreateNewTest(
    mainClassFqn: String,
    testFramework: TestFramework,
    expectedTestFileRelativePath: String,
    expectedTestClassFqn: String,
  ): Unit = doTestCreateNewTest(
    mainClassFqn = mainClassFqn,
    testDialogMockData = ScalaTestCreator.MockTestDialogData(
      selectedTestFramework = Some(testFramework)
    ),
    expectedTestResult = ExpectedTestResult(
      createdTestFileRelativePath = expectedTestFileRelativePath,
      createdTestClassFqn = expectedTestClassFqn,
      createdTestFileText = null
    )
  )

  private def doTestCreateNewTest(
    mainClassFqn: String,
    expectedTestFileRelativePath: String,
    expectedTestClassFqn: String,
    @Language("Scala")
    expectedTestFileText: String
  ): Unit = doTestCreateNewTest(
    mainClassFqn = mainClassFqn,
    testDialogMockData = ScalaTestCreator.MockTestDialogData(),
    ExpectedTestResult(
      createdTestFileRelativePath = expectedTestFileRelativePath,
      createdTestClassFqn = expectedTestClassFqn,
      createdTestFileText = expectedTestFileText
    )
  )

  private case class ExpectedTestResult(
    createdTestFileRelativePath: String,
    createdTestClassFqn: String,
    @Language("Scala")
    createdTestFileText: String
  )

  //noinspection DfaNullableToUnannotatedParam,DfaNullableToNotNullParam
  private def doTestCreateNewTest(
    mainClassFqn: String,
    testDialogMockData: ScalaTestCreator.MockTestDialogData,
    expectedTestResult: ExpectedTestResult
  ): Unit = {
    val (psiFile, editor) = findFileForClassAndOpenEditor(mainClassFqn)

    val projectRoot = TestUtils.guessProjectDir(getMyProject)

    val allSourceFilesBefore = getAllSourceFiles(projectRoot)

    createTest(editor, psiFile, testDialogMockData)

    val allSourceFilesAfter = getAllSourceFiles(projectRoot)

    val createdSourceFiles = allSourceFilesAfter.diff(allSourceFilesBefore)
    val createdSourceFilesPaths = createdSourceFiles.map(TestUtils.getPathRelativeToProject(_, getMyProject))

    val ExpectedTestResult(
      expectedTestFileRelativePath,
      expectedTestClassFqn,
      expectedTestFileText,
    ) = expectedTestResult

    assertCollectionEquals(
      s"Expected single test file to be created at '$expectedTestFileRelativePath', but got these new source files",
      Seq(expectedTestFileRelativePath),
      createdSourceFilesPaths.toSeq
    )

    val testClass = findClass(getMyProject, expectedTestClassFqn)
    assertNotNull(s"Expected test class '$expectedTestClassFqn' to be created", testClass)

    if (expectedTestFileText != null) {
      val createdTestFile = createdSourceFiles.head
      val createdTestDocument = FileDocumentManager.getInstance().getDocument(createdTestFile)
      assertEquals(
        expectedTestFileText,
        createdTestDocument.getText()
      )
    }

    // Cleanup just in case to avoid strange test exceptions in tearDown
    closeAllOpenEditors(getMyProject)
  }

  private def findFileForClassAndOpenEditor(mainClassFqn: String): (PsiFile, Editor) = {
    val project = getMyProject

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
    testDialogMockData: ScalaTestCreator.MockTestDialogData
  ): Unit = {
    // Set up mock test dialog data
    getMyProject.putUserData(ScalaTestCreator.MockTestDialogDataKey, testDialogMockData)

    try {
      val testCreator = new ScalaTestCreator()
      testCreator.createTest(getMyProject, editor, psiFile)

      // Wait for any background tasks (like dialog disposal) to complete before proceeding with test validation
      // ATTENTION: this is needed to avoid flaky NPE exception (like WI-75722 and KTIJ-9718)
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    } finally {
      // Cleanup mock data
      getMyProject.putUserData(ScalaTestCreator.MockTestDialogDataKey, null)
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
    val projectFileIndex = ProjectFileIndex.getInstance(getMyProject)
    VfsUtil.collectChildrenRecursively(projectRoot)
      .asScala
      .filter(file => !file.isDirectory && projectFileIndex.isInSourceContent(file))
      .toSet
  }
}
