package org.jetbrains.plugins.scala
package base


import com.intellij.testFramework.fixtures.{CodeInsightFixtureTestCase, CodeInsightTestFixture}
import org.jetbrains.plugins.scala.base.libraryLoaders.ScalaLibraryLoader
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

/**
 * User: Alexander Podkhalyuzin
 * Date: 03.08.2009
 */

abstract class ScalaFixtureTestCase(private val scalaVersion: ScalaSdkVersion = TestUtils.DEFAULT_SCALA_SDK_VERSION,
                                    private val loadReflect: Boolean = false) extends CodeInsightFixtureTestCase with TestFixtureProvider {

  var libLoader: ScalaLibraryLoader = _

  override protected def setUp(): Unit = {
    super.setUp()

    libLoader = ScalaLibraryLoader.withMockJdk(myFixture.getProject, myFixture.getModule, loadReflect)
    libLoader.init(scalaVersion)
  }

  override def tearDown(): Unit = {
    libLoader.clean()
    libLoader = null
    super.tearDown()
  }

  override def getFixture: CodeInsightTestFixture = myFixture
}