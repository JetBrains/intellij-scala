package org.jetbrains.plugins.scala.testingSupport.test.scalatest

/**
 * @author Roman.Shein
 * @since 10.04.2015.
 */
object ScalaTestUtil {
  def getFunSuiteBases: List[String] = List("org.scalatest.FunSuite",
    "org.scalatest.FunSuiteLike",
    "org.scalatest.fixture.FunSuite",
    "org.scalatest.fixture.FunSuiteLike",
    "org.scalatest.fixture.FixtureFunSuite",
    "org.scalatest.fixture.MultipleFixtureFunSuite")

  def getFeatureSpecBases: List[String] = List("org.scalatest.FeatureSpec",
    "org.scalatest.FeatureSpecLike",
    "org.scalatest.fixture.FeatureSpec",
    "org.scalatest.fixture.FeatureSpecLike",
    "org.scalatest.fixture.FixtureFeatureSpec",
    "org.scalatest.fixture.MultipleFixtureFeatureSpec")

  def getFreeSpecBases: List[String] = List("org.scalatest.FreeSpec",
    "org.scalatest.FreeSpecLike",
    "org.scalatest.fixture.FreeSpec",
    "org.scalatest.fixture.FreeSpecLike",
    "org.scalatest.fixture.FixtureFreeSpec",
    "org.scalatest.fixture.MultipleFixtureFreeSpec",
    "org.scalatest.path.FreeSpec",
    "org.scalatest.path.FreeSpecLike")

  def getJUnit3SuiteBases: List[String] = List("org.scalatest.junit.JUnit3Suite")

  def getJUnitSuiteBases: List[String] = List("org.scalatest.junit.JUnitSuite", "org.scalatest.junit.JUnitSuiteLike")

  def getPropSpecBases: List[String] = List("org.scalatest.PropSpec",
    "org.scalatest.PropSpecLike",
    "org.scalatest.fixture.PropSpec",
    "org.scalatest.fixture.PropSpecLike",
    "org.scalatest.fixture.FixturePropSpec",
    "org.scalatest.fixture.MultipleFixturePropSpec")

  def getFunSpecBasesPre2_0: List[String] = List("org.scalatest.Spec",
    "org.scalatest.SpecLike",
    "org.scalatest.fixture.Spec",
    "org.scalatest.fixture.SpecLike",
    "org.scalatest.fixture.FixtureSpec",
    "org.scalatest.fixture.MultipleFixtureSpec")

  def getFunSpecBasesPost2_0: List[String] = List("org.scalatest.FunSpec",
    "org.scalatest.FunSpecLike",
    "org.scalatest.fixture.FunSpec",
    "org.scalatest.fixture.FunSpecLike",
    "org.scalatest.path.FunSpec",
    "org.scalatest.path.FunSpecLike")

  def getTestNGSuiteBases: List[String] = List("org.scalatest.testng.TestNGSuite", "org.scalatest.testng.TestNGSuiteLike")

  def getFlatSpecBases: List[String] = List("org.scalatest.FlatSpec",
    "org.scalatest.FlatSpecLike",
    "org.scalatest.fixture.FlatSpec",
    "org.scalatest.fixture.FlatSpecLike",
    "org.scalatest.fixture.FixtureFlatSpec",
    "org.scalatest.fixture.MultipleFixtureFlatSpec")

  def getWordSpecBases: List[String] = List("org.scalatest.WordSpec",
    "org.scalatest.WordSpecLike",
    "org.scalatest.fixture.WordSpec",
    "org.scalatest.fixture.WordSpecLike",
    "org.scalatest.fixture.FixtureWordSpec",
    "org.scalatest.fixture.MultipleFixtureWordSpec")
}
