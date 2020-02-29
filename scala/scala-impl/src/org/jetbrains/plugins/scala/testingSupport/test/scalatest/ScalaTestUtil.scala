package org.jetbrains.plugins.scala.testingSupport.test.scalatest
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestMigrationUtils.MigrationOps._

object ScalaTestUtil {

  lazy val itWordFqns: Set[String] = {
    val flatSpecItWordFqns = Set(
      "org.scalatest.FlatSpecLike.ItWord",
      "org.scalatest.FlatSpecLike.ItVerbStringTaggedAs",
      "org.scalatest.FlatSpecLike.ItVerbString",
      "org.scalatest.fixture.FlatSpecLike.ItWord",
      "org.scalatest.fixture.FlatSpecLike.ItVerbStringTaggedAs",
      "org.scalatest.fixture.FlatSpecLike.ItVerbString"
    )

    val asyncFlatSpecItWordFqns = Set(
      "org.scalatest.AsyncFlatSpecLike.ItWord",
      "org.scalatest.AsyncFlatSpecLike.ItVerbStringTaggedAs",
      "org.scalatest.AsyncFlatSpecLike.ItVerbString",
      "org.scalatest.fixture.AsyncFlatSpecLike.ItWord",
      "org.scalatest.fixture.AsyncFlatSpecLike.ItVerbStringTaggedAs",
      "org.scalatest.fixture.AsyncFlatSpecLike.ItVerbString",
    )
    flatSpecItWordFqns ++ asyncFlatSpecItWordFqns ++ Set(
      "org.scalatest.FunSpecLike.ItWord",
      "org.scalatest.fixture.FunSpecLike.ItWord",
      "org.scalatest.path.FunSpecLike.ItWord",
      "org.scalatest.WordSpecLike.ItWord",
      "org.scalatest.fixture.WordSpecLike.ItWord",
    )
  }.withMigrated

  lazy val theyWordFqns: Set[String] = {
    val flatSpecTheyWordFqns = Set(
      "org.scalatest.FlatSpecLike.TheyWord",
      "org.scalatest.FlatSpecLike.TheyVerbStringTaggedAs",
      "org.scalatest.FlatSpecLike.TheyVerbString",
      "org.scalatest.fixture.FlatSpecLike.TheyWord",
      "org.scalatest.fixture.FlatSpecLike.TheyVerbStringTaggedAs",
      "org.scalatest.fixture.FlatSpecLike.TheyVerbString"
    )

    val asyncFlatSpecTheyWordFqns = Set(
      "org.scalatest.AsyncFlatSpecLike.TheyWord",
      "org.scalatest.AsyncFlatSpecLike.TheyVerbStringTaggedAs",
      "org.scalatest.AsyncFlatSpecLike.TheyVerbString",
      "org.scalatest.fixture.AsyncFlatSpecLike.TheyWord",
      "org.scalatest.fixture.AsyncFlatSpecLike.TheyVerbStringTaggedAs",
      "org.scalatest.fixture.AsyncFlatSpecLike.TheyVerbString",
    )

    flatSpecTheyWordFqns ++ asyncFlatSpecTheyWordFqns ++ Set(
      "org.scalatest.FunSpecLike.TheyWord",
      "org.scalatest.fixture.FunSpecLike.TheyWord",
      "org.scalatest.path.FunSpecLike.TheyWord",
      "org.scalatest.WordSpecLike.TheyWord",
      "org.scalatest.fixture.WordSpecLike.TheyWord",
    )
  }.withMigrated

  lazy val funSuiteBases: List[String] = List(
    "org.scalatest.FunSuite",
    "org.scalatest.FunSuiteLike",
    "org.scalatest.fixture.FunSuite",
    "org.scalatest.fixture.FunSuiteLike",
    "org.scalatest.fixture.FixtureFunSuite",
    "org.scalatest.fixture.MultipleFixtureFunSuite"
  ).withMigrated

  lazy val featureSpecBases: List[String] = List(
    "org.scalatest.featurespec.AnyFeatureSpecLike", // 3.1.0
    "org.scalatest.FeatureSpec",
    "org.scalatest.FeatureSpecLike",
    "org.scalatest.fixture.FeatureSpec",
    "org.scalatest.fixture.FeatureSpecLike",
    "org.scalatest.fixture.FixtureFeatureSpec",
    "org.scalatest.fixture.MultipleFixtureFeatureSpec"
  ).withMigrated

  lazy val freeSpecBases: List[String] = List(
    "org.scalatest.FreeSpec",
    "org.scalatest.FreeSpecLike",
    "org.scalatest.fixture.FreeSpec",
    "org.scalatest.fixture.FreeSpecLike",
    "org.scalatest.fixture.FixtureFreeSpec",
    "org.scalatest.fixture.MultipleFixtureFreeSpec",
    "org.scalatest.path.FreeSpec",
    "org.scalatest.path.FreeSpecLike"
  ).withMigrated

  lazy val JUnit3SuiteBases: List[String] = List(
    "org.scalatest.junit.JUnit3Suite"
  ).withMigrated

  lazy val JUnitSuiteBases: List[String] = List(
    "org.scalatest.junit.JUnitSuite",
    "org.scalatest.junit.JUnitSuiteLike"
  ).withMigrated

  lazy val propSpecBases: List[String] = List(
    "org.scalatest.PropSpec",
    "org.scalatest.PropSpecLike",
    "org.scalatest.fixture.PropSpec",
    "org.scalatest.fixture.PropSpecLike",
    "org.scalatest.fixture.FixturePropSpec",
    "org.scalatest.fixture.MultipleFixturePropSpec"
  ).withMigrated

  lazy val funSpecBasesPre2_0: List[String] = List(
    "org.scalatest.Spec",
    "org.scalatest.SpecLike",
    "org.scalatest.fixture.Spec",
    "org.scalatest.fixture.SpecLike",
    "org.scalatest.fixture.FixtureSpec",
    "org.scalatest.fixture.MultipleFixtureSpec"
  ).withMigrated

  lazy val funSpecBasesPost2_0: List[String] = List(
    "org.scalatest.FunSpec",
    "org.scalatest.FunSpecLike",
    "org.scalatest.fixture.FunSpec",
    "org.scalatest.fixture.FunSpecLike",
    "org.scalatest.path.FunSpec",
    "org.scalatest.path.FunSpecLike"
  ).withMigrated

  lazy val testNGSuiteBases: List[String] = List(
    "org.scalatest.testng.TestNGSuite",
    "org.scalatest.testng.TestNGSuiteLike"
  ).withMigrated

  lazy val flatSpecBases: List[String] = List(
    "org.scalatest.FlatSpec",
    "org.scalatest.FlatSpecLike",
    "org.scalatest.fixture.FlatSpec",
    "org.scalatest.fixture.FlatSpecLike",
    "org.scalatest.fixture.FixtureFlatSpec",
    "org.scalatest.fixture.MultipleFixtureFlatSpec"
  ).withMigrated

  lazy val wordSpecBases: List[String] = List(
    "org.scalatest.WordSpec",
    "org.scalatest.WordSpecLike",
    "org.scalatest.fixture.WordSpec",
    "org.scalatest.fixture.WordSpecLike",
    "org.scalatest.fixture.FixtureWordSpec",
    "org.scalatest.fixture.MultipleFixtureWordSpec"
  ).withMigrated

  lazy val suitePaths: List[String] = List(
    "org.scalatest.Suite"
  ).withMigrated
}
