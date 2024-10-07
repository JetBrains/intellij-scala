package org.jetbrains.plugins.scala.projectHighlighting.scalaLibrary

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.libraryLoaders.ScalaSDKLoader

class ScalaLibraryHighlightingTest_3 extends ScalaLibraryHighlightingTest {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_3

  /**
   * Do not include Scala 2 library transitive dependency.
   * Scala 2.13 library is tested independently in [[ScalaLibraryHighlightingTest_2_13]]
   */
  override protected def customScalaSdkLoader: ScalaSDKLoader = super.customScalaSdkLoader.copy(
    includeScalaLibraryTransitiveDependencies = false
  )

  override protected val filesWithProblems: Map[String, Set[TextRange]] = Map()

  override protected def scalaLibraryJarName: String = "scala3-library_3"
}
