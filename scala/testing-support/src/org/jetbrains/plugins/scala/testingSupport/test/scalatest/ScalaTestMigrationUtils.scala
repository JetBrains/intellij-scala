package org.jetbrains.plugins.scala.testingSupport.test.scalatest

import scala.collection.immutable.ListMap

/**
 * ScalaTest 3.1.0 had a lot of refactorings. A lot of classes moved to other packages.
 * This utility class helps to migrate old (prior 3.1.0) class full qualified names to new ones.
 *
 *  - In 3.1.0 old-style names were  deprecated but continued to work
 *  - In 3.2.0 old-style names were deprecated
 *
 * @see http://www.scalatest.org/release_notes/3.1.0
 * @see http://www.scalatest.org/release_notes/3.2.0
 */
object ScalaTestMigrationUtils {

  object MigrationOps {

    private def selectMigration(fqnToMigrate: String): Option[(String, String)] = {
      val newStyles = AllStyles.collect { case migration if shouldBeMigratedFrom(fqnToMigrate, migration._1) => migration }
      newStyles match {
        case Nil             => None
        case newStyle :: Nil => Some(newStyle)
        case ambiguous       =>
          throw new AssertionError(s"Expected single replacement for $fqnToMigrate, but found:\n${ambiguous.mkString("\n")}")
      }
    }

    private def shouldBeMigratedFrom(fqnToMigrate: String, oldFqn: String) =
      if (fqnToMigrate == oldFqn) true
      else if (fqnToMigrate.startsWith(oldFqn)) fqnToMigrate.drop(oldFqn.length).headOption.contains('.')
      else false

    implicit class SetOps(private val fqns: Set[String]) extends AnyVal {
      def withMigrated: Set[String] = {
        val migrated = for {
          fqn <- fqns
          (oldstyle, newStyle) <- selectMigration(fqn)
        } yield (fqn, fqn.replace(oldstyle, newStyle))
        fqns ++ migrated.map(_._2)
      }
    }

    implicit class ListOps(private val fqns: List[String]) extends AnyVal {
      def migrated: List[String] = {
        val result = for {
          fqn <- fqns
          (oldstyle, newStyle) <- selectMigration(fqn)
        } yield (fqn, fqn.replace(oldstyle, newStyle))
        result.map(_._2)
      }
      def withMigrated: List[String] =
        fqns ++ migrated
    }
  }

  //
  // Mappings below copied from tables from http://www.scalatest.org/release_notes/3.1.0
  //

  private val FunSuite =
    ListMap(
      "org.scalatest.FunSuite" -> "org.scalatest.funsuite.AnyFunSuite",
      "org.scalatest.FunSuiteLike" -> "org.scalatest.funsuite.AnyFunSuiteLike",
      "org.scalatest.AsyncFunSuite" -> "org.scalatest.funsuite.AsyncFunSuite",
      "org.scalatest.AsyncFunSuiteLike" -> "org.scalatest.funsuite.AsyncFunSuiteLike",

      "org.scalatest.fixture.FunSuite" -> "org.scalatest.funsuite.FixtureAnyFunSuite",
      "org.scalatest.fixture.FunSuiteLike" -> "org.scalatest.funsuite.FixtureAnyFunSuiteLike",
      "org.scalatest.fixture.AsyncFunSuite" -> "org.scalatest.funsuite.FixtureAsyncFunSuite",
      "org.scalatest.fixture.AsyncFunSuiteLike" -> "org.scalatest.funsuite.FixtureAsyncFunSuiteLike",
    )

  private val FlatSpec =
    ListMap(
      "org.scalatest.FlatSpec" -> "org.scalatest.flatspec.AnyFlatSpec",
      "org.scalatest.FlatSpecLike" -> "org.scalatest.flatspec.AnyFlatSpecLike",
      "org.scalatest.AsyncFlatSpec" -> "org.scalatest.flatspec.AsyncFlatSpec",
      "org.scalatest.AsyncFlatSpecLike" -> "org.scalatest.flatspec.AsyncFlatSpecLike",

      "org.scalatest.fixture.FlatSpec" -> "org.scalatest.flatspec.FixtureAnyFlatSpec",
      "org.scalatest.fixture.FlatSpecLike" -> "org.scalatest.flatspec.FixtureAnyFlatSpecLike",
      "org.scalatest.fixture.AsyncFlatSpec" -> "org.scalatest.flatspec.FixtureAsyncFlatSpec",
      "org.scalatest.fixture.AsyncFlatSpecLike" -> "org.scalatest.flatspec.FixtureAsyncFlatSpecLike",
    )

  private val FunSpec =
    ListMap(
      "org.scalatest.FunSpec" -> "org.scalatest.funspec.AnyFunSpec",
      "org.scalatest.FunSpecLike" -> "org.scalatest.funspec.AnyFunSpecLike",
      "org.scalatest.AsyncFunSpec" -> "org.scalatest.funspec.AsyncFunSpec",
      "org.scalatest.AsyncFunSpecLike" -> "org.scalatest.funspec.AsyncFunSpecLike",

      "org.scalatest.fixture.FunSpec" -> "org.scalatest.funspec.FixtureAnyFunSpec",
      "org.scalatest.fixture.FunSpecLike" -> "org.scalatest.funspec.FixtureAnyFunSpecLike",
      "org.scalatest.fixture.AsyncFunSpec" -> "org.scalatest.funspec.FixtureAsyncFunSpec",
      "org.scalatest.fixture.AsyncFunSpecLike" -> "org.scalatest.funspec.FixtureAsyncFunSpecLike",

      "org.scalatest.path.FunSpec" -> "org.scalatest.funspec.PathAnyFunSpec",
      "org.scalatest.path.FunSpecLike" -> "org.scalatest.funspec.PathAnyFunSpecLike",
    )

