package org.jetbrains.plugins.scala
package base

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.base.libraryLoaders.{HeavyJDKLoader, LibraryLoader, ScalaSDKLoader}

abstract class ScalaFixtureTestCase extends CodeInsightFixtureTestCase with ScalaSdkOwner {

  protected val CARET = EditorTestUtil.CARET_TAG

  protected val includeCompilerAsLibrary: Boolean = false

  protected final implicit def projectContext: Project = getProject

  override def librariesLoaders: Seq[LibraryLoader] = Seq(
    ScalaSDKLoader(includeScalaCompilerIntoLibraryClasspath = includeCompilerAsLibrary),
    HeavyJDKLoader()
  )

  override protected def setUp(): Unit = {
    super.setUp()
    setUpLibraries(myModule)
    Registry.get("ast.loading.filter").setValue(true, getTestRootDisposable)
  }

  override def tearDown(): Unit = {
    disposeLibraries(myModule)
    super.tearDown()
  }
}