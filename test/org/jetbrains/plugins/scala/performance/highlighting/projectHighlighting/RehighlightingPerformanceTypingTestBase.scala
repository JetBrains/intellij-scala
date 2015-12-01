package org.jetbrains.plugins.scala.performance.highlighting.projectHighlighting

import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.file.impl.FileManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures._
import com.intellij.util.ThrowableRunnable
import org.jetbrains.plugins.scala.base.ScalaLibraryLoader
import org.jetbrains.plugins.scala.extensions.inWriteCommandAction
import org.jetbrains.plugins.scala.performance.DownloadingAndImportingTestCase
import org.jetbrains.plugins.scala.util.TestUtils

/**
  * Author: Svyatoslav Ilinskiy
  * Date: 11/17/2015
  */
abstract class RehighlightingPerformanceTypingTestBase extends DownloadingAndImportingTestCase {

  var myCodeInsightTestFixture: CodeInsightTestFixture = null

  var libLoader: ScalaLibraryLoader = null

  override def setUp(): Unit = {
    super.setUp()

    //have to create a fake fixture instead of using myTestFixture because when I call setUp() on myCodeInsightTestFixture
    //it calls setUp on myTestFixture, which throws an error because it is already initialized
    //I'm pretty sure I'm not supposed to create fixtures that way but it works (at least for this case)
    val fakeFixture = new IdeaProjectTestFixture {
      override def getModule: Module = myTestFixture.getModule

      override def getProject: Project = myTestFixture.getProject

      override def setUp(): Unit = ()

      override def tearDown(): Unit = ()
    }
    myCodeInsightTestFixture = IdeaTestFixtureFactory.getFixtureFactory.createCodeInsightFixture(fakeFixture)
    myCodeInsightTestFixture.setUp()

    libLoader = ScalaLibraryLoader.withMockJdk(myCodeInsightTestFixture.getProject, myCodeInsightTestFixture.getModule,
      TestUtils.getTestDataPath + "/")
    libLoader.loadScala(TestUtils.DEFAULT_SCALA_SDK_VERSION)
  }


  override def tearDown(): Unit = {
    myCodeInsightTestFixture.tearDown()
    myCodeInsightTestFixture = null
    libLoader.clean()
    libLoader = null
    super.tearDown()
  }

  def doTest(filename: String,
             timeoutInMillis: Int,
             stringsToType: Seq[String],
             pos: LogicalPosition,
             typeInSetup: Option[String]): Unit = {
    val file = findFile(filename)
    val fileManager: FileManager = PsiManager.getInstance(myProject).asInstanceOf[PsiManagerEx].getFileManager

    myCodeInsightTestFixture.openFileInEditor(file)
    val editor = myCodeInsightTestFixture.getEditor
    val initialText = editor.getDocument.getText
    PlatformTestUtil.startPerformanceTest(s"Performance test $filename", timeoutInMillis, new ThrowableRunnable[Nothing] {
      override def run(): Unit = {
        stringsToType.foreach { s =>
          myCodeInsightTestFixture.`type`(s)
          myCodeInsightTestFixture.doHighlighting()
        }
        fileManager.cleanupForNextTest()
      }
    }).setup(new ThrowableRunnable[Nothing] {
      override def run(): Unit = {
        //file.refresh(false, false)
        inWriteCommandAction(myProject) {
          editor.getDocument.setText(initialText)
        }
        editor.getCaretModel.moveToLogicalPosition(pos)
        typeInSetup.foreach(myCodeInsightTestFixture.`type`)
        myCodeInsightTestFixture.doHighlighting()
      }
    }).assertTiming()
    inWriteCommandAction(myProject) {
      editor.getDocument.setText(initialText)
    }
  }

  override def githubUsername: String

  override def githubRepoName: String

  override def revision: String

  override protected def getExternalSystemConfigFileName: String
}
