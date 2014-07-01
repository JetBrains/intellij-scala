package org.jetbrains.plugins.scala
package refactoring.nameSuggester

import junit.framework.Assert
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester

/**
 * Nikolay.Tropin
 * 2014-07-01
 */
class
NameSuggesterTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testNamesByType(typeElementText: String, names: Seq[String]) {
    val typeElement = ScalaPsiElementFactory.createTypeElementFromText(typeElementText, myFixture.getPsiManager)
    val scType = typeElement.getType().getOrNothing
    Assert.assertEquals(names.mkString(", "), NameSuggester.suggestNamesByType(scType).mkString(", "))
  }

  def testArray() {
    testNamesByType("Array[String]", Seq("strings", "array"))
    testNamesByType("Array[Int]", Seq("ints", "array"))
    testNamesByType("Array[Object]", Seq("objects", "array"))
  }

  def testCollections() {
    testNamesByType("Seq[String]", Seq("strings", "seq"))
    testNamesByType("Seq[Int]", Seq("ints", "seq"))
    testNamesByType("scala.collection.immutable.Vector[String]", Seq("strings", "vector"))
    testNamesByType("scala.collection.SeqLike[String]", Seq("strings", "like", "seqLike"))
    testNamesByType("scala.collection.IterableView[String]", Seq("strings", "view", "iterableView"))
  }

  def testJavaCollections() {
    testNamesByType("java.util.List[String]", Seq("strings", "list"))
    testNamesByType("java.util.ArrayList[String]", Seq("strings", "list", "arrayList"))
    testNamesByType("java.lang.Iterable[String]", Seq("strings", "iterable"))
  }

  def testMaps() {
    testNamesByType("java.util.Map[String, Object]", Seq("stringToObject", "map"))
    testNamesByType("java.util.HashMap[String, Object]", Seq("stringToObject", "map", "hashMap"))
    testNamesByType("java.util.HashMap[String, Object]", Seq("stringToObject", "map", "hashMap"))
    testNamesByType("scala.collection.mutable.Map[String, Int]", Seq("stringToInt", "map"))
    testNamesByType("Map[String, Int]", Seq("stringToInt", "map"))
    testNamesByType("scala.collection.mutable.HashMap[String, Int]", Seq("stringToInt", "map", "hashMap"))
  }

  def testTuple() {
    testNamesByType("(String, Int)", Seq("tuple"))
    testNamesByType("(String, Int, Int)", Seq("tuple"))
  }

  def testFunction() {
    testNamesByType("() => String", Seq("s", "function"))
    testNamesByType("(Int) => (String)", Seq("intToString", "function"))
    testNamesByType("(Int, Int) => (String)", Seq("function"))
  }

  def testSpecial() {
    testNamesByType("Option[String]", Seq("maybeString", "option"))
    testNamesByType("Some[String]", Seq("someString", "some"))
    testNamesByType("scala.concurrent.Future[String]", Seq("eventualString", "future"))
    testNamesByType("scala.concurrent.Promise[String]", Seq("promisedString", "promise"))
    testNamesByType("scala.util.Try[String]", Seq("triedString"))
    testNamesByType("scala.util.Either[Int, String]", Seq("intOrString", "either"))
  }


}
