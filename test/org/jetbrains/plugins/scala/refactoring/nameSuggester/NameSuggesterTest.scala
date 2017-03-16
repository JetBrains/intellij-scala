package org.jetbrains.plugins.scala
package refactoring.nameSuggester

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createTypeElementFromText
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester.suggestNamesByType
import org.junit.Assert.assertArrayEquals

/**
  * Nikolay.Tropin
  * 2014-07-01
  */
class NameSuggesterTest extends ScalaLightCodeInsightFixtureTestAdapter {

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
    testNamesByType("scala.collection.IterableView[String]", "strings", "view", "iterableView")
  }

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

  private def testNamesByType(typeElementText: String, expected: String*): Unit = {
    implicit val manager = myFixture.getPsiManager
    val `type` = createTypeElementFromText(typeElementText)
      .getType().getOrNothing

    val actual = suggestNamesByType(`type`).toArray[AnyRef]
    assertArrayEquals(expected.toArray[AnyRef], actual)
  }
}
