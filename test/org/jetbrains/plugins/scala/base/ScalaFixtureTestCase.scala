package org.jetbrains.plugins.scala
package base

import com.intellij.testFramework.fixtures.{CodeInsightFixtureTestCase, CodeInsightTestFixture}
import org.jetbrains.plugins.scala.base.libraryLoaders.{JdkLoader, LibraryLoader, ScalaLibraryLoader}
import org.jetbrains.plugins.scala.debugger.DefaultScalaSdkOwner

/**
  * User: Alexander Podkhalyuzin
  * Date: 03.08.2009
  */

abstract class ScalaFixtureTestCase
  extends CodeInsightFixtureTestCase with DefaultScalaSdkOwner {

  protected val includeReflectLibrary: Boolean = false

  override def getFixture: CodeInsightTestFixture = myFixture

  override def librariesLoaders: Seq[LibraryLoader] = Seq(
    ScalaLibraryLoader(includeReflectLibrary),
    JdkLoader()
  )

  override protected def setUp(): Unit = {
    super.setUp()
    setUpLibraries()
  }

  override def tearDown(): Unit = {
    tearDownLibraries()
    super.tearDown()
  }
}