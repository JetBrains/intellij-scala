package org.jetbrains.plugins.scala
package testcases

import java.io.{File, FileNotFoundException}

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import junit.framework.TestSuite
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.util.TestUtils

import scala.collection.JavaConverters._

abstract class FileSetTestCase(val path: String) extends TestSuite {
  var myProject: Project = _
  val myFiles: Array[File] =
    try FileScanner.scan(path, "(.*)\\.test", false).asScala.toArray
    catch {
      case e: FileNotFoundException =>
        Array.empty
    }

  addAllTests()

  private def addAllTests(): Unit =
    for (f <- myFiles) {
      if (f.isFile) addFileTest(f)
    }


  protected def getProject: Project = myProject

  protected def setUp(project: Project): Unit = {
    myProject = project
  }

  protected def tearDown(project: Project): Unit = {
    myProject = null
  }
  override def getName: String = getClass.getName

  protected def addFileTest(file: File): Unit = if (!StringUtil.startsWithChar(file.getName, '_') && !("CVS" == file.getName)) {
    val t = new ActualTest(file)
    addTest(t)
  }

  @throws[Throwable]
  protected def runTest(file: File, project: Project): Unit

  private class ActualTest(testFile: File) extends ScalaLightPlatformCodeInsightTestCaseAdapter {

    override protected def getTestName(lowercaseFirstLetter: Boolean) = ""

    @throws[Exception]
    override protected def setUp(): Unit = try {
      super.setUp()
      FileSetTestCase.this.setUp(getProject)
      TestUtils.disableTimerThread()
    } catch {
      case x: Exception =>
        // The tearDown method is not final and may be overridden
        try tearDown()
        catch {
          case ignore: Exception =>
        }
        throw x
    }

    @throws[Exception]
    override protected def tearDown(): Unit = {
      FileSetTestCase.this.tearDown(getProject)
      try super.tearDown()
      catch {
        case ignore: IllegalArgumentException =>
      }
    }

    @throws[Throwable]
    override protected def runTest(): Unit =
      FileSetTestCase.this.runTest(testFile, getProject)

    override def countTestCases = 1

    override def toString: String = getName + " "

    override protected def resetAllFields(): Unit = {
      // Do nothing otherwise myTestFile will be nulled out before getName() is called.
    }

    override def getName: String = testFile.getAbsolutePath
  }
}