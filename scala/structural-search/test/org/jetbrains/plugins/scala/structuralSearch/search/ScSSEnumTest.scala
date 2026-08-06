package org.jetbrains.plugins.scala.structuralSearch.search

import org.jetbrains.plugins.scala.structuralSearch.ScalaStructuralSearchTestCase

class ScSSEnumTest extends ScalaStructuralSearchTestCase {

  def testCaseMatching(): Unit = {
    val content =
      """<match="AA">enum Illumination(val radius: Int) {
        |  case Lightbulb extends Illumination(2)
        |  case Neonlight extends Illumination(10)
        |  case Sun extends Illumination(999999999)
        |}</match="AA">
        |
        |<match="AB">enum Illumination2(val radius: Int) {
        |  case Lightbulb extends Illumination2(2)
        |}</match="AB">
        |
        |<match="AC">enum Illumination3() {
        |  case Lightbulb, Neonlight, Sun
        |}</match="AC">
        |"""
    matchAndAssert(
      "Empty matches all",
      content, "enum $test$ {}"
    )
    matchAndAssert(
      "Match one",
      clearMarker(content, Set("AA", "AC")),
      """enum $name$ {
        | case Neonlight
        |}
        |"""
    )
    matchAndAssert(
      "Match multiple out of order with comma",
      clearMarker(content, Set("AA", "AC")),
      """enum $name$ {
        | case Neonlight, Lightbulb
        |}
        |"""
    )
    matchAndAssert(
      "Match multiple out of order listed",
      clearMarker(content, Set("AA", "AC")),
      """enum $name$ {
        | case Neonlight
        | case Lightbulb
        |}
        |"""
    )

    matchAndAssert(
      "Match constructors match",
      clearMarker(content, Set("AA")),
      """enum $name$ {
        | case Neonlight extends Illumination(10)
        | case Lightbulb
        |}
        |"""
    )

    matchAndAssert(
      "Match with vars",
      clearMarker(content, Set("AA")),
      """enum $name$ {
        | case $a$ extends $b$(10)
        | case Lightbulb
        |}
        |"""
    )
    matchAndAssert(
      "Match with vars with count",
      clearMarker(content, Set("AA")),
      """enum $name$ {
        | case Lightbulb
        | case $a$ extends Illumination($b$)
        |}
        |""",
      mO => {
        val constr = mO.addNewVariableConstraint("a")
        constr.setMinCount(2)
        constr.setMaxCount(2)
      }
    )
  }
}
