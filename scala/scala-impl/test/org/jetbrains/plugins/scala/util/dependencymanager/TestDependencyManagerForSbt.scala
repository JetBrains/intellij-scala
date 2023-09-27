package org.jetbrains.plugins.scala.util.dependencymanager

import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.DependencyManagerBase.IvyResolver
import org.jetbrains.plugins.scala.project.Version

/**
 * Adds additional resolver for typesafe repository<br>
 * It's needed to be able to resolve sbt 0.13 releases, which is not published to maven central
 */
final class TestDependencyManagerForSbt(private val sbtVersion: Version) extends DependencyManagerBase {

  private val includeTypesafeRepo = sbtVersion < Version("1.0.0")

  private val typeSafeResolver = IvyResolver(
    "typesafe-releases",
    "https://repo.typesafe.com/typesafe/ivy-releases/[organisation]/[module]/[revision]/[type]s/[artifact](-[classifier]).[ext]"
  )

  override protected def resolvers: Seq[DependencyManagerBase.Resolver] = {
    val extraResolvers = if (includeTypesafeRepo) Seq(typeSafeResolver) else Nil
    super.resolvers ++ extraResolvers
  }

  override def equals(other: Any): Boolean = other match {
    case that: TestDependencyManagerForSbt =>
      sbtVersion == that.sbtVersion
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq(sbtVersion)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}