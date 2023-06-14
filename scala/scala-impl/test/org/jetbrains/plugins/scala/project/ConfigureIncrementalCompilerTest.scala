package org.jetbrains.plugins.scala.project

import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.LatestScalaVersions
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, ScalaSDKLoader}
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.junit.Assert.assertEquals

class ConfigureIncrementalCompilerTest extends JavaCodeInsightFixtureTestCase {

  def testMixedScalaKotlinProjectIncrementalityType(): Unit = {
    val loaders = Array(ScalaSDKLoader(), IvyManagedLoader("org.jetbrains.kotlin" % "kotlin-stdlib" % "1.8.21"))

    try {
      val scalaVersion = LatestScalaVersions.Scala_2_13

      // Check default value of incrementality type.
      val project = getProject
      var incrementalityType = ScalaCompilerConfiguration.instanceIn(project).incrementalityType
      assertEquals(IncrementalityType.SBT, incrementalityType)

      loaders(0).init(getModule, scalaVersion)

      // Check incrementality type after setting up the Scala SDK.
      incrementalityType = ScalaCompilerConfiguration.instanceIn(project).incrementalityType
      assertEquals(IncrementalityType.SBT, incrementalityType)

      loaders(1).init(getModule, scalaVersion)

      // Check incrementality type after setting up the Kotlin standard library.
      incrementalityType = ScalaCompilerConfiguration.instanceIn(project).incrementalityType
      assertEquals(IncrementalityType.IDEA, incrementalityType)
    } finally {
      loaders.foreach(_.clean(getModule))
    }
  }

  def testOnlyKotlinNoScalaProjectIncrementalityType(): Unit = {
    val loaders = Array(ScalaSDKLoader(), IvyManagedLoader("org.jetbrains.kotlin" % "kotlin-stdlib" % "1.8.21"))

    try {
      val scalaVersion = LatestScalaVersions.Scala_2_13

      // Check default value of incrementality type.
      val project = getProject
      var incrementalityType = ScalaCompilerConfiguration.instanceIn(project).incrementalityType
      assertEquals(IncrementalityType.SBT, incrementalityType)

      loaders(0).init(getModule, scalaVersion)

      // Check incrementality type after setting up the Kotlin standard library.
      incrementalityType = ScalaCompilerConfiguration.instanceIn(project).incrementalityType
      assertEquals(IncrementalityType.SBT, incrementalityType)
    } finally {
      loaders.foreach(_.clean(getModule))
    }
  }
}
