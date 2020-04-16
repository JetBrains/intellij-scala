package org.jetbrains.plugins.scala.lang.parser

class Scala3ExprParserTest extends SimpleScala3ParserTestBase {

  def test_if_then_else(): Unit = checkTree(
    """
      |if a then b else c
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  IfStatement
      |    PsiElement(if)('if')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: a
      |      PsiElement(identifier)('a')
      |    PsiWhiteSpace(' ')
      |    PsiElement(then)('then')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: b
      |      PsiElement(identifier)('b')
      |    PsiWhiteSpace(' ')
      |    PsiElement(else)('else')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: c
      |      PsiElement(identifier)('c')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_if_indented(): Unit = checkTree(
    """
      |if a
      |  b
      |  c
      |else
      |  d
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  IfStatement
      |    PsiElement(if)('if')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: a
      |      PsiElement(identifier)('a')
      |    PsiWhiteSpace('\n  ')
      |    BlockOfExpressions
      |      ReferenceExpression: b
      |        PsiElement(identifier)('b')
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: c
      |        PsiElement(identifier)('c')
      |    PsiWhiteSpace('\n')
      |    PsiElement(else)('else')
      |    PsiWhiteSpace('\n  ')
      |    ReferenceExpression: d
      |      PsiElement(identifier)('d')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_if_then_indented(): Unit = checkTree(
    """
      |if a then
      |  b
      |  c
      |else
      |  d
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  IfStatement
      |    PsiElement(if)('if')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: a
      |      PsiElement(identifier)('a')
      |    PsiWhiteSpace(' ')
      |    PsiElement(then)('then')
      |    PsiWhiteSpace('\n  ')
      |    BlockOfExpressions
      |      ReferenceExpression: b
      |        PsiElement(identifier)('b')
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: c
      |        PsiElement(identifier)('c')
      |    PsiWhiteSpace('\n')
      |    PsiElement(else)('else')
      |    PsiWhiteSpace('\n  ')
      |    ReferenceExpression: d
      |      PsiElement(identifier)('d')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )
}
