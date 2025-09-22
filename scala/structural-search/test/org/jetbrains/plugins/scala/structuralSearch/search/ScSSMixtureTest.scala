package org.jetbrains.plugins.scala.structuralSearch.search

import org.jetbrains.plugins.scala.structuralSearch.ScalaStructuralSearchTestCase

class ScSSMixtureTest extends ScalaStructuralSearchTestCase {

  def testSimpleOps(): Unit = {
    val content =
      """<match="AA">return 1</match="AA">
        |<match="AB">throw Exception()</match="AB">
        |<match="AC">new Tree()</match="AC">
        |<match="AD">a = b</match="AD">
        |<match="AE">!a</match="AE">
        |<match="AF">type Person = (name: String, age: Int)</match="AF">
        |<match="AG">val fkt = [T, S] => (t: T, s: S) => <match="AH">(name = s, age = t)</match="AH"></match="AG">
        |<match="AI">fkt[T, S]</match="AI">
        |"""
      
    matchAndAssert(
      "Match return",
      clearMarker(content, Set("AA")),
      "return 1"
    )
    matchAndAssert(
      "Match throw",
      clearMarker(content, Set("AB")),
      "throw Exception()"
    )
    matchAndAssert(
      "Match new",
      clearMarker(content, Set("AC")),
      "new Tree()"
    )
    matchAndAssert(
      "Match assignment",
      clearMarker(content, Set("AD")),
      "a = b"
    )
    matchAndAssert(
      "Match prefix",
      clearMarker(content, Set("AE")),
      "!a"
    )
    matchAndAssert(
      "Match named tuple type",
      clearMarker(content, Set("AF")),
      "type Person = (name: String, age: Int)"
    )
    matchAndAssert(
      "Match named tuple",
      clearMarker(content, Set("AH")),
      "(name = s, age = t)"
    )
    matchAndAssert(
      "Match polyfunction and function expression",
      clearMarker(content, Set("AG")),
      "val fkt = [T, S] => (t: T, s: S) => (name = s, age = t)"
    )
    matchAndAssert(
      "Match generic call",
      clearMarker(content, Set("AI")),
      "fkt[T, S]"
    )
  }
}
