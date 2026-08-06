package org.jetbrains.plugins.scala.lang.psi.uast.kinds

import org.jetbrains.uast.UastSpecialExpressionKind

object ScalaSpecialExpressionKinds {

  val Match: UastSpecialExpressionKind =
    new UastSpecialExpressionKind("match")

  val CaseClause: UastSpecialExpressionKind =
    new UastSpecialExpressionKind("case_clause")

  val ImportsList: UastSpecialExpressionKind =
    new UastSpecialExpressionKind("imports_list")

  val EmptyList: UastSpecialExpressionKind =
    new UastSpecialExpressionKind("empty_list")
}
