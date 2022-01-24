package org.jetbrains.sbt.project

import junit.framework.TestCase
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals
import org.jetbrains.sbt.structure.{Configuration, ModuleDependencyData, ModuleIdentifier}

class SbtProjectResolverTest extends TestCase {

  def testResolveLibraryDependencyConflicts_HealthCheckTest(): Unit = {
    def dep(org: String, art: String, version: String, scopes: String*) =
      ModuleDependencyData(ModuleIdentifier(org, art, version, "jar", ""), scopes.map(Configuration.apply))

    val dependenciesBefore: Seq[ModuleDependencyData] = Seq(
      dep("io.netty", "netty-all", "4.0.33.Final", "runtime"),
      dep("org.apache.commons", "commons-compress", "1.21", "compile"),
      dep("io.netty", "netty-all", "4.1.15.Final", "provided"),
      dep("io.netty", "netty-all", "4.1.17.Final", "test"),
      dep("org.scala-lang", "scala-library", "2.13.6", "compile")
    )

    val dependenciesAfterExpected: Seq[ModuleDependencyData] = Seq(
      dep("io.netty", "netty-all", "4.1.17.Final", "provided"),
      dep("org.apache.commons", "commons-compress", "1.21", "compile"),
      dep("org.scala-lang", "scala-library", "2.13.6", "compile")
    )

    val dependenciesAfterActual = SbtProjectResolver.resolveLibraryDependencyConflicts(dependenciesBefore)
    assertCollectionEquals(
      dependenciesAfterExpected,
      dependenciesAfterActual,
    )
  }

}