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

  def test_old_if(): Unit = checkTree(
    """
      |if (a) {
      |  b
      |} else {
      |  c
      |}
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  IfStatement
      |    PsiElement(if)('if')
      |    PsiWhiteSpace(' ')
      |    PsiElement(()('(')
      |    ReferenceExpression: a
      |      PsiElement(identifier)('a')
      |    PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    BlockExpression
      |      PsiElement({)('{')
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: b
      |        PsiElement(identifier)('b')
      |      PsiWhiteSpace('\n')
      |      PsiElement(})('}')
      |    PsiWhiteSpace(' ')
      |    PsiElement(else)('else')
      |    PsiWhiteSpace(' ')
      |    BlockExpression
      |      PsiElement({)('{')
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: c
      |        PsiElement(identifier)('c')
      |      PsiWhiteSpace('\n')
      |      PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_while_without_do_intended(): Unit = checkTree(
    """
      |while (a)
      |  b
      |  c
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  WhileStatement
      |    PsiElement(while)('while')
      |    PsiWhiteSpace(' ')
      |    PsiElement(()('(')
      |    ReferenceExpression: a
      |      PsiElement(identifier)('a')
      |    PsiElement())(')')
      |    PsiWhiteSpace('\n  ')
      |    BlockOfExpressions
      |      ReferenceExpression: b
      |        PsiElement(identifier)('b')
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: c
      |        PsiElement(identifier)('c')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_while_do_intended(): Unit = checkTree(
    """
      |while
      |  a
      |  b
      |do
      |  c
      |  d
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  WhileStatement
      |    PsiElement(while)('while')
      |    PsiWhiteSpace('\n  ')
      |    BlockOfExpressions
      |      ReferenceExpression: a
      |        PsiElement(identifier)('a')
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: b
      |        PsiElement(identifier)('b')
      |    PsiWhiteSpace('\n')
      |    PsiElement(do)('do')
      |    PsiWhiteSpace('\n  ')
      |    BlockOfExpressions
      |      ReferenceExpression: c
      |        PsiElement(identifier)('c')
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: d
      |        PsiElement(identifier)('d')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_while_do_one_line(): Unit = checkTree(
    """
      |while a do b
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  WhileStatement
      |    PsiElement(while)('while')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: a
      |      PsiElement(identifier)('a')
      |    PsiWhiteSpace(' ')
      |    PsiElement(do)('do')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: b
      |      PsiElement(identifier)('b')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_while_without_do(): Unit = checkTree(
    """
      |while a
      |  b
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  WhileStatement
      |    PsiElement(while)('while')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: a
      |      PsiElement(identifier)('a')
      |    PsiErrorElement:expected 'do'
      |      <empty list>
      |    PsiWhiteSpace('\n  ')
      |    ReferenceExpression: b
      |      PsiElement(identifier)('b')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_while_parenthesis_do(): Unit = checkTree(
    """
      |while (a) do b
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  WhileStatement
      |    PsiElement(while)('while')
      |    PsiWhiteSpace(' ')
      |    ExpressionInParenthesis
      |      PsiElement(()('(')
      |      ReferenceExpression: a
      |        PsiElement(identifier)('a')
      |      PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    PsiElement(do)('do')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: b
      |      PsiElement(identifier)('b')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_old_while(): Unit = checkTree(
    """
      |while (a) {
      |  b
      |}
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  WhileStatement
      |    PsiElement(while)('while')
      |    PsiWhiteSpace(' ')
      |    PsiElement(()('(')
      |    ReferenceExpression: a
      |      PsiElement(identifier)('a')
      |    PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    BlockExpression
      |      PsiElement({)('{')
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: b
      |        PsiElement(identifier)('b')
      |      PsiWhiteSpace('\n')
      |      PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )
}
