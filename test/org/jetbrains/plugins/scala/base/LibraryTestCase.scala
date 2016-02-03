package org.jetbrains.plugins.scala.base

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.util.TestUtils

/**
  * @author Pavel Fatin
  */
abstract class LibraryTestCase extends LightCodeInsightFixtureTestCase {
  private var libraryLoader: Option[ScalaLibraryLoader] = None

  override def setUp() {
    super.setUp()

    val loader = ScalaLibraryLoader.withMockJdk(myFixture.getProject, myFixture.getModule, rootPath = null)
    libraryLoader = Some(loader)

    loader.loadScala(TestUtils.DEFAULT_SCALA_SDK_VERSION)
  }

  override def tearDown() {
    try {
      libraryLoader.foreach(_.clean())
    } finally {
      super.tearDown()
    }
  }
}
