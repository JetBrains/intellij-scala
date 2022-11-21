package org.jetbrains.plugins.scala.injection

import com.intellij.codeInsight.intention.impl.QuickEditAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.injected.{InjectedLanguageUtil, InjectedLanguageUtilBase}
import com.intellij.testFramework.fixtures.EditorTestFixture
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestCase, SharedTestProjectToken}
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.injection.ScalaInjectedLanguageEditInjectedFragmentTest.createEditorForInjectedFragment
import org.junit.Assert.{assertEquals, assertTrue}

import scala.annotation.nowarn

/**
 * Tests for editing injected code fragment via:<br>
 * [[com.intellij.codeInsight.intention.impl.QuickEditAction]]<br>
 * (e.g. using "Edit RegExp fragment")
 */
class ScalaInjectedLanguageEditInjectedFragmentTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def sharedProjectToken: SharedTestProjectToken =
    SharedTestProjectToken.ByScalaSdkAndProjectLibraries(this)

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_2_13

  override protected def setUp(): Unit = {
    super.setUp()

    //If we don't disable "caresAboutInjection" flag the original scala file which contains string literal will be lost
    //CodeInsightTestFixtureImpl.setupEditorForInjectedLanguage will be called and override `myFile` with the injected file
    //As an alternative we manually setup editor for the injected fragment in `doTypingTest`
    this.myFixture.setCaresAboutInjection(false)
  }

  private def doTypingTest(
    textToType: String,
    fileTextBefore: String,
    fileTextAfter: String,
    expectedInjectedTextBefore: String
  ): Unit = {
    configureFromFileText(fileTextBefore)

    val quickEditHandler = new QuickEditAction().invokeImpl(getProject, getEditor, getFile)

    val injectedFile = quickEditHandler.getNewFile
    val injectedVirtualFile = injectedFile.getVirtualFile

    assertEquals(
      "Wrong text in injected fragment",
      expectedInjectedTextBefore.withNormalizedSeparator,
      injectedFile.getText
    )

    val injectedEditor = createEditorForInjectedFragment(getProject, getFile, getEditor, injectedVirtualFile)
    val injectedEditorFixture = new EditorTestFixture(getProject, injectedEditor, injectedVirtualFile)
    injectedEditorFixture.`type`(textToType)

    myFixture.checkResult(fileTextAfter.withNormalizedSeparator)
  }


  private val CommonExpectedInjectedText = """text \w\s"""
  private val CommonTextToType = raw""" text2 \d \s \w """
  private val qqq = "\"\"\""

  def testTypeSpaces_SimpleString(): Unit = doTypingTest(
    CommonTextToType,
    raw""""text \\w\\s$CARET".r""",
    raw""""text \\w\\s text2 \\d \\s \\w ".r""",
    CommonExpectedInjectedText
  )

  def testTypeSpaces_InterpolatedString(): Unit = doTypingTest(
    CommonTextToType,
    raw"""s"text \\w\\s$CARET".r""",
    raw"""s"text \\w\\s text2 \\d \\s \\w ".r""",
    CommonExpectedInjectedText
  )

  def testTypeSpaces_InterpolatedString_Raw(): Unit = doTypingTest(
    CommonTextToType,
    raw"""raw"text \w\s$CARET".r""",
    raw"""raw"text \w\s text2 \d \s \w ".r""",
    CommonExpectedInjectedText
  )

  def testTypeSpaces_MultilineString(): Unit = doTypingTest(
    CommonTextToType,
    raw"""${qqq}text \w\s$CARET$qqq.r""",
    raw"""${qqq}text \w\s text2 \d \s \w $qqq.r""",
    CommonExpectedInjectedText
  )

  def testTypeSpaces_MultilineInterpolatedString(): Unit = doTypingTest(
    CommonTextToType,
    raw"""s${qqq}text \\w\\s$CARET$qqq.r""",
    raw"""s${qqq}text \\w\\s text2 \\d \\s \\w $qqq.r""",
    CommonExpectedInjectedText
  )

  def testTypeSpaces_MultilineInterpolatedString_Raw(): Unit = doTypingTest(
    CommonTextToType,
    raw"""raw${qqq}text \w\s$CARET$qqq.r""",
    raw"""raw${qqq}text \w\s text2 \d \s \w $qqq.r""",
    CommonExpectedInjectedText
  )

  //SCL-15461
  def testTypeEnter_SimpleString(): Unit = doTypingTest(
    "\n",
    raw"""//language=JAVA
         |"class A {$CARET}"""".stripMargin,
    raw"""//language=JAVA
         |"class A {\n    \n}"""".stripMargin,
    "class A {}"
  )

  //SCL-15461
  def testTypeEnter_IntepolatedString(): Unit = doTypingTest(
    "\n",
    raw"""//language=JAVA
         |s"class A {$CARET}"""".stripMargin,
    raw"""//language=JAVA
         |s"class A {\n    \n}"""".stripMargin,
    "class A {}"
  )
}

object ScalaInjectedLanguageEditInjectedFragmentTest {

  //noinspection ScalaDeprecation,ApiStatus,UnstableApiUsage
  //NOTE: we need to use deprecated `InjectedLanguageUtil` because there is no alternative in [[InjectedLanguageManager]]
  //This method is used in `com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl.setupEditorForInjectedLanguage`
  //But in this text class it's not invoked because we invoke `setCaresAboutInjection(false)` (see `setUp` method)
  @nowarn("cat=deprecation")
  /*private*/ def createEditorForInjectedFragment(
    project: Project,
    hostFile: PsiFile,
    hostEditor: Editor,
    injectedVirtualFile: VirtualFile
  ): Editor = {
    val caretOffset = hostEditor.getCaretModel.getOffset

    //NOTE: for some reason this `injectedFile` is different from what is returned from `QuickEditHandler.getNewFile`
    //this one is needed to find `documentWindow`
    val injectedFile = InjectedLanguageUtilBase.findInjectedPsiNoCommit(hostFile, caretOffset)
    val documentWindow = InjectedLanguageUtil.getDocumentWindow(injectedFile)
    val injectedOffset = InjectedLanguageUtil.hostToInjectedUnescaped(documentWindow, caretOffset)
    val fileEditorManager = FileEditorManager.getInstance(project)

    val injectedFragmentEditor = fileEditorManager.openTextEditor(new OpenFileDescriptor(project, injectedVirtualFile, injectedOffset), true)
    assertTrue("Expected editor for injected fragment, not the host editor", injectedFragmentEditor != hostEditor)
    injectedFragmentEditor
  }
}