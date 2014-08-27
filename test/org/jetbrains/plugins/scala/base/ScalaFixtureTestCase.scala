package org.jetbrains.plugins.scala
package base


import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.util.TestUtils

/**
 * User: Alexander Podkhalyuzin
 * Date: 03.08.2009
 */

abstract class ScalaFixtureTestCase extends CodeInsightFixtureTestCase {

  protected def rootPath = TestUtils.getTestDataPath + "/"

  var libLoader: ScalaLibraryLoader = null

  override protected def setUp {
    super.setUp()

    libLoader = ScalaLibraryLoader.withMockJdk(myFixture.getProject, myFixture.getModule, rootPath)
    libLoader.loadLibrary(TestUtils.DEFAULT_SCALA_SDK_VERSION)
  }

  override def tearDown(): Unit = {
    libLoader.clean()
    super.tearDown()
  }
}