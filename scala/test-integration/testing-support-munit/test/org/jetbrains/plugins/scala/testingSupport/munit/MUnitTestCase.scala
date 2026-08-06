package org.jetbrains.plugins.scala.testingSupport.munit

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.testingSupport.ScalaTestingTestCase
import org.jetbrains.plugins.scala.testingSupport.junit.JUnitIntegrationTestConfigAssertions
import org.jetbrains.plugins.scala.testingSupport.test.munit.MUnitConfiguration

abstract class MUnitTestCase extends ScalaTestingTestCase with JUnitIntegrationTestConfigAssertions {

  def munitVersion: String

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_2_13

  override protected val expectedDefaultRunConfigurationClass: Class[MUnitConfiguration] =
    classOf[MUnitConfiguration]

  override protected def additionalLibraries: Seq[LibraryLoader] =
    IvyManagedLoader(("org.scalameta" %% "munit" % munitVersion).transitive()) ::
      IvyManagedLoader(("org.scalameta" %% "munit-scalacheck" % munitVersion).transitive()) ::
      Nil
}
