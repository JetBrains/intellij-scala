package org.jetbrains.plugins.scala.structuralSearch.search

import org.jetbrains.plugins.scala.structuralSearch.ScalaStructuralSearchTestCase

class ScSSFunctionsTest extends ScalaStructuralSearchTestCase {

  def testBasicFunction(): Unit = {
    val content =
      """<match="AA">def test(): Unit = {
        |  a
        |}</match="AA">
        |"""
    val pattern =
      """def test(): Unit = {
        |  a
        |}
        |"""
    matchAndAssert(
      "Basic function",
      content, pattern
    )
  }

  def testDeclarationMatchesBody(): Unit = {
    val content =
      """<match="AA">def test(): Unit = {
        |  a
        |}</match="AA">
        |"""
    val pattern =
      """def $test$()
        |"""
    matchAndAssert(
      "Test that declaration matches definitions",
      content, pattern
    )
  }

  def testReturnTypeMatch(): Unit = {
    val content =
      """<match="AA">def test1(): Unit = {
        |  a
        |}</match="AA">
        |<match="AB">def test2(): Int = {
        |  a
        |}</match="AB">
        |<match="AC">def test3(): String</match="AC">
        |"""
    val pattern =
      """def $test$()
        |"""
    val patternUnit =
      """def $test$(): Unit
        |"""
    val patternInt =
      """def $test$(): Int
        |"""
    val patternString =
      """def $test$(): String
        |"""
    val patternVar =
      """def $test$(): $ty$
        |"""
    matchAndAssert(
      "No return type matches all return types",
      content, pattern
    )
    matchAndAssert(
      "Return type is checked (Unit)",
      clearMarker(content, Set("AA")), patternUnit
    )
    matchAndAssert(
      "Return type is checked (Int)",
      clearMarker(content, Set("AB")), patternInt
    )
    matchAndAssert(
      "Return type is checked (String)",
      clearMarker(content, Set("AC")), patternString
    )
    matchAndAssert(
      "Return type is checked (Var)",
      content, patternVar
    )
  }

  def testModifierMatch(): Unit = {
    val content =
      """<match="AA">private def test1() = {
        |  a
        |}</match="AA">
        |<match="AB">protected def test2() = {
        |  a
        |}</match="AB">
        |<match="AC">abstract def test3()</match="AC">
        |<match="AD">def test4()</match="AD">
        |"""
    val pattern =
      """def $test$()
        |"""
    val patternPriv =
      """private def $test$()
        |"""
    val patternProt =
      """protected def $test$()
        |"""
    val patternAbs =
      """abstract def $test$()
        |"""
    matchAndAssert(
      "No modifier matches all",
      content, pattern
    )
    matchAndAssert(
      "Modifier is checked (private)",
      content, pattern
    )
    matchAndAssert(
      "Modifier is checked (private)",
      clearMarker(content, Set("AA")), patternPriv
    )
    matchAndAssert(
      "Modifier is checked (protected)",
      clearMarker(content, Set("AB")), patternProt
    )
    matchAndAssert(
      "Modifier is checked (abstract)",
      clearMarker(content, Set("AC")), patternAbs
    )
  }

  def testParametersMatch(): Unit = {
    // TODO
  }

  def testTypeParametersMatch(): Unit = {
    // TODO
  }
}
