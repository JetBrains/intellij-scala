package org.jetbrains.plugins.scala.lang.actions.editor.enter_long_tests.scala3

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import junitparams.naming.TestCaseName
import junitparams.{JUnitParamsRunner, Parameters}
import org.jetbrains.plugins.scala.FileSetTests
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.lang.actions.editor.enter.scala3.DoEditorStateTestOps
import org.jetbrains.plugins.scala.lang.actions.editor.enter_long_tests.scala3.Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveGenerator.TestData
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith

import scala.annotation.unused

// TODO: add tests for parameter default value after it's fixed in parser:
//  https://youtrack.jetbrains.com/issue/SCL-16603#focus=Comments-27-4772356.0-0

// test Enter press in different contexts
//  + different nesting level
//  + different scopes inside template definition/function body/etc...
//    + in the beginning/middle of scope
//    + in the last position inside scope
//    + in the end of the file, with & without trailing white space
//  + with & without end markers
//  + with & without space before CARET in the starting position
//  + with & without space after CARET in the starting position
//  + with some content after caret (on each step)
//  + with trimmed data & with extra spaces after it
@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
@RunWith(classOf[JUnitParamsRunner])
@Category(Array(classOf[FileSetTests]))
abstract class Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveBase extends DoEditorStateTestOps {

  override protected def setUp(): Unit = {
    super.setUp()
    ScalaCompileServerSettings.getInstance().COMPILE_SERVER_ENABLED = false
    getScalaCodeStyleSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX = true
  }

  override protected def tearDown(): Unit = {
    super.tearDown()
  }

  protected final def runEnterHandler(testData: TestData): Unit = {
    testData match {
      case TestData.ExplicitEditorStates(editorStates) =>
        doEditorStateTest(myFixture, editorStates)

      case TestData.Generated(contextCode, codeToType) =>
        checkIndentAfterTypingCode(contextCode, codeToType, myFixture)
    }
  }
}

abstract class Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveSingleGroupBase
  extends Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveBase {

  protected def createTestParameters(): Array[AnyRef]

  @unused("used reflectively by the @Parameters annotation")
  final def testParameters: Array[AnyRef] = createTestParameters()

  @Test
  @Parameters(method = "testParameters")
  @TestCaseName(value = "{method}[{0}]")
  final def enterHandler(
    @unused("used reflectively by the @TestCaseName annotation") testName: String,
    testData: TestData
  ): Unit = {
    runEnterHandler(testData)
  }
}
