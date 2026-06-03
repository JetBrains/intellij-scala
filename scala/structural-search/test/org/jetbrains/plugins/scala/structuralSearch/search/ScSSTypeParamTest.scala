package org.jetbrains.plugins.scala.structuralSearch.search

import org.jetbrains.plugins.scala.structuralSearch.ScalaStructuralSearchTestCase

class ScSSTypeParamTest extends ScalaStructuralSearchTestCase {

  def testBasicFunction(): Unit = {
    val content =
      """<match="AA">class test1 {}</match="AA">
        |<match="AB">class test2[T] {}</match="AB">
        |<match="AC">class test3[T, S] {}</match="AC">
        |<match="AD">class test4[T <: S, S] {}</match="AD">
        |<match="AE">class test5[T >: S, S {}</match="AE">
        |<match="AF">class test3[+T, S] {}</match="AF">
        |<match="AG">class test3[-T, S] {}</match="AG">
        |"""

    matchAndAssert(
      "Empty matches all",
      content, "class $name$ {}"
    )
    matchAndAssert(
      "Empty match one 1",
      clearMarker(content, Set("AB", "AC", "AD", "AE", "AF", "AG")), "class $name$[T] {}"
    )
    matchAndAssert(
      "Empty match one 2",
      clearMarker(content, Set("AC", "AD", "AE", "AF", "AG")), "class $name$[S] {}"
    )
    matchAndAssert(
      "Match out of order",
      clearMarker(content, Set("AC", "AD", "AE", "AF", "AG")), "class $name$[S, T] {}"
    )
    matchAndAssert(
      "Match covariant",
      clearMarker(content, Set("AF")), "class $name$[+T] {}"
    )
    matchAndAssert(
      "Match contravariant",
      clearMarker(content, Set("AG")), "class $name$[-T] {}"
    )
    matchAndAssert(
      "Match upper bound",
      clearMarker(content, Set("AD")), "class $name$[T <: $S$] {}"
    )
    matchAndAssert(
      "Match lower bound",
      clearMarker(content, Set("AE")), "class $name$[T >: $S$] {}"
    )
  }
}
