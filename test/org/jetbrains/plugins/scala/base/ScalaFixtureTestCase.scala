package org.jetbrains.plugins.scala
package base

import com.intellij.testFramework.fixtures.{CodeInsightFixtureTestCase, CodeInsightTestFixture}
import org.jetbrains.plugins.scala.base.libraryLoaders.{CompositeLibrariesLoader, JdkLoader, ScalaLibraryLoader}
import org.jetbrains.plugins.scala.debugger.DefaultScalaSdkOwner

/**
  * User: Alexander Podkhalyuzin
  * Date: 03.08.2009
  */

abstract class ScalaFixtureTestCase
  extends CodeInsightFixtureTestCase with TestFixtureProvider with DefaultScalaSdkOwner {

  private var librariesLoader: Option[CompositeLibrariesLoader] = None

  protected val includeReflectLibrary: Boolean = false

  override def getFixture: CodeInsightTestFixture = myFixture

  override protected def setUp(): Unit = {
    super.setUp()

    implicit val project = getProject
    implicit val module = myFixture.getModule

    librariesLoader = Some(CompositeLibrariesLoader(
      ScalaLibraryLoader(includeReflectLibrary),
      JdkLoader()
    ))
    librariesLoader.foreach(_.init)
  }

  override def tearDown(): Unit = {
    librariesLoader.foreach(_.clean())
    librariesLoader = None

    super.tearDown()
  }
}