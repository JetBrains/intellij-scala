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
  }

  val contentPar =
    """<match="AA">def test1(a: Int, b: Int)</match="AA">
      |<match="AB">def test2(a: String, b: String)</match="AB">
      |<match="AC">def test3(a: Int, b: String)</match="AC">
      |<match="AD">def test4(a: Int, b: String, c: Int)</match="AD">
      |<match="AE">def test4()</match="AE">
      |"""
  def testParametersMatch_pure(): Unit = {
    matchAndAssert(
      "Match Pure 1",
      clearMarker(contentPar, Set("AA")), "def $test$(a: Int, b: Int)"
      )
    matchAndAssert(
      "Match Pure 2",
      clearMarker(contentPar, Set("AB")), "def $test$(a: String, b: String)"
    )
    matchAndAssert(
      "Match Pure 3",
      clearMarker(contentPar, Set("AD")), "def $test$(a: Int, b: String, c: Int)"
    )
    matchAndAssert(
      "Match Pure 4",
      clearMarker(contentPar, Set("AE")), "def $test$()"
    )
  }

  def testParametersMatch_onlyName(): Unit = {
    matchAndAssert(
      "Match Only Name 1",
      clearMarker(contentPar, Set("AA", "AB", "AC")), "def $test$(a, b)"
    )
    matchAndAssert(
      "Match Only Name 1",
      clearMarker(contentPar, Set("AA", "AC")), "def $test$(a: Int, b)"
    )
    matchAndAssert(
      "Match Only Name 3",
      clearMarker(contentPar, Set("AD")), "def $test$(a, b: String, c: Int)"
    )
  }

  def testParametersMatch_vars(): Unit = {
    matchAndAssert(
      "Match Only Name 1",
      clearMarker(contentPar, Set("AB")), "def $test$($a$: String)",
      _.addNewVariableConstraint("a").setMaxCount(10)
    )
    matchAndAssert(
      "Match Only Name 1",
      clearMarker(contentPar, Set("AA", "AB", "AC", "AD")), "def $test$($a$)",
      _.addNewVariableConstraint("a").setMaxCount(10)
    )
    matchAndAssert(
      "Match Only Name 1",
      contentPar, "def $test$($a$)",
      matchOptions =>
        val constraints = matchOptions.addNewVariableConstraint("a")
        constraints.setMinCount(0)
        constraints.setMaxCount(10)
    )
  }

  def testParametersMatch_Implicit(): Unit = {
    val content =
      """<match="AA">def test1(a: Int)</match="AA">
        |<match="AB">def test2(implicit a: Int)</match="AB">
        |"""

    matchAndAssert(
      "Match Implicit",
      content,
      "def $test$(a: Int)"
    )

    matchAndAssert(
      "Match Implicit",
      clearMarker(content, Set("AB")),
      "def $test$(implicit a: Int)"
    )
  }

  // TODO wait for fix of type parameters and look for a new pattern
  def testTypeParametersMatch(): Unit = {
    val content =
      """<match="AA">def test1[T](): Unit</match="AA">
        |<match="AB">def test2[T, R](): Unit</match="AB">
        |<match="AC">def test3(): Unit</match="AC">
        |<match="AD">def test4[E](): Unit</match="AD">
        |"""

    matchAndAssert(
      "Match no type matches all",
      content, "def $test$()",
    )
    matchAndAssert(
      "Match specific 1",
      clearMarker(content, Set("AA", "AB")), "def $test$[T]()",
    )
    matchAndAssert(
      "Match specific 2",
      clearMarker(content, Set("AB")), "def $test$[T, R]()",
    )
    matchAndAssert(
      "Match Var 1",
      clearMarker(content, Set("AB")), "def $test$[T, $A$]()",
    )
    matchAndAssert(
      "Match Var 2",
      clearMarker(content, Set("AA", "AB", "AD")), "def $test$[$A$]()",
      _.addNewVariableConstraint("A").setMaxCount(10)
    )
    matchAndAssert(
      "Match Var 3",
      content, "def $test$[$A$]()",
      matchOptions =>
        val constraints = matchOptions.addNewVariableConstraint("A")
        constraints.setMinCount(0)
        constraints.setMaxCount(10)
    )
  }

  def testMatchFunctionAnnotations(): Unit = {
    val content =
      """<match="AA">@Annot1 def funcA()</match="AA">
        |<match="AB">@Annot2 def funcB()</match="AB">
        |<match="AC">@Annot1 @Annot2 def funcC()</match="AC">
        |<match="AD">def funcD()</match="AD">
        |"""
    matchAndAssert(
      "Empty matches all",
      content, "def $name$()"
    )
    matchAndAssert(
      "Match annotation 1",
      clearMarker(content, Set("AA", "AC")), "@Annot1 def $name$()"
    )
    matchAndAssert(
      "Match annotation 2",
      clearMarker(content, Set("AB", "AC")), "@Annot2 def $name$()"
    )
    matchAndAssert(
      "Match both",
      clearMarker(content, Set("AC")), "@Annot1 @Annot2 def $name$()"
    )
    matchAndAssert(
      "Match with variable",
      clearMarker(content, Set("AA", "AB", "AC")), "@$anno$ def $name$()"
    )
    matchAndAssert(
      "Match with variable with count",
      content, "@$anno$ def $name$()",
      matchOpt => {
        val constr = matchOpt.addNewVariableConstraint("anno")
        constr.setMinCount(0)
        constr.setMaxCount(10)
      }
    )
  }

  def testMatchParameterAnnotations(): Unit = {
    val content =
      """<match="AA">def funcA(@Annot1 par: Int)</match="AA">
        |<match="AB">def funcB(@Annot2 par: Int)</match="AB">
        |<match="AC">def funcC(@Annot1 @Annot2 par: Int)</match="AC">
        |<match="AD">def funcD(par: Int)</match="AD">
        |"""
    matchAndAssert(
      "Empty matches all",
      content, "def $name$($p$)"
    )
    matchAndAssert(
      "Match annotation 1",
      clearMarker(content, Set("AA", "AC")), "def $name$(@Annot1 $p$)"
    )
    matchAndAssert(
      "Match annotation 2",
      clearMarker(content, Set("AB", "AC")), "def $name$(@Annot2 $p$)"
    )
    matchAndAssert(
      "Match both",
      clearMarker(content, Set("AC")), "def $name$(@Annot1 @Annot2 $p$)"
    )
    matchAndAssert(
      "Match with variable",
      clearMarker(content, Set("AA", "AB", "AC")), "def $name$(@$anno$ $p$)"
    )
    matchAndAssert(
      "Match with variable with count",
      content, "def $name$(@$anno$ $p$)",
      matchOpt => {
        val constr = matchOpt.addNewVariableConstraint("anno")
        constr.setMinCount(0)
        constr.setMaxCount(10)
      }
    )
  }

  val contentParMul =
    """<match="AA">def test1(a: Int)(b: Int)</match="AA">
      |<match="AB">def test2(a: String)(b: String)</match="AB">
      |<match="AC">def test3(a: Int)(b: String)</match="AC">
      |<match="AD">def test4(a: Int)(b: String, c: Int)</match="AD">
      |<match="AE">def test4()</match="AE">
      |"""
  def testMatchParameterMultipleClausesSing(): Unit = {
    matchAndAssert(
      "Match Pure 1",
      clearMarker(contentParMul, Set("AA")), "def $test$(a: Int, b: Int)"
    )
    matchAndAssert(
      "Match Pure 2",
      clearMarker(contentParMul, Set("AB")), "def $test$(a: String, b: String)"
    )
    matchAndAssert(
      "Match Pure 3",
      clearMarker(contentParMul, Set("AD")), "def $test$(a: Int, b: String, c: Int)"
    )
    matchAndAssert(
      "Match Pure 4",
      clearMarker(contentParMul, Set("AE")), "def $test$()"
    )

    matchAndAssert(
      "Match Only Name 1",
      clearMarker(contentParMul, Set("AA", "AB", "AC")), "def $test$(a, b)"
    )
    matchAndAssert(
      "Match Only Name 1",
      clearMarker(contentParMul, Set("AA", "AC")), "def $test$(a: Int, b)"
    )
    matchAndAssert(
      "Match Only Name 3",
      clearMarker(contentParMul, Set("AD")), "def $test$(a, b: String, c: Int)"
    )
  }

  def testMatchParameterMultipleClauseMul(): Unit = {
    matchAndAssert(
      "Match Pure 1",
      clearMarker(contentParMul, Set("AA")), "def $test$(a: Int)(b: Int)"
    )
    matchAndAssert(
      "Match Pure 2",
      clearMarker(contentParMul, Set("AB")), "def $test$(a: String)(b: String)"
    )
    matchAndAssert(
      "Match Pure 3",
      clearMarker(contentParMul, Set("AD")), "def $test$(a: Int)(b: String, c: Int)"
    )
    matchAndAssert(
      "Match Pure 4",
      clearMarker(contentParMul, Set("AE")), "def $test$()"
    )

    matchAndAssert(
      "Match Only Name 1",
      clearMarker(contentParMul, Set("AA", "AB", "AC")), "def $test$(a)(b)"
    )
    matchAndAssert(
      "Match Only Name 1",
      clearMarker(contentParMul, Set("AA", "AC")), "def $test$(a: Int)(b)"
    )
    matchAndAssert(
      "Match Only Name 3",
      clearMarker(contentParMul, Set("AD")), "def $test$(a)(b: String, c: Int)"
    )
  }

  def testMatchParameterMultipleClausesMulAnti(): Unit = {
    matchAndAssert(
      "Match Pure 1",
      clearMarker(contentParMul), "def $test$(a: Int, b: Int)()"
    )
    matchAndAssert(
      "Match Pure 2",
      clearMarker(contentParMul), "def $test$()(a: String, b: String)"
    )
    matchAndAssert(
      "Match Pure 3",
      clearMarker(contentParMul), "def $test$(a: Int, b: String)(c: Int)"
    )

    matchAndAssert(
      "Match Only Name 1",
      clearMarker(contentParMul), "def $test$(a, b)()"
    )
    matchAndAssert(
      "Match Only Name 1",
      clearMarker(contentParMul), "def $test$()(a: Int, b)"
    )
    matchAndAssert(
      "Match Only Name 3",
      clearMarker(contentParMul), "def $test$(a)(b: String)(c: Int)"
    )
  }

  def testMatchParameterDefaults(): Unit = {
    val content =
      """<match="AA">def test1(a: Int)</match="AA">
        |<match="AB">def test2(a: Int = 3)</match="AB">
        |<match="AC">def test2(a: Int = 4)</match="AC">
        |"""
    matchAndAssert(
      "No match all",
      content, "def $test$(a: Int)"
    )
    matchAndAssert(
      "Match correct 1",
      clearMarker(content, Set("AB")), "def $test$(a: Int = 3)"
    )
    matchAndAssert(
      "Match correct 2",
      clearMarker(content, Set("AC")), "def $test$(a: Int = 4)"
    )
  }
}
