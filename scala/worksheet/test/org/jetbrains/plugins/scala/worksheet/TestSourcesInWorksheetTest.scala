package org.jetbrains.plugins.scala.worksheet

import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileUtil}
import com.intellij.testFramework.{CompilerTester, EdtTestUtil}
import junit.framework.TestCase.assertEquals
import org.jetbrains.plugins.scala.WorksheetEvaluationTests
import org.jetbrains.plugins.scala.compiler.{CompilerMessagesUtil, JdkVersionParameters, SbtProjectCompilationTestBase}
import org.jetbrains.plugins.scala.util.runners.TestJdkVersion
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction.RunWorksheetActionResult
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetCache
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetExternalRunType.ReplRunType
import org.jetbrains.plugins.scala.worksheet.settings.persistent.WorksheetFilePersistentSettings
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters._

@Category(Array(classOf[WorksheetEvaluationTests]))
@RunWith(classOf[Parameterized])
class TestSourcesInWorksheetTest(jdkVersion: TestJdkVersion) extends SbtProjectCompilationTestBase(separateProdAndTestSources = true) {

  override protected def jdkVersionForTest: TestJdkVersion = jdkVersion

  override def runInDispatchThread(): Boolean = false

  private var worksheetVirtualFile: VirtualFile = _

  override def setUp(): Unit = {
    EdtTestUtil.runInEdtAndWait { () =>
      super.setUp()
      createProjectSubDirs("project", "src/main/scala", "src/test/scala")
      createProjectSubFile("project/build.properties", "sbt.version=1.10.5")
      createProjectSubFile("src/main/scala/Person.scala",
        s"""case class Person(name: String, age: Int)""")
      createProjectSubFile("src/test/scala/PersonSuite.scala",
        s"""class PersonSuite extends munit.FunSuite:
           |  test("simple test for a person instance"):
           |    val person = Person("Alice", 27)
           |    assertEquals(person.name, "Alice")
           |    assertEquals(person.age, 27)
           |
           |  test("a test which uses Apache Commons Text"):
           |    val sentence = "this is a sentence!"
           |    import org.apache.commons.text.WordUtils
           |    val capitalized = WordUtils.capitalize(sentence)
           |    assertEquals("This Is A Sentence!", capitalized)
           |""".stripMargin)
      worksheetVirtualFile = createProjectSubFile("src/test/scala/worksheet.sc",
        s"""PersonSuite().munitTests().map(_.name)
           |val sentence = "this is a sentence!"
           |import org.apache.commons.text.WordUtils
           |val capitalized = WordUtils.capitalize(sentence)
           |""".stripMargin)
      createProjectConfig(
        s"""lazy val root = project.in(file("."))
           |  .settings(
           |    scalaVersion := "3.5.2",
           |    libraryDependencies ++= Seq(
           |      "org.scalameta" %% "munit" % "1.0.2" % Test,
           |      "org.apache.commons" % "commons-text" % "1.12.0" % Test
           |    )
           |  )
           |""".stripMargin)

      importProject(false)
      val modules = ModuleManager.getInstance(getMyProject).getModules
      compiler = new CompilerTester(getMyProject, java.util.Arrays.asList(modules: _*), null, false)
    }
  }

  override def tearDown(): Unit = {
    EdtTestUtil.runInEdtAndWait(() => super.tearDown())
  }

  @Test
  def testSourcesInWorksheet(): Unit = {
    val messages = compiler.rebuild().asScala.toSeq
    CompilerMessagesUtil.assertNoErrorsOrWarnings(messages)

    val descriptor = new OpenFileDescriptor(getMyProject, worksheetVirtualFile)
    val (editor, psiFile) = EdtTestUtil.runInEdtAndGet { () =>
      val e = FileEditorManager.getInstance(getMyProject).openTextEditor(descriptor, true)
      val p = VirtualFileUtil.findPsiFile(worksheetVirtualFile, getMyProject).asInstanceOf[WorksheetFile]
      e -> p
    }

    val settings = WorksheetFilePersistentSettings(psiFile.getVirtualFile)
    settings.setRunType(ReplRunType)
    settings.setInteractive(false) // TODO: test these values?
    settings.setMakeBeforeRun(false)

    val future = EdtTestUtil.runInEdtAndGet { () =>
      RunWorksheetAction.runCompiler(editor, psiFile, auto = false)
    }
    val result = Await.result(future, Duration.Inf)
    assertEquals(RunWorksheetActionResult.Done, result)

    val resultText = {
      val resultEditor = WorksheetCache.getInstance(getMyProject).getViewer(editor)
      resultEditor.getDocument.getText()
    }
    assertEquals(
      s"""val res0: Seq[String] = List(simple test for a person instance, a test which uses Apache Commons Text)
         |val sentence: String = this is a sentence!
         |
         |val capitalized: String = This Is A Sentence!""".stripMargin, resultText)
  }
}

private object TestSourcesInWorksheetTest extends JdkVersionParameters
