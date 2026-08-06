package org.jetbrains.plugins.scala.structuralSearch.search

import org.jetbrains.plugins.scala.structuralSearch.ScalaStructuralSearchTestCase

class ScSSTryExprTest extends ScalaStructuralSearchTestCase {

  def testTry(): Unit = {
    val content =
      """<match="AA">try {
        | contentA
        |} catch {
        | case exc: E => doSomething(exc)
        |}</match="AA">
        |<match="AB">try {
        | contentB
        |} finally {
        | doSomethingElse()
        |}</match="AB">
        |<match="AC">try {
        | contentC
        |} catch {
        | case exc: E => doSomething(exc)
        |} finally {
        | doSomethingElse()
        |}</match="AC">
        |<match="AD">try {
        | contentA
        |} catch {
        | case exca: Ea => doSomethingA(exca)
        | case excb: Eb => doSomethingB(excb)
        | case excc: Ec => doSomethingC(excc)
        |}</match="AD">
        |"""
    matchAndAssert(
      "Empty matches all",
      content,
      """try {
        |  $cont$
        |}
        |"""
    )
    matchAndAssert(
      "Match catch",
      clearMarker(content, Set("AA", "AC")),
      """try {
        |  $cont$
        |} catch {
        | case exc: E => doSomething(exc)
        |}
        |"""
    )
    matchAndAssert(
      "Match catch any order",
      clearMarker(content, Set("AD")),
      """try {
        |  $cont$
        |} catch {
        | case excc: Ec => doSomethingC(excc)
        | case exca: Ea => doSomethingA(exca)
        | case excb: Eb => doSomethingB(excb)
        |}
        |"""
    )
    matchAndAssert(
      "Match finally",
      clearMarker(content, Set("AB", "AC")),
      """try {
        |  $cont$
        |} finally {
        | doSomethingElse()
        |}
        |"""
    )
    matchAndAssert(
      "Match both",
      clearMarker(content, Set("AC")),
      """try {
        |  $cont$
        |} catch {
        | case exc: E => doSomething(exc)
        |} finally {
        | doSomethingElse()
        |}
        |"""
    )

    matchAndAssert(
      "Catch variables work",
      clearMarker(content, Set("AA", "AC", "AD")),
      """try {
        |  $cont$
        |} catch {
        | case $a$: $b$ => $c$
        |}
        |"""
    )
    matchAndAssert(
      "Catch variables work with count",
      clearMarker(content, Set("AD")),
      """try {
        |  $cont$
        |} catch {
        | case $a$: $b$ => $c$
        |}
        |""",
      mO => {
        val constr = mO.addNewVariableConstraint("a")
        constr.setMinCount(2)
        constr.setMaxCount(10)
      }
    )
  }
}
