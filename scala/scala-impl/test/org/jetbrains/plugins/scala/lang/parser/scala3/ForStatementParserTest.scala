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

}
