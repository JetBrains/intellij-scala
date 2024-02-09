package org.jetbrains.plugins.scala.refactoring.inline

import com.intellij.ide.DataManager
import com.intellij.lang.refactoring.InlineActionHandler
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem, VirtualFile}
import com.intellij.psi.PsiElement
import com.intellij.refactoring.actions.BaseRefactoringAction
import com.intellij.refactoring.util.CommonRefactoringUtil.RefactoringErrorHintException
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.refactoring.inline.InlineRefactoringTestBase.{ExpectedResult, SettingDescriptor, TestCaseDescriptor, TestCaseOptions, parseExpectedError}
import org.jetbrains.plugins.scala.refactoring.refactoringCommonTestDataRoot
import org.jetbrains.plugins.scala.util.TestUtils.ExpectedResultFromLastComment
import org.jetbrains.plugins.scala.util.{RevertableChange, TestUtils}
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaFileType}
import org.junit.Assert._

import java.io.File
import java.util.concurrent.TimeUnit

abstract class InlineRefactoringTestBase extends ScalaLightCodeInsightFixtureTestCase {
  protected val caretMarker = "/*caret*/"

  protected def folderPath = refactoringCommonTestDataRoot + "inline/"

  protected def doTest(): Unit = {
    val filePath = folderPath + getTestName(false) + ".scala"
    doTest(filePath)
  }

  protected def doTest(filePath: String): Unit = try {
    val testCaseFile = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assertNotNull(s"Can't find file $filePath", testCaseFile)

    val testCaseDescriptor = parseTestCase(testCaseFile)
    configureFromFileText(ScalaFileType.INSTANCE, testCaseDescriptor.fileText)

    assertCaretIsSet()

    val elementAtCaret = getElementAtCaret

    //in case some settings are modified we want to revert the change. It matters in tests where project is shared between test cases
    val revertibleChange = RevertableChange.combine(testCaseDescriptor.testOptions.settingDescriptors.map { case SettingDescriptor(getter, setter, newValue) =>
      RevertableChange.withModifiedSetting[Any](
        getter(),
        setter,
        newValue
      )
    })
    revertibleChange.run {
      doInlineTest(elementAtCaret, testCaseDescriptor)
    }
  } catch {
    case ex: Throwable =>
      System.err.println(s"### Test file location: $filePath")
      throw ex
  }

  protected def getElementAtCaret: PsiElement = {
    val elementAtCaretFromFocus = CommonDataKeys.PSI_ELEMENT.getData(DataManager.getInstance().getDataContextFromFocusAsync.blockingGet(5, TimeUnit.SECONDS))
    if (elementAtCaretFromFocus == null)
      BaseRefactoringAction.getElementAtCaret(getEditor, getFile)
    else
      elementAtCaretFromFocus
  }

  private def parseTestCase(testCaseFile: VirtualFile): TestCaseDescriptor = {
    val testFileTextOriginal = FileUtil.loadFile(new File(testCaseFile.getCanonicalPath), CharsetToolkit.UTF8).withNormalizedSeparator

    val (directiveLines, nonDirectiveLines) = testFileTextOriginal.linesIterator.toSeq.span(_.startsWith(ScalaDirectivePrefix))
    val testOptions = parseTestOptionDirectives(directiveLines)

    val withoutDirectives = nonDirectiveLines.mkString("\n")
    val withNormalizedCaretMarker = withoutDirectives.replace(caretMarker, CARET)

    val (fileText, expectedResult) = testOptions.expectedError match {
      case Some(value) =>
        (withNormalizedCaretMarker, ExpectedResult.Error(value))
      case _ =>
        // note: here we overuse configureFromFileText just because because
        // TestUtils.extractExpectedResultFromLastComment requires PsiFile instance
        configureFromFileText(ScalaFileType.INSTANCE, withNormalizedCaretMarker)

        val ExpectedResultFromLastComment(textBefore, textAfter) =
          TestUtils.extractExpectedResultFromLastComment(getFile)

        (textBefore, ExpectedResult.FileText(textAfter))
    }

    TestCaseDescriptor(
      fileText,
      expectedResult,
      testOptions
    )
  }

  private def doInlineTest(
    elementAtCaret: PsiElement,
    testCaseDescriptor: TestCaseDescriptor
  ): Unit = {
    try {
      invokeInlineHandler(elementAtCaret)
    } catch {
      case e: RefactoringErrorHintException =>
        val expectedError = testCaseDescriptor.testOptions.expectedError.orNull
        val actualError = e.getMessage
        assertEquals("Refactoring error message does not match", expectedError, actualError)
        return
    }

    val expectedFileText = testCaseDescriptor.expectedResult.asInstanceOf[ExpectedResult.FileText].text
    val actualFileText = getFile.getText.trim
    assertEquals(expectedFileText, actualFileText)
  }

  protected def invokeInlineHandler(elementAtCaret: PsiElement): Unit = {
    val inlineHandler = InlineActionHandler.EP_NAME.getExtensions.find(_.canInlineElement(elementAtCaret))
    inlineHandler match {
      case Some(handler) =>
        handler.inlineElement(getProject, getEditor, elementAtCaret)
      case None =>
        fail("No inline refactoring handler available")
    }

    //NOTE: we test PsiFile.getText not Document.getText
    //So we need to commit the latest changes in the document, in order psi file is up-to-date.
    //Without doing this we won't see any changes made in the document in
    //org.jetbrains.plugins.scala.lang.refactoring.inline.ScalaInlineProcessor.performPsiSpoilingRefactoring
    getEditor.getDocument.commit(getProject)
  }

