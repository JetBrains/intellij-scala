package org.jetbrains.plugins.scala.lang.parser.scala3

class ExprParserTest extends SimpleScala3ParserTestBase {

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
      |    BlockExpression
      |      PsiWhiteSpace('\n  ')
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
      |    BlockExpression
      |      PsiWhiteSpace('\n  ')
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

  def test_if_then_else_SCL_18769a(): Unit = checkTree(
    """
      |if a then
      |  b
      |  else
      |  c
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
      |    ReferenceExpression: b
      |      PsiElement(identifier)('b')
      |    PsiWhiteSpace('\n  ')
      |    PsiElement(else)('else')
      |    PsiWhiteSpace('\n  ')
      |    ReferenceExpression: c
      |      PsiElement(identifier)('c')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_if_then_else_SCL_18769b(): Unit = checkTree(
    """
      |if a
      |then
      |  b
      |  c
      |  else
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
      |    PsiWhiteSpace('\n')
      |    PsiElement(then)('then')
      |    BlockExpression
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: b
      |        PsiElement(identifier)('b')
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: c
      |        PsiElement(identifier)('c')
      |    PsiWhiteSpace('\n  ')
      |    PsiElement(else)('else')
      |    PsiWhiteSpace('\n  ')
      |    ReferenceExpression: d
      |      PsiElement(identifier)('d')
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
      |    BlockExpression
      |      PsiWhiteSpace('\n  ')
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
      |    BlockExpression
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: a
      |        PsiElement(identifier)('a')
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: b
      |        PsiElement(identifier)('b')
      |    PsiWhiteSpace('\n')
      |    PsiElement(do)('do')
      |    BlockExpression
      |      PsiWhiteSpace('\n  ')
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

  def test_match_after_dot(): Unit = checkTree(
    """
      |x.y.match
      |  case _ => ???
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  MatchStatement
      |    ReferenceExpression: x.y
      |      ReferenceExpression: x
      |        PsiElement(identifier)('x')
      |      PsiElement(.)('.')
      |      PsiElement(identifier)('y')
      |    PsiElement(.)('.')
      |    PsiElement(match)('match')
      |    PsiWhiteSpace('\n  ')
      |    CaseClauses
      |      CaseClause
      |        PsiElement(case)('case')
      |        PsiWhiteSpace(' ')
      |        WildcardPattern
      |          PsiElement(_)('_')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        PsiWhiteSpace(' ')
      |        BlockOfExpressions
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )
}
