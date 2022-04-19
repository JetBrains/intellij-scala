package org.jetbrains.plugins.scala.testingSupport.munit

import com.intellij.execution.actions.RunConfigurationProducer
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.testingSupport.ScalaTestingTestCase
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestConfigurationProducer
import org.jetbrains.plugins.scala.testingSupport.test.munit.MUnitConfigurationProducer

abstract class MUnitTestCase extends ScalaTestingTestCase {

  val LatestMunitVersion = "0.7.29"

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_2_13

  override protected lazy val configurationProducer: AbstractTestConfigurationProducer[_] =
    RunConfigurationProducer.getInstance(classOf[MUnitConfigurationProducer])

  override protected def additionalLibraries: Seq[LibraryLoader] =
    IvyManagedLoader(("org.scalameta" %% "munit" % LatestMunitVersion).transitive()) ::
      IvyManagedLoader(("org.scalameta" %% "munit-scalacheck" % LatestMunitVersion).transitive()) ::
      Nil
}
