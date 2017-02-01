package org.jetbrains.plugins.scala
package base

import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture.CARET_MARKER
import com.intellij.testFramework.fixtures.{CodeInsightTestFixture, LightCodeInsightFixtureTestCase}
import org.jetbrains.plugins.scala.base.libraryLoaders.{CompositeLibrariesLoader, JdkLoader, ScalaLibraryLoader}
import org.jetbrains.plugins.scala.debugger.ScalaVersion
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

/**
  * User: Dmitry Naydanov
  * Date: 3/5/12
  */

abstract class ScalaLightCodeInsightFixtureTestAdapter
  extends LightCodeInsightFixtureTestCase with TestFixtureProvider with ScalaVersion {

  private var librariesLoader: Option[CompositeLibrariesLoader] = None

  override def getFixture: CodeInsightTestFixture = myFixture

  override protected def setUp(): Unit = {
    super.setUp()

    if (loadScalaLibrary) {
      getFixture.allowTreeAccessForAllFiles()

      implicit val module = getFixture.getModule
      implicit val project = getProject
      implicit val version = scalaSdkVersion

      librariesLoader = Some(CompositeLibrariesLoader(
        ScalaLibraryLoader(),
        JdkLoader()
      ))
      librariesLoader.foreach(_.init)
    }
  }

  protected override def scalaSdkVersion: ScalaSdkVersion = TestUtils.DEFAULT_SCALA_SDK_VERSION

  protected def loadScalaLibrary = true

  protected def checkTextHasNoErrors(text: String): Unit = {
    getFixture.configureByText("dummy.scala", text)
    CodeFoldingManager.getInstance(getProject).buildInitialFoldings(getEditor)

    getFixture.testHighlighting(false, false, false, getFile.getVirtualFile)
  }

  protected override def tearDown(): Unit = {
    librariesLoader.foreach(_.clean())
    librariesLoader = None
    super.tearDown()
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