  private val WordSpec =
    ListMap(
      "org.scalatest.WordSpec" -> "org.scalatest.wordspec.AnyWordSpec",
      "org.scalatest.WordSpecLike" -> "org.scalatest.wordspec.AnyWordSpecLike",
      "org.scalatest.AsyncWordSpec" -> "org.scalatest.wordspec.AsyncWordSpec",
      "org.scalatest.AsyncWordSpecLike" -> "org.scalatest.wordspec.AsyncWordSpecLike",

      "org.scalatest.fixture.WordSpec" -> "org.scalatest.wordspec.FixtureAnyWordSpec",
      "org.scalatest.fixture.WordSpecLike" -> "org.scalatest.wordspec.FixtureAnyWordSpecLike",
      "org.scalatest.fixture.AsyncWordSpec" -> "org.scalatest.wordspec.FixtureAsyncWordSpec",
      "org.scalatest.fixture.AsyncWordSpecLike" -> "org.scalatest.wordspec.FixtureAsyncWordSpecLike",
    )

  private val FreeSpec =
    ListMap(
      "org.scalatest.FreeSpec" -> "org.scalatest.freespec.AnyFreeSpec",
      "org.scalatest.FreeSpecLike" -> "org.scalatest.freespec.AnyFreeSpecLike",
      "org.scalatest.AsyncFreeSpec" -> "org.scalatest.freespec.AsyncFreeSpec",
      "org.scalatest.AsyncFreeSpecLike" -> "org.scalatest.freespec.AsyncFreeSpecLike",

      "org.scalatest.fixture.FreeSpec" -> "org.scalatest.freespec.FixtureAnyFreeSpec",
      "org.scalatest.fixture.FreeSpecLike" -> "org.scalatest.freespec.FixtureAnyFreeSpecLike",
      "org.scalatest.fixture.AsyncFreeSpec" -> "org.scalatest.freespec.FixtureAsyncFreeSpec",
      "org.scalatest.fixture.AsyncFreeSpecLike" -> "org.scalatest.freespec.FixtureAsyncFreeSpecLike",

      "org.scalatest.path.FreeSpec" -> "org.scalatest.freespec.PathAnyFreeSpec",
      "org.scalatest.path.FreeSpecLike" -> "org.scalatest.freespec.PathAnyFreeSpecLike",
    )

  private val PropSpec =
    ListMap(
      "org.scalatest.PropSpec" -> "org.scalatest.propspec.AnyPropSpec",
      "org.scalatest.PropSpecLike" -> "org.scalatest.propspec.AnyPropSpecLike",
      "org.scalatest.AsyncPropSpec" -> "org.scalatest.propspec.AsyncPropSpec",
      "org.scalatest.AsyncPropSpecLike" -> "org.scalatest.propspec.AsyncPropSpecLike",

      "org.scalatest.fixture.PropSpec" -> "org.scalatest.propspec.FixtureAnyPropSpec",
      "org.scalatest.fixture.PropSpecLike" -> "org.scalatest.propspec.FixtureAnyPropSpecLike",
      "org.scalatest.fixture.AsyncPropSpec" -> "org.scalatest.propspec.FixtureAsyncPropSpec",
      "org.scalatest.fixture.AsyncPropSpecLike" -> "org.scalatest.propspec.FixtureAsyncPropSpecLike",
    )

  private val FeatureSpec =
    ListMap(
      "org.scalatest.FeatureSpec" -> "org.scalatest.featurespec.AnyFeatureSpec",
      "org.scalatest.FeatureSpecLike" -> "org.scalatest.featurespec.AnyFeatureSpecLike",
      "org.scalatest.AsyncFeatureSpec" -> "org.scalatest.featurespec.AsyncFeatureSpec",
      "org.scalatest.AsyncFeatureSpecLike" -> "org.scalatest.featurespec.AsyncFeatureSpecLike",

      "org.scalatest.fixture.FeatureSpec" -> "org.scalatest.featurespec.FixtureAnyFeatureSpec",
      "org.scalatest.fixture.FeatureSpecLike" -> "org.scalatest.featurespec.FixtureAnyFeatureSpecLike",
      "org.scalatest.fixture.AsyncFeatureSpec" -> "org.scalatest.featurespec.FixtureAsyncFeatureSpec",
      "org.scalatest.fixture.AsyncFeatureSpecLike" -> "org.scalatest.featurespec.FixtureAsyncFeatureSpecLike",
    )

  private val AllStyles: Seq[(String, String)] = Seq(
    FunSuite,
    FlatSpec,
    FunSpec,
    WordSpec,
    FreeSpec,
    PropSpec,
    FeatureSpec,
  ).flatten
}
