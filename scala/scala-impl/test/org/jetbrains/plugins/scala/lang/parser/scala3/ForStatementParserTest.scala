package org.jetbrains.plugins.scala.lang.parser.scala3

class ForStatementParserTest extends SimpleScala3ParserTestBase {

  def test_for(): Unit = checkTree(
    """for
      |  (x, y) <- foo
      |yield x + y
      |""".stripMargin,
    """ScalaFile
      |  ForStatement
      |    PsiElement(for)('for')
      |    PsiWhiteSpace('\n  ')
      |    Enumerators
      |      Generator
      |        TuplePattern
      |          PsiElement(()('(')
      |          ArgumentPatterns
      |            ReferencePattern: x
      |              PsiElement(identifier)('x')
      |            PsiElement(,)(',')
      |            PsiWhiteSpace(' ')
      |            ReferencePattern: y
      |              PsiElement(identifier)('y')
      |          PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<-)('<-')
      |        PsiWhiteSpace(' ')
      |        ReferenceExpression: foo
      |          PsiElement(identifier)('foo')
      |    PsiWhiteSpace('\n')
      |    PsiElement(yield)('yield')
      |    PsiWhiteSpace(' ')
      |    InfixExpression
      |      ReferenceExpression: x
      |        PsiElement(identifier)('x')
      |      PsiWhiteSpace(' ')
      |      ReferenceExpression: +
      |        PsiElement(identifier)('+')
      |      PsiWhiteSpace(' ')
      |      ReferenceExpression: y
      |        PsiElement(identifier)('y')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_for_multiple_generators(): Unit = checkTree(
    """for
      |  (x, y) <- foo
      |  (z, _) <- bar
      |yield x + z
      |""".stripMargin,
    """ScalaFile
      |  ForStatement
      |    PsiElement(for)('for')
      |    PsiWhiteSpace('\n  ')
      |    Enumerators
      |      Generator
      |        TuplePattern
      |          PsiElement(()('(')
      |          ArgumentPatterns
      |            ReferencePattern: x
      |              PsiElement(identifier)('x')
      |            PsiElement(,)(',')
      |            PsiWhiteSpace(' ')
      |            ReferencePattern: y
      |              PsiElement(identifier)('y')
      |          PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<-)('<-')
      |        PsiWhiteSpace(' ')
      |        ReferenceExpression: foo
      |          PsiElement(identifier)('foo')
      |      PsiWhiteSpace('\n  ')
      |      Generator
      |        TuplePattern
      |          PsiElement(()('(')
      |          ArgumentPatterns
      |            ReferencePattern: z
      |              PsiElement(identifier)('z')
      |            PsiElement(,)(',')
      |            PsiWhiteSpace(' ')
      |            WildcardPattern
      |              PsiElement(_)('_')
      |          PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<-)('<-')
      |        PsiWhiteSpace(' ')
      |        ReferenceExpression: bar
      |          PsiElement(identifier)('bar')
      |    PsiWhiteSpace('\n')
      |    PsiElement(yield)('yield')
      |    PsiWhiteSpace(' ')
      |    InfixExpression
      |      ReferenceExpression: x
      |        PsiElement(identifier)('x')
      |      PsiWhiteSpace(' ')
      |      ReferenceExpression: +
      |        PsiElement(identifier)('+')
      |      PsiWhiteSpace(' ')
      |      ReferenceExpression: z
      |        PsiElement(identifier)('z')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_for_multiple_generators_with_case(): Unit = checkTree(
    """for
      |  case (x, y) <- foo
      |  (z, _) <- bar
      |yield x + z
      |""".stripMargin,
    """ScalaFile
      |  ForStatement
      |    PsiElement(for)('for')
      |    PsiWhiteSpace('\n  ')
      |    Enumerators
      |      Generator
      |        PsiElement(case)('case')
      |        PsiWhiteSpace(' ')
      |        TuplePattern
      |          PsiElement(()('(')
      |          ArgumentPatterns
      |            ReferencePattern: x
      |              PsiElement(identifier)('x')
      |            PsiElement(,)(',')
      |            PsiWhiteSpace(' ')
      |            ReferencePattern: y
      |              PsiElement(identifier)('y')
      |          PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<-)('<-')
      |        PsiWhiteSpace(' ')
      |        ReferenceExpression: foo
      |          PsiElement(identifier)('foo')
      |      PsiWhiteSpace('\n  ')
      |      Generator
      |        TuplePattern
      |          PsiElement(()('(')
      |          ArgumentPatterns
      |            ReferencePattern: z
      |              PsiElement(identifier)('z')
      |            PsiElement(,)(',')
      |            PsiWhiteSpace(' ')
      |            WildcardPattern
      |              PsiElement(_)('_')
      |          PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<-)('<-')
      |        PsiWhiteSpace(' ')
      |        ReferenceExpression: bar
      |          PsiElement(identifier)('bar')
      |    PsiWhiteSpace('\n')
      |    PsiElement(yield)('yield')
      |    PsiWhiteSpace(' ')
      |    InfixExpression
      |      ReferenceExpression: x
      |        PsiElement(identifier)('x')
      |      PsiWhiteSpace(' ')
      |      ReferenceExpression: +
      |        PsiElement(identifier)('+')
      |      PsiWhiteSpace(' ')
      |      ReferenceExpression: z
      |        PsiElement(identifier)('z')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_for_in_value_definition(): Unit = checkTree(
    """val something = (for
      |  (x, y) <- foo
      |yield x + y)
      |""".stripMargin,
    """ScalaFile
      |  ScPatternDefinition: something
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: something
      |        PsiElement(identifier)('something')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ExpressionInParenthesis
      |      PsiElement(()('(')
      |      ForStatement
      |        PsiElement(for)('for')
      |        PsiWhiteSpace('\n  ')
      |        Enumerators
      |          Generator
      |            TuplePattern
      |              PsiElement(()('(')
      |              ArgumentPatterns
      |                ReferencePattern: x
      |                  PsiElement(identifier)('x')
      |                PsiElement(,)(',')
      |                PsiWhiteSpace(' ')
      |                ReferencePattern: y
      |                  PsiElement(identifier)('y')
      |              PsiElement())(')')
      |            PsiWhiteSpace(' ')
      |            PsiElement(<-)('<-')
      |            PsiWhiteSpace(' ')
      |            ReferenceExpression: foo
      |              PsiElement(identifier)('foo')
      |        PsiWhiteSpace('\n')
      |        PsiElement(yield)('yield')
      |        PsiWhiteSpace(' ')
      |        InfixExpression
      |          ReferenceExpression: x
      |            PsiElement(identifier)('x')
      |          PsiWhiteSpace(' ')
      |          ReferenceExpression: +
      |            PsiElement(identifier)('+')
      |          PsiWhiteSpace(' ')
      |          ReferenceExpression: y
      |            PsiElement(identifier)('y')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_for_in_value_definition_multiple_generators(): Unit = checkTree(
    """val something = (for
      |  (x, y) <- foo
      |  (z, _) <- bar
      |yield x + z)
      |""".stripMargin,
    """ScalaFile
      |  ScPatternDefinition: something
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: something
      |        PsiElement(identifier)('something')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ExpressionInParenthesis
      |      PsiElement(()('(')
      |      ForStatement
      |        PsiElement(for)('for')
      |        PsiWhiteSpace('\n  ')
      |        Enumerators
      |          Generator
      |            TuplePattern
      |              PsiElement(()('(')
      |              ArgumentPatterns
      |                ReferencePattern: x
      |                  PsiElement(identifier)('x')
      |                PsiElement(,)(',')
      |                PsiWhiteSpace(' ')
      |                ReferencePattern: y
      |                  PsiElement(identifier)('y')
      |              PsiElement())(')')
      |            PsiWhiteSpace(' ')
      |            PsiElement(<-)('<-')
      |            PsiWhiteSpace(' ')
      |            ReferenceExpression: foo
      |              PsiElement(identifier)('foo')
      |          PsiWhiteSpace('\n  ')
      |          Generator
      |            TuplePattern
      |              PsiElement(()('(')
      |              ArgumentPatterns
      |                ReferencePattern: z
      |                  PsiElement(identifier)('z')
      |                PsiElement(,)(',')
      |                PsiWhiteSpace(' ')
      |                WildcardPattern
      |                  PsiElement(_)('_')
      |              PsiElement())(')')
      |            PsiWhiteSpace(' ')
      |            PsiElement(<-)('<-')
      |            PsiWhiteSpace(' ')
      |            ReferenceExpression: bar
      |              PsiElement(identifier)('bar')
      |        PsiWhiteSpace('\n')
      |        PsiElement(yield)('yield')
      |        PsiWhiteSpace(' ')
      |        InfixExpression
      |          ReferenceExpression: x
      |            PsiElement(identifier)('x')
      |          PsiWhiteSpace(' ')
      |          ReferenceExpression: +
      |            PsiElement(identifier)('+')
      |          PsiWhiteSpace(' ')
      |          ReferenceExpression: z
      |            PsiElement(identifier)('z')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_for_in_value_definition_multiple_generators_with_case(): Unit = checkTree(
    """val something = (for
      |  case (x, y) <- foo
      |  (z, _) <- bar
      |yield x + z)
      |""".stripMargin,
    """ScalaFile
      |  ScPatternDefinition: something
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: something
      |        PsiElement(identifier)('something')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ExpressionInParenthesis
      |      PsiElement(()('(')
      |      ForStatement
      |        PsiElement(for)('for')
      |        PsiWhiteSpace('\n  ')
      |        Enumerators
      |          Generator
      |            PsiElement(case)('case')
      |            PsiWhiteSpace(' ')
      |            TuplePattern
      |              PsiElement(()('(')
      |              ArgumentPatterns
      |                ReferencePattern: x
      |                  PsiElement(identifier)('x')
      |                PsiElement(,)(',')
      |                PsiWhiteSpace(' ')
      |                ReferencePattern: y
      |                  PsiElement(identifier)('y')
      |              PsiElement())(')')
      |            PsiWhiteSpace(' ')
      |            PsiElement(<-)('<-')
      |            PsiWhiteSpace(' ')
      |            ReferenceExpression: foo
      |              PsiElement(identifier)('foo')
      |          PsiWhiteSpace('\n  ')
      |          Generator
      |            TuplePattern
      |              PsiElement(()('(')
      |              ArgumentPatterns
      |                ReferencePattern: z
      |                  PsiElement(identifier)('z')
      |                PsiElement(,)(',')
      |                PsiWhiteSpace(' ')
      |                WildcardPattern
      |                  PsiElement(_)('_')
      |              PsiElement())(')')
      |            PsiWhiteSpace(' ')
      |            PsiElement(<-)('<-')
      |            PsiWhiteSpace(' ')
      |            ReferenceExpression: bar
      |              PsiElement(identifier)('bar')
      |        PsiWhiteSpace('\n')
      |        PsiElement(yield)('yield')
      |        PsiWhiteSpace(' ')
      |        InfixExpression
      |          ReferenceExpression: x
      |            PsiElement(identifier)('x')
      |          PsiWhiteSpace(' ')
      |          ReferenceExpression: +
      |            PsiElement(identifier)('+')
      |          PsiWhiteSpace(' ')
      |          ReferenceExpression: z
      |            PsiElement(identifier)('z')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_for_with_tuples_SCL_21081(): Unit = checkTree(
    """for {
      |  x <- Seq(1, 2, 3)
      |  t1 = (1, 2)
      |  (a, b) = (3, 4)
      |  t2 = (1, 2)
      |  (c, d) = (3, 4)
      |} {}""".stripMargin,
    """ScalaFile
      |  ForStatement
      |    PsiElement(for)('for')
      |    PsiWhiteSpace(' ')
      |    PsiElement({)('{')
      |    PsiWhiteSpace('\n  ')
      |    Enumerators
      |      Generator
      |        ReferencePattern: x
      |          PsiElement(identifier)('x')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<-)('<-')
      |        PsiWhiteSpace(' ')
      |        MethodCall
      |          ReferenceExpression: Seq
      |            PsiElement(identifier)('Seq')
      |          ArgumentList
      |            PsiElement(()('(')
      |            IntegerLiteral
      |              PsiElement(integer)('1')
      |            PsiElement(,)(',')
      |            PsiWhiteSpace(' ')
      |            IntegerLiteral
      |              PsiElement(integer)('2')
      |            PsiElement(,)(',')
      |            PsiWhiteSpace(' ')
      |            IntegerLiteral
      |              PsiElement(integer)('3')
      |            PsiElement())(')')
      |      PsiWhiteSpace('\n  ')
      |      ForBinding
      |        ReferencePattern: t1
      |          PsiElement(identifier)('t1')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=)('=')
      |        PsiWhiteSpace(' ')
      |        Tuple
      |          PsiElement(()('(')
      |          IntegerLiteral
      |            PsiElement(integer)('1')
      |          PsiElement(,)(',')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('2')
      |          PsiElement())(')')
      |      PsiWhiteSpace('\n  ')
      |      ForBinding
      |        TuplePattern
      |          PsiElement(()('(')
      |          ArgumentPatterns
      |            ReferencePattern: a
      |              PsiElement(identifier)('a')
      |            PsiElement(,)(',')
      |            PsiWhiteSpace(' ')
      |            ReferencePattern: b
      |              PsiElement(identifier)('b')
      |          PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=)('=')
      |        PsiWhiteSpace(' ')
      |        Tuple
      |          PsiElement(()('(')
      |          IntegerLiteral
      |            PsiElement(integer)('3')
      |          PsiElement(,)(',')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('4')
      |          PsiElement())(')')
      |      PsiWhiteSpace('\n  ')
      |      ForBinding
      |        ReferencePattern: t2
      |          PsiElement(identifier)('t2')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=)('=')
      |        PsiWhiteSpace(' ')
      |        Tuple
      |          PsiElement(()('(')
      |          IntegerLiteral
      |            PsiElement(integer)('1')
      |          PsiElement(,)(',')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('2')
      |          PsiElement())(')')
      |      PsiWhiteSpace('\n  ')
      |      ForBinding
      |        TuplePattern
      |          PsiElement(()('(')
      |          ArgumentPatterns
      |            ReferencePattern: c
      |              PsiElement(identifier)('c')
      |            PsiElement(,)(',')
      |            PsiWhiteSpace(' ')
      |            ReferencePattern: d
      |              PsiElement(identifier)('d')
      |          PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=)('=')
      |        PsiWhiteSpace(' ')
      |        Tuple
      |          PsiElement(()('(')
      |          IntegerLiteral
      |            PsiElement(integer)('3')
      |          PsiElement(,)(',')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('4')
      |          PsiElement())(')')
      |    PsiWhiteSpace('\n')
      |    PsiElement(})('}')
      |    PsiWhiteSpace(' ')
      |    BlockExpression
      |      PsiElement({)('{')
      |      PsiElement(})('}')
      |""".stripMargin
  )

}
