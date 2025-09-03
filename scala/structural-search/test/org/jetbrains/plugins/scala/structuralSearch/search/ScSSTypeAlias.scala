package org.jetbrains.plugins.scala.structuralSearch.search

import org.jetbrains.plugins.scala.structuralSearch.ScalaStructuralSearchTestCase

class ScSSTypeAlias extends ScalaStructuralSearchTestCase {

  def testTypeAlias(): Unit = {
    val content =
      """<match="AE"><match="AA">type IntSeq = Seq[Int]</match="AA">
        |<match="AB">type StringSeq = Seq[String]</match="AB">
        |<match="AC">type StringList = List[String]</match="AC"></match="AE">
        |<match="AD">type PureDecl</match="AD">
        |"""
    matchAndAssert(
      "Empty matches all",
      clearMarker(content, Set("AA", "AB", "AC", "AD")), "type $name$"
    )
    matchAndAssert(
      "Match one type",
      clearMarker(content, Set("AA", "AB")),
      "type $name$ = Seq"
    )
    matchAndAssert(
      "Match one type with parameter",
      clearMarker(content, Set("AA")),
      "type $name$ = Seq[Int]"
    )
    matchAndAssert(
      "Match type with var parameter",
      clearMarker(content, Set("AA", "AB")),
      "type $name$ = Seq[$a$]"
    )
    matchAndAssert(
      "Match two vars",
      clearMarker(content, Set("AA", "AB", "AC")),
      "type $name$ = $b$[$a$]"
    )
    matchAndAssert(
      "Match vars with count",
      clearMarker(content, Set("AE")),
      "type $name$ = $b$[$a$]",
      mO => {
        val constr = mO.addNewVariableConstraint("name")
        constr.setMinCount(3)
        constr.setMaxCount(3)
      }
    )
  }
}
