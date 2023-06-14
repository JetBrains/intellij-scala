package org.jetbrains.plugins.scala.project

import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.LatestScalaVersions
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader, ScalaSDKLoader}
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.junit.Assert.{assertEquals, fail}

class ConfigureIncrementalCompilerTest extends JavaCodeInsightFixtureTestCase {

  private def kotlinProjectIncrementalityTypeTest(loaders: Seq[LibraryLoader], expectedIncrementalities: Seq[IncrementalityType]): Unit = {
    if (loaders.sizeCompare(expectedIncrementalities) != 0) {
      fail("The number of provided library loaders should exactly match the number of expected incrementality types. " +
        "Each incrementality type is checked after a library loader is initialized.")
    }

    try {
      val scalaVersion = LatestScalaVersions.Scala_2_13

      // Check default value of incrementality type.
      val project = getProject

      def projectIncrementalityType: IncrementalityType = ScalaCompilerConfiguration.instanceIn(project).incrementalityType

      assertEquals(IncrementalityType.SBT, projectIncrementalityType)

      loaders.zip(expectedIncrementalities).foreach { case (loader, expected) =>
        loader.init(getModule, scalaVersion)

        // Check incrementality type after setting up a library loader.
        assertEquals(expected, projectIncrementalityType)
      }
    } finally {
      loaders.foreach(_.clean(getModule))
    }
  }

  def testMixedScalaKotlinProjectIncrementalityType(): Unit = {
    kotlinProjectIncrementalityTypeTest(
      Seq(ScalaSDKLoader(), IvyManagedLoader("org.jetbrains.kotlin" % "kotlin-stdlib" % "1.8.21")),
      Seq(IncrementalityType.SBT, IncrementalityType.IDEA)
    )
  }

  def testOnlyKotlinNoScalaProjectIncrementalityType(): Unit = {
    kotlinProjectIncrementalityTypeTest(
      Seq(IvyManagedLoader("org.jetbrains.kotlin" % "kotlin-stdlib" % "1.8.21")),
      Seq(IncrementalityType.SBT)
    )
  }
}
