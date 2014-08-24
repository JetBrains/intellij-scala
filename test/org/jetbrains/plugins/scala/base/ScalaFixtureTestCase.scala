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
  private val JDK_HOME = TestUtils.getMockJdk

  protected def rootPath = TestUtils.getTestDataPath + "/"

  var libLoader: ScalaLibraryLoader = null

  override protected def setUp {
    super.setUp()

    libLoader = new ScalaLibraryLoader(myFixture.getProject, myFixture.getModule, rootPath,
      javaSdk = Some(JavaSdk.getInstance.createJdk("java sdk", JDK_HOME, false)))
    libLoader.loadLibrary(TestUtils.DEFAULT_SCALA_SDK_VERSION)
  }

  override def tearDown(): Unit = {
    libLoader.clean()
    super.tearDown()
  }
}