  private val ScalaDirectivePrefix = "//>"

  // Examples:
  // //> set ScalaCodeStyleSettings.MULTILINE_STRING_INSERT_MARGIN_ON_ENTER=false
  // //> expected.error cannot.inline.value.functional.type
  // //> expected.error cannot.inline.not.simple.definition, value
  private def parseTestOptionDirectives(directiveLines: Seq[String]): TestCaseOptions = {
    val directiveContents: Seq[String] =
      directiveLines.map(_.stripPrefix(ScalaDirectivePrefix).trim)

    val pattern = """([\w.]+)\s+(.*?)""".r
    val keyValues: Seq[(String, String)] = directiveContents.map {
      case pattern(key, value) => key -> value
    }

    TestCaseOptions(
      expectedError = parseExpectedError(keyValues),
      settingDescriptors = parseSettingsDescriptors(keyValues)
    )
  }

  private def parseSettingsDescriptors(keyToValue: Seq[(String, String)]): Seq[SettingDescriptor] = {
    keyToValue.collect { case ("set", settingDescriptorString) =>
      parseSettingDescriptor(settingDescriptorString)
    }
  }

  // Examples:
  // ScalaCodeStyleSettings.MULTILINE_STRING_INSERT_MARGIN_ON_ENTER=false
  // ScalaCodeStyleSettings.MULTILINE_STRING_INSERT_MARGIN_ON_ENTER = false
  private val SettingRegex = """([a-zA-Z_$0-9]+)\.([a-zA-Z_$0-9]+)\s*=\s*(.*?)""".r

  private def parseSettingDescriptor(optionSetterString: String): SettingDescriptor =
    optionSetterString match {
      case SettingRegex(className, fieldName, value) =>
        className match {
          case "ScalaCodeStyleSettings" =>
            val settingsInstance = getScalaCodeStyleSettings
            createSettingDescriptor(settingsInstance, fieldName, value)
          case _ =>
            throw new AssertionError(s"Unexpected class name $className in option setter: $optionSetterString")
        }
    }

  private val StringClass = classOf[String]

  private def createSettingDescriptor(
    instance: AnyRef,
    fieldName: String,
    valueString: String,
  ): SettingDescriptor = {
    val field = instance.getClass.getField(fieldName)

    val valueDeserialised: Any = field.getType match {
      case java.lang.Boolean.TYPE => valueString.toBoolean
      case java.lang.Integer.TYPE => valueString.toInt
      case java.lang.Long.TYPE => valueString.toLong
      case java.lang.Short.TYPE => valueString.toShort
      case java.lang.Double.TYPE => valueString.toDouble
      case java.lang.Float.TYPE => valueString.toFloat
      case StringClass => valueString
    }

    SettingDescriptor(
      setter = v => {
        field.getType match {
          case java.lang.Boolean.TYPE => field.setBoolean(instance, v.asInstanceOf[java.lang.Boolean])
          case java.lang.Integer.TYPE => field.setInt(instance, v.asInstanceOf[java.lang.Integer])
          case java.lang.Long.TYPE => field.setLong(instance, v.asInstanceOf[java.lang.Long])
          case java.lang.Short.TYPE => field.setShort(instance, v.asInstanceOf[java.lang.Short])
          case java.lang.Double.TYPE => field.setDouble(instance, v.asInstanceOf[java.lang.Double])
          case java.lang.Float.TYPE => field.setFloat(instance, v.asInstanceOf[java.lang.Float])
          case _ => field.set(instance, v)
        }
      },
      getter = () => field.get(instance),
      value = valueDeserialised
    )
  }

  private def assertCaretIsSet(): Unit = {
    val caretModel = getEditor.getCaretModel
    assert(caretModel.getCaretCount == 1, "Expected exactly one caret.")
    assert(caretModel.getOffset > 0, s"Not specified caret marker in test case. Use $caretMarker in scala file for this.")
  }
}

object InlineRefactoringTestBase {

  private sealed trait ExpectedResult
  private object ExpectedResult {
    case class Error(message: String) extends ExpectedResult
    case class FileText(text: String) extends ExpectedResult
  }

  private case class TestCaseDescriptor(
    fileText: String,
    expectedResult: ExpectedResult,
    testOptions: TestCaseOptions
  )

  private case class SettingDescriptor(
    getter: () => Any,
    setter: Any => Unit,
    value: Any
  )

  private case class TestCaseOptions(
    expectedError: Option[String],
    settingDescriptors: Seq[SettingDescriptor]
  )

  private def parseExpectedError(keyToValue: Seq[(String, String)]) = {
    val expectedErrorBundleKey = keyToValue.find(_._1 == "expected.error").map(_._2)
    //noinspection DynamicPropertyKey
    val expectedError = expectedErrorBundleKey.map { expectedError =>
      expectedError.split(',').toList.map(_.trim) match {
        case bundleKey :: args =>
          ScalaBundle.message(bundleKey, args: _*)
        case _ =>
          fail(s"Unexpected error message format: $expectedError").asInstanceOf[Nothing]
      }
    }
    expectedError
  }
}
