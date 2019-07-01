package org.jetbrains.plugins.scala.testingSupport.test.scalatest

/**
 * @author Roman.Shein
 * @since 10.04.2015.
 */
object ScalaTestUtil {
  val flatSpecItWordFqns: Set[String] = Set(
    "org.scalatest.FlatSpecLike.ItWord",
    "org.scalatest.FlatSpecLike.ItVerbStringTaggedAs",
    "org.scalatest.FlatSpecLike.ItVerbString",
    "org.scalatest.fixture.FlatSpecLike.ItWord",
    "org.scalatest.fixture.FlatSpecLike.ItVerbStringTaggedAs",
    "org.scalatest.fixture.FlatSpecLike.ItVerbString"
  )

  val asyncFlatSpecItWordFqns: Set[String] = Set(
    "org.scalatest.AsyncFlatSpecLike.ItWord",
    "org.scalatest.AsyncFlatSpecLike.ItVerbStringTaggedAs",
    "org.scalatest.AsyncFlatSpecLike.ItVerbString",
    "org.scalatest.fixture.AsyncFlatSpecLike.ItWord",
    "org.scalatest.fixture.AsyncFlatSpecLike.ItVerbStringTaggedAs",
    "org.scalatest.fixture.AsyncFlatSpecLike.ItVerbString",
  )

  val itWordFqns: Set[String] = flatSpecItWordFqns ++ asyncFlatSpecItWordFqns ++ Set(
    "org.scalatest.FunSpecLike.ItWord",
    "org.scalatest.fixture.FunSpecLike.ItWord",
    "org.scalatest.path.FunSpecLike.ItWord",
    "org.scalatest.WordSpecLike.ItWord",
    "org.scalatest.fixture.WordSpecLike.ItWord",
  )

  val funSuiteBases: List[String] = List(
    "org.scalatest.FunSuite",
    "org.scalatest.FunSuiteLike",
    "org.scalatest.fixture.FunSuite",
    "org.scalatest.fixture.FunSuiteLike",
    "org.scalatest.fixture.FixtureFunSuite",
    "org.scalatest.fixture.MultipleFixtureFunSuite"
  )

  val featureSpecBases: List[String] = List(
    "org.scalatest.FeatureSpec",
    "org.scalatest.FeatureSpecLike",
    "org.scalatest.fixture.FeatureSpec",
    "org.scalatest.fixture.FeatureSpecLike",
    "org.scalatest.fixture.FixtureFeatureSpec",
    "org.scalatest.fixture.MultipleFixtureFeatureSpec"
  )

  val freeSpecBases: List[String] = List(
    "org.scalatest.FreeSpec",
    "org.scalatest.FreeSpecLike",
    "org.scalatest.fixture.FreeSpec",
    "org.scalatest.fixture.FreeSpecLike",
    "org.scalatest.fixture.FixtureFreeSpec",
    "org.scalatest.fixture.MultipleFixtureFreeSpec",
    "org.scalatest.path.FreeSpec",
    "org.scalatest.path.FreeSpecLike"
  )

  val JUnit3SuiteBases: List[String] = List(
    "org.scalatest.junit.JUnit3Suite"
  )

  val JUnitSuiteBases: List[String] = List(
    "org.scalatest.junit.JUnitSuite",
    "org.scalatest.junit.JUnitSuiteLike"
  )

  val propSpecBases: List[String] = List(
    "org.scalatest.PropSpec",
    "org.scalatest.PropSpecLike",
    "org.scalatest.fixture.PropSpec",
    "org.scalatest.fixture.PropSpecLike",
    "org.scalatest.fixture.FixturePropSpec",
    "org.scalatest.fixture.MultipleFixturePropSpec"
  )

  val funSpecBasesPre2_0: List[String] = List(
    "org.scalatest.Spec",
    "org.scalatest.SpecLike",
    "org.scalatest.fixture.Spec",
    "org.scalatest.fixture.SpecLike",
    "org.scalatest.fixture.FixtureSpec",
    "org.scalatest.fixture.MultipleFixtureSpec"
  )

  val funSpecBasesPost2_0: List[String] = List(
    "org.scalatest.FunSpec",
    "org.scalatest.FunSpecLike",
    "org.scalatest.fixture.FunSpec",
    "org.scalatest.fixture.FunSpecLike",
    "org.scalatest.path.FunSpec",
    "org.scalatest.path.FunSpecLike"
  )

  val testNGSuiteBases: List[String] = List(
    "org.scalatest.testng.TestNGSuite",
    "org.scalatest.testng.TestNGSuiteLike"
  )

  val flatSpecBases: List[String] = List(
    "org.scalatest.FlatSpec",
    "org.scalatest.FlatSpecLike",
    "org.scalatest.fixture.FlatSpec",
    "org.scalatest.fixture.FlatSpecLike",
    "org.scalatest.fixture.FixtureFlatSpec",
    "org.scalatest.fixture.MultipleFixtureFlatSpec"
  )

  val wordSpecBases: List[String] = List(
    "org.scalatest.WordSpec",
    "org.scalatest.WordSpecLike",
    "org.scalatest.fixture.WordSpec",
    "org.scalatest.fixture.WordSpecLike",
    "org.scalatest.fixture.FixtureWordSpec",
    "org.scalatest.fixture.MultipleFixtureWordSpec"
  )

  val suitePaths: List[String] = List("org.scalatest.Suite")
}
