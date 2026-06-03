package org.jetbrains.plugins.scala.lang.parser.scala3

class NestedTryCatchFinallyOutdentTest extends SimpleScala3ParserTestBase {

  def test_outdented_finally_belongs_to_outer_try(): Unit = checkTree(
    """
      |try
      |  try
      |    1
      |finally
      |  2
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  TryStatement
      |    PsiElement(try)('try')
      |    PsiWhiteSpace('\n  ')
      |    TryStatement
      |      PsiElement(try)('try')
      |      PsiWhiteSpace('\n    ')
      |      IntegerLiteral
      |        PsiElement(integer)('1')
      |    PsiWhiteSpace('\n')
      |    FinallyBlock
      |      PsiElement(finally)('finally')
      |      PsiWhiteSpace('\n  ')
      |      IntegerLiteral
      |        PsiElement(integer)('2')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_outdented_catch_belongs_to_outer_try(): Unit = checkTree(
    """
      |try
      |  try
      |    1
      |catch
      |  case _ => 2
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  TryStatement
      |    PsiElement(try)('try')
      |    PsiWhiteSpace('\n  ')
      |    TryStatement
      |      PsiElement(try)('try')
      |      PsiWhiteSpace('\n    ')
      |      IntegerLiteral
      |        PsiElement(integer)('1')
      |    PsiWhiteSpace('\n')
      |    CatchBlock
      |      PsiElement(catch)('catch')
      |      PsiWhiteSpace('\n  ')
      |      CaseClauses
      |        CaseClause
      |          PsiElement(case)('case')
      |          PsiWhiteSpace(' ')
      |          WildcardPattern
      |            PsiElement(_)('_')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=>)('=>')
      |          PsiWhiteSpace(' ')
      |          BlockOfExpressions
      |            IntegerLiteral
      |              PsiElement(integer)('2')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_outdented_finally_after_inner_catch_belongs_to_outer_try(): Unit = checkTree(
    """
      |try
      |  try
      |    1
      |  catch
      |    case _ => 2
      |finally
      |  3
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  TryStatement
      |    PsiElement(try)('try')
      |    PsiWhiteSpace('\n  ')
      |    TryStatement
      |      PsiElement(try)('try')
      |      PsiWhiteSpace('\n    ')
      |      IntegerLiteral
      |        PsiElement(integer)('1')
      |      PsiWhiteSpace('\n  ')
      |      CatchBlock
      |        PsiElement(catch)('catch')
      |        PsiWhiteSpace('\n    ')
      |        CaseClauses
      |          CaseClause
      |            PsiElement(case)('case')
      |            PsiWhiteSpace(' ')
      |            WildcardPattern
      |              PsiElement(_)('_')
      |            PsiWhiteSpace(' ')
      |            PsiElement(=>)('=>')
      |            PsiWhiteSpace(' ')
      |            BlockOfExpressions
      |              IntegerLiteral
      |                PsiElement(integer)('2')
      |    PsiWhiteSpace('\n')
      |    FinallyBlock
      |      PsiElement(finally)('finally')
      |      PsiWhiteSpace('\n  ')
      |      IntegerLiteral
      |        PsiElement(integer)('3')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )
}
