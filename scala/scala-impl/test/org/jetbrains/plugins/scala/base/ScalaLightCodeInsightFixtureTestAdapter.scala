package org.jetbrains.plugins.scala
package base

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil.saveText
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.{CodeStyleSettings, CommonCodeStyleSettings}
import com.intellij.testFramework.LightPlatformTestCase.getSourceRoot
import com.intellij.testFramework.fixtures.CodeInsightTestFixture.CARET_MARKER
import com.intellij.testFramework.fixtures.{CodeInsightTestFixture, LightCodeInsightFixtureTestCase}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter.normalize
import org.jetbrains.plugins.scala.base.libraryLoaders._
import org.jetbrains.plugins.scala.debugger.DefaultScalaSdkOwner
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.util.TestUtils

/**
  * User: Dmitry Naydanov
  * Date: 3/5/12
  */

abstract class ScalaLightCodeInsightFixtureTestAdapter
  extends LightCodeInsightFixtureTestCase with DefaultScalaSdkOwner with FailableTest {

  override def getFixture: CodeInsightTestFixture = myFixture

  override def getTestDataPath: String = TestUtils.getTestDataPath + "/"

  protected def loadScalaLibrary: Boolean = true

  override def librariesLoaders: Seq[LibraryLoader] = Seq(
    ScalaSDKLoader(),
    HeavyJDKLoader()
  )

  override protected def getProjectDescriptor =
    DelegatingProjectDescriptor.withAfterSetupProject(super.getProjectDescriptor) { () =>
      afterSetUpProject()
    }

  protected def afterSetUpProject(): Unit = {
    if (loadScalaLibrary) {
      getFixture.allowTreeAccessForAllFiles()
      setUpLibraries()
    }
  }

  override def tearDown(): Unit = {
    disposeLibraries()
    super.tearDown()
  }

  protected def configureJavaFile(fileText: String, className: String,
                                  packageName: String = null): Unit = inWriteAction {
    val sourceRoot = getSourceRoot
    val root = packageName match {
      case null => sourceRoot
      case _ => sourceRoot.createChildDirectory(null, packageName)
    }

    val file = root.createChildData(null, s"$className.java")
    saveText(file, normalize(fileText))
  }

  protected def configureFromFileText(fileText: String): PsiFile =
    getFixture.configureByText(ScalaFileType.INSTANCE, normalize(fileText))

  protected def checkTextHasNoErrors(text: String): Unit = {
    getFixture.configureByText(ScalaFileType.INSTANCE, text)
    CodeFoldingManager.getInstance(getProject).buildInitialFoldings(getEditor)

    if (shouldPass) {
      getFixture.testHighlighting(false, false, false, getFile.getVirtualFile)
    } else {
      try {
        getFixture.testHighlighting(false, false, false, getFile.getVirtualFile)
      } catch {
        case _: AssertionError => return
      }
      failingTestPassed()
    }
  }

  protected def failingTestPassed(): Unit = throw new RuntimeException(failingPassed)

  protected def getCurrentCodeStyleSettings: CodeStyleSettings = CodeStyle.getSettings(getProject)

  protected def getCommonSettings = getCurrentCodeStyleSettings.getCommonSettings(ScalaLanguage.INSTANCE)
}

object ScalaLightCodeInsightFixtureTestAdapter {
  def normalize(text: String, stripTrailingSpaces: Boolean = true): String =
    text.stripMargin.replace("\r", "") match {
      case result if stripTrailingSpaces => result.trim
      case result => result
    }

  def findCaretOffset(text: String, stripTrailingSpaces: Boolean): (String, Int) = {
    val normalized = normalize(text, stripTrailingSpaces)
    (normalized.replace(CARET_MARKER, ""), normalized.indexOf(CARET_MARKER))
  }
}
