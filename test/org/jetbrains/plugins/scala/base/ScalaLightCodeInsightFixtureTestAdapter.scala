package org.jetbrains.plugins.scala
package base

import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture.CARET_MARKER
import com.intellij.testFramework.fixtures.{CodeInsightTestFixture, LightCodeInsightFixtureTestCase}
import org.jetbrains.plugins.scala.base.libraryLoaders.{JdkLoader, LibraryLoader, ScalaLibraryLoader}
import org.jetbrains.plugins.scala.debugger.DefaultScalaSdkOwner
import org.jetbrains.plugins.scala.util.TestUtils

/**
  * User: Dmitry Naydanov
  * Date: 3/5/12
  */

abstract class ScalaLightCodeInsightFixtureTestAdapter
  extends LightCodeInsightFixtureTestCase with DefaultScalaSdkOwner {

  override def getFixture: CodeInsightTestFixture = myFixture

  override def getTestDataPath: String = TestUtils.getTestDataPath + "/"

  protected def loadScalaLibrary: Boolean = true

  override def librariesLoaders: Seq[LibraryLoader] = Seq(
    ScalaLibraryLoader(),
    JdkLoader()
  )

  override protected def setUp(): Unit = {
    super.setUp()

    if (loadScalaLibrary) {
      getFixture.allowTreeAccessForAllFiles()
      setUpLibraries()
    }
  }

  protected override def tearDown(): Unit = {
    tearDownLibraries()
    super.tearDown()
  }

  protected def checkTextHasNoErrors(text: String): Unit = {
    getFixture.configureByText(ScalaFileType.INSTANCE, text)
    CodeFoldingManager.getInstance(getProject).buildInitialFoldings(getEditor)

    getFixture.testHighlighting(false, false, false, getFile.getVirtualFile)
  }
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
