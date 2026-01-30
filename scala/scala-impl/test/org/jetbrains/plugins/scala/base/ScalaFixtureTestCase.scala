package org.jetbrains.plugins.scala
package base

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.{CodeInsightFixtureTestCase, IndexingModeCodeInsightTestFixture}
import com.intellij.testFramework.{EditorTestUtil, IdeaTestUtil, IndexingTestUtil}
import com.intellij.util.lang.JavaVersion
import org.jetbrains.plugins.scala.base.libraryLoaders.{LibraryLoader, PlatformSdkJdkLoader, ScalaSDKLoader}

abstract class ScalaFixtureTestCase extends CodeInsightFixtureTestCase[ModuleFixtureBuilder[_]] with ScalaSdkOwner {

  protected val CARET = EditorTestUtil.CARET_TAG

  protected val includeCompilerAsLibrary: Boolean = false

  protected val includeScalaLibrarySources: Boolean = false

  protected final implicit def projectContext: Project = getProject

  protected lazy val jdk: Sdk = IdeaTestUtil.getMockJdk(JavaVersion.compose(17))

  override protected def librariesLoaders: Seq[LibraryLoader] = Seq(
    ScalaSDKLoader(includeScalaCompilerIntoLibraryClasspath = includeCompilerAsLibrary, includeScalaLibrarySources = includeScalaLibrarySources),
    new PlatformSdkJdkLoader(jdk)
  )

  //start section: indexing mode setup
  private[this] var indexingMode: IndexingMode = IndexingMode.SMART

  // SCL-21849
  protected def getIndexingMode: IndexingMode = indexingMode
  protected def setIndexingMode(mode: IndexingMode): Unit = indexingMode = mode
  //end section: indexing mode setup

  override protected def setUp(): Unit = {
    super.setUp()
    setUpLibraries(myModule)
    IndexingTestUtil.waitUntilIndexesAreReady(getProject)

    Registry.get("ast.loading.filter").setValue(true, getTestRootDisposable)
  }

  override def tuneFixture(moduleBuilder: ModuleFixtureBuilder[_]): Unit = {
    super.tuneFixture(moduleBuilder)

    indexingMode = this.getIndexingModeConsideringDumbModeChecks
    myFixture = IndexingModeCodeInsightTestFixture.Companion.wrapFixture(myFixture, indexingMode)
  }

  override def tearDown(): Unit = {
    disposeLibraries(myModule)
    super.tearDown()
  }
}
