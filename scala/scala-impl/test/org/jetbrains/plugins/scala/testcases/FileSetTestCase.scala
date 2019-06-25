/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *//*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.scala.testcases

import java.io.{File, FileNotFoundException}

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import junit.framework.TestSuite
import org.jetbrains.plugins.scala.FileScanner
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.util.TestUtils

import scala.collection.JavaConverters._

/**
 * Created by IntelliJ IDEA.
 *
 * @author oleg
 * @date Oct 11, 2006
 */
abstract class FileSetTestCase(val path: String) extends TestSuite {
  var myProject: Project = _
  val myFiles: Array[File] =
    try FileScanner.scan(path, getSearchPattern, false).asScala.toArray
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

  def getSearchPattern: String = FileSetTestCase.TEST_FILE_PATTERN

  protected def addFileTest(file: File): Unit = if (!StringUtil.startsWithChar(file.getName, '_') && !("CVS" == file.getName)) {
    val t = new ActualTest(file)
    addTest(t)
  }

  @throws[Throwable]
  protected def runTest(file: File): Unit


  private class ActualTest(var myTestFile: File) extends ScalaLightPlatformCodeInsightTestCaseAdapter {
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
    override protected def runTest(): Unit = FileSetTestCase.this.runTest(myTestFile)

    override def countTestCases = 1

    override def toString: String = myTestFile.getAbsolutePath + " "

    override protected def resetAllFields(): Unit = {
      // Do nothing otherwise myTestFile will be nulled out before getName() is called.
    }

    override def getName: String = myTestFile.getAbsolutePath
  }
}

object FileSetTestCase {
  protected val TEST_FILE_PATTERN = "(.*)\\.test"
}