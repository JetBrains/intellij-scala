package org.jetbrains.plugins.scala.structuralSearch.search

import org.jetbrains.plugins.scala.structuralSearch.ScalaStructuralSearchTestCase

class ScSSTypeDefTest extends ScalaStructuralSearchTestCase {


  def testTypeDefType(): Unit = {
    val content =
      """<match="AA">class Test1 {}</match="AA">
        |<match="AB">trait Test2 {}</match="AB">
        |<match="AC">enum Test2 {}</match="AC">
        |<match="AD">object Test4 {}</match="AD">
        |"""
    matchAndAssert(
      "Match typedef class",
      clearMarker(content, Set("AA")), "class $test$ {}"
    )
    matchAndAssert(
      "Match typedef trait",
      clearMarker(content, Set("AB")), "trait $test$ {}"
    )
    matchAndAssert(
      "Match typedef enum",
      clearMarker(content, Set("AC")), "enum $test$ {}"
    )
    matchAndAssert(
      "Match typedef object",
      clearMarker(content, Set("AD")), "object $test$ {}"
    )
  }

  def testMatchTypeDefModifier(): Unit = {
    val content =
      """<match="AA">final class Test1 {}</match="AA">
        |<match="AB">abstract class Test2 {}</match="AB">
        |<match="AC">sealed abstract class Tree</match="AC">
        |<match="AD">case class Oak() {}</match="AD">
        |<match="AE">case class Birch() {}</match="AE">
        |<match="AF">case class Jungle() {}</match="AF">
        |<match="AG">case object Spruce {}</match="AG">
        |<match="AH">private class Test3 {}</match="AH">
        |<match="AI">protected class Test4 {}</match="AI">
        |"""
    matchAndAssert(
      "Match modifier final",
      clearMarker(content, Set("AA")), "final class $test$ {}"
    )
    matchAndAssert(
      "Match modifier abstract",
      clearMarker(content, Set("AB", "AC")), "abstract class $test$ {}"
    )
    matchAndAssert(
      "Match modifier sealed",
      clearMarker(content, Set("AC")), "sealed class $test$ {}"
    )
    matchAndAssert(
      "Match modifier sealed abstract",
      clearMarker(content, Set("AC")), "sealed abstract class $test$ {}"
    )
    matchAndAssert(
      "Match modifier sealed abstract",
      clearMarker(content, Set("AD", "AE", "AF")), "case class $test$ {}"
    )
    matchAndAssert(
      "Match modifier sealed abstract",
      clearMarker(content, Set("AG")), "case object $test$ {}"
    )
    matchAndAssert(
      "Match modifier private",
      clearMarker(content, Set("AH")), "private class $test$ {}"
    )
    matchAndAssert(
      "Match modifier protected",
      clearMarker(content, Set("AI")), "protected class $test$ {}"
    )
  }

  def testMatchFunctions(): Unit = {
    val content =
      """<match="AA">class ClassA {
        |  def func(a: Int, b: Int): Int = a + b
        |}</match="AA">
        |<match="AB">class ClassB(var c: String) {
        |  private def func(a: String, b: Int): Unit = {
        |    println(a)
        |    println(b)
        |  }
        |  def this() = { this("Test") }
        |  def funcs(): Unit = {  }
        |  def func(a: String): Unit = { println(a) }
        |  def func2(a: String)(b: Int): Unit = { }
        |}</match="AB">
        |<match="AC">class ClassC {
        |  def func(a: String): Unit = { println(a) }
        |}</match="AC">
        |"""
    matchAndAssert(
      "No function matches all",
      content, "class $name$ {}"
    )
    matchAndAssert(
      "Match one function 1",
      clearMarker(content, Set("AA", "AB")),
      """class $name$ {
        | def $fname$(a, b)
        |}
        |"""
    )
    matchAndAssert(
      "Match one function 2",
      clearMarker(content, Set("AB", "AC")),
      """class $name$ {
        | def $fname$(a: String): Unit
        |}
        |"""
    )
    matchAndAssert(
      "Match out of order",
      clearMarker(content, Set("AB")),
      """class $name$ {
        | def func2(a: String)(b: Int): Unit
        | def func(a: String): Unit
        |}
        |"""
    )
    matchAndAssert(
      "Match exact function count",
      clearMarker(content, Set("AB")),
      """class $name$ {
        |  def $func$($par$)
        |}
        |""",
      matchOptions => {
        val fC = matchOptions.addNewVariableConstraint("func")
        fC.setMinCount(5)
        fC.setMaxCount(5)
        val pC = matchOptions.addNewVariableConstraint("par")
        pC.setMinCount(0)
        pC.setMaxCount(10)
      }
    )
  }

  def testMatchPrimaryConstructor(): Unit = {
    val content =
      """<match="AA">class A(a: Int, b: String) {}</match="AA">
        |<match="AB">class B(a: Int) {}</match="AB">
        |<match="AC">class C {}</match="AC">
        |"""
    matchAndAssert(
      "Empty matches all",
      content, "class $name$ {}"
    )
    matchAndAssert(
      "Match parameter with type",
      clearMarker(content, Set("AA")), "class $name$(a: Int, b: String) {}"
    )
    matchAndAssert(
      "Match only parameter name",
      clearMarker(content, Set("AB")), "class $name$(a) {}"
    )
    matchAndAssert(
      "Match with variable",
      clearMarker(content, Set("AB")), "class $name$($p$: $ty$) {}"
    )
    matchAndAssert(
      "Match with variable with count",
      clearMarker(content, Set("AA", "AB")), "class $name$($p$: $ty$) {}",
      _.addNewVariableConstraint("p").setMaxCount(10)
    )
  }

  def testMatchAnnotations(): Unit = {
    val content =
      """<match="AA">@Annot1 class A {}</match="AA">
        |<match="AB">@Annot2 class B {}</match="AB">
        |<match="AC">@Annot1 @Annot2 class C {}</match="AC">
        |<match="AD">class C {}</match="AD">
        |"""
    matchAndAssert(
      "Empty matches all",
      content, "class $name$ {}"
    )
    matchAndAssert(
      "Match annotation 1",
      clearMarker(content, Set("AA", "AC")), "@Annot1 class $name$ {}"
    )
    matchAndAssert(
      "Match annotation 2",
      clearMarker(content, Set("AB", "AC")), "@Annot2 class $name$ {}"
    )
    matchAndAssert(
      "Match both",
      clearMarker(content, Set("AC")), "@Annot1 @Annot2 class $name$ {}"
    )
    matchAndAssert(
      "Match with variable",
      clearMarker(content, Set("AA", "AB", "AC")), "@$anno$ class $name$ {}"
    )
    matchAndAssert(
      "Match with variable with count",
      content, "@$anno$ class $name$ {}",
      matchOpt => {
        val constr = matchOpt.addNewVariableConstraint("anno")
        constr.setMinCount(0)
        constr.setMaxCount(10)
      }
    )
  }

  // TODO extends types
}
