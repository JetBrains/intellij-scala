package org.jetbrains.plugins.scala.base

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.util.TestUtils

class ScalaLightCodeInsightFixtureTestCase extends LightCodeInsightFixtureTestCase {

  protected def rootPath = TestUtils.getTestDataPath + "/"

  def loadScalaLibrary = false

  var myLibraryLoader: ScalaLibraryLoader = null

  protected def getDefaultScalaSDKVersion: TestUtils.ScalaSdkVersion = TestUtils.DEFAULT_SCALA_SDK_VERSION

  override def setUp(): Unit = {
    super.setUp()

    if (loadScalaLibrary) {
      myLibraryLoader = new ScalaLibraryLoader(getProject, myFixture.getModule, rootPath)
      myLibraryLoader.loadLibrary(getDefaultScalaSDKVersion)
    }
  }

  override def tearDown(): Unit = {
    if (myLibraryLoader != null) {
      myLibraryLoader.clean()
      myLibraryLoader = null
    }
    super.tearDown()
  }
}
