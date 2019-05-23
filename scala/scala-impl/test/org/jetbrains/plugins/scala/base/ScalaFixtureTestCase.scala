package org.jetbrains.plugins.scala
package base

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.fixtures.{CodeInsightFixtureTestCase, CodeInsightTestFixture}
import org.jetbrains.plugins.scala.base.libraryLoaders.{HeavyJDKLoader, LibraryLoader, ScalaSDKLoader}

/**
  * User: Alexander Podkhalyuzin
  * Date: 03.08.2009
  */

abstract class ScalaFixtureTestCase extends CodeInsightFixtureTestCase with DefaultScalaSdkOwner {

  protected val includeReflectLibrary: Boolean = false

  override final def getFixture: CodeInsightTestFixture = myFixture

  protected final implicit def projectContext: Project = getProject

  override def librariesLoaders: Seq[LibraryLoader] = Seq(
    ScalaSDKLoader(includeReflectLibrary),
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