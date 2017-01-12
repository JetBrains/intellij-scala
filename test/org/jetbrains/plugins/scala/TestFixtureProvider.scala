package org.jetbrains.plugins.scala

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.base.ScalaLibraryLoader
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

/**
  * @author mucianm 
  * @since 07.04.16.
  */
trait TestFixtureProvider {
  protected def fixture: CodeInsightTestFixture
  protected val rootPath: String

  protected val scalaSdkVersion: ScalaSdkVersion = TestUtils.DEFAULT_SCALA_SDK_VERSION
  protected val loadReflect: Boolean = false

  private[this] var libraryLoader: ScalaLibraryLoader = _

  def initFixture(): Unit = {
    libraryLoader = ScalaLibraryLoader.withMockJdk(fixture.getProject, fixture.getModule, rootPath, loadReflect)
    libraryLoader.loadScala(scalaSdkVersion)
  }

  def cleanFixture(): Unit = {
    if (libraryLoader != null) {
      libraryLoader.clean()
      libraryLoader = null
    }
  }
}
