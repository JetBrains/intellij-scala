package org.jetbrains.plugins.scala.refactoring.nameSuggester

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createTypeElementFromText
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.ScalaNameSuggestionProvider
import org.jetbrains.plugins.scala.project.ScalaFeatures
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.Assert.{assertEquals, assertNotNull}
import org.junit.runner.RunWith

import scala.jdk.CollectionConverters._

abstract class NameSuggesterTest extends AbstractNameSuggesterTest {

  def testStandard(): Unit = {
    testNamesByType("Boolean", "bool")
    testNamesByType("Char", "c")
    testNamesByType("Byte", "b")
    testNamesByType("Short", "sh")
    testNamesByType("Int", "i")
    testNamesByType("Long", "l")
    testNamesByType("Float", "fl")
    testNamesByType("Double", "d")
    testNamesByType("Unit", "unit")
    testNamesByType("String", "str")
  }

  def testArray(): Unit = {
    testNamesByType("Array[String]", "strings", "array")
    testNamesByType("Array[Int]", "ints", "array")
    testNamesByType("Array[Object]", "objects", "array")
  }

  def testCollections(): Unit = {
    testNamesByType("Seq[String]", "strings", "seq")
    testNamesByType("Seq[Int]", "ints", "seq")
    testNamesByType("scala.collection.immutable.Vector[String]", "strings", "vector")
    testNamesByType("scala.collection.SeqLike[String]", "strings", "like", "seqLike")
  }

  // Views were simplified in 2.13: https://docs.scala-lang.org/overviews/core/collections-migration-213.html
  protected def doTestViews(): Unit
  def testViews(): Unit = doTestViews()

  def testCollectionsOfCollections(): Unit = {
    testNamesByType("List[List[String]]", "list")
    testNamesByType("Set[List[Object]]", "set")
  }

  // Views were simplified in 2.13: https://docs.scala-lang.org/overviews/core/collections-migration-213.html
  protected def doTestViewsOfCollections(): Unit
  def testViewsOfCollections(): Unit = doTestViewsOfCollections()

  def testJavaCollections(): Unit = {
    testNamesByType("java.util.List[String]", "strings", "list")
    testNamesByType("java.util.ArrayList[String]", "strings", "list", "arrayList")
    testNamesByType("java.lang.Iterable[String]", "strings", "iterable")
  }

  def testMaps(): Unit = {
    testNamesByType("java.util.Map[String, Object]", "stringToObject", "map")
    testNamesByType("java.util.HashMap[String, Object]", "stringToObject", "map", "hashMap")
    testNamesByType("java.util.HashMap[String, Object]", "stringToObject", "map", "hashMap")
    testNamesByType("scala.collection.mutable.Map[String, Int]", "stringToInt", "map")
    testNamesByType("Map[String, Int]", "stringToInt", "map")
    testNamesByType("scala.collection.mutable.HashMap[String, Int]", "stringToInt", "map", "hashMap")
  }

  def testTuple(): Unit = {
    testNamesByType("(String, Int)", "tuple")
    testNamesByType("(String, Int, Int)", "tuple")
  }

  def testFunction(): Unit = {
    testNamesByType("() => String", "str", "function")
    testNamesByType("(Int) => (String)", "intToString", "function")
    testNamesByType("(Int, Int) => (String)", "function")
  }

  def testSpecial(): Unit = {
    testNamesByType("Option[String]", "maybeString", "option")
    testNamesByType("Some[String]", "someString", "some")
    testNamesByType("scala.concurrent.Future[String]", "eventualString", "future")
    testNamesByType("scala.concurrent.Promise[String]", "promisedString", "promise")
    testNamesByType("scala.util.Try[String]", "triedString")
    testNamesByType("scala.util.Either[Int, String]", "intOrString", "either")
  }

  protected def testNamesByType(typeElementText: String, expected: String*): Unit = {
    val typeElement = createTypeElementFromText(typeElementText, ScalaFeatures.onlyByVersion(version))(getProject)
    testNamesByElement(typeElement, expected)
  }
}

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_11,
  TestScalaVersion.Scala_2_12,
))
class NameSuggesterTest_Before_2_13 extends NameSuggesterTest {
  override protected def doTestViews(): Unit = {
    testNamesByType("scala.collection.IterableView[String]", "strings", "view", "iterableView")
    testNamesByType("scala.collection.SeqView[String]", "strings", "view", "seqView")
  }

  override protected def doTestViewsOfCollections(): Unit = {
    testNamesByType("scala.collection.IterableView[Set[List[Int]]]", "view", "iterableView")
    testNamesByType("scala.collection.SeqView[Set[List[Int]]]", "view", "seqView")
  }
}

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest,
))
class NameSuggesterTest_After_2_13 extends NameSuggesterTest {
  override protected def doTestViews(): Unit = {
    testNamesByType("scala.collection.View[String]", "strings", "view")
    testNamesByType("scala.collection.SeqView[String]", "strings", "view", "seqView")
    testNamesByType("scala.collection.MapView[String, Int]", "stringToInt", "view", "mapView")
  }

  override protected def doTestViewsOfCollections(): Unit = {
    testNamesByType("scala.collection.View[Set[List[Int]]]", "view")
    testNamesByType("scala.collection.SeqView[Set[List[Int]]]", "view", "seqView")
    testNamesByType("scala.collection.MapView[Set[List[Int]], Vector[Set[String]]]", "setToVector", "view", "mapView")
  }
}

abstract class AbstractNameSuggesterTest extends ScalaLightCodeInsightFixtureTestCase {

  protected def testNamesByElement(element: PsiElement, expected: Seq[String]): Unit = {
    assertNotNull(element)

    val actual = new java.util.LinkedHashSet[String]
    new ScalaNameSuggestionProvider().getSuggestedNames(element, getFile, actual)

    assertEquals(expected, actual.asScala.toSeq)
  }
}
