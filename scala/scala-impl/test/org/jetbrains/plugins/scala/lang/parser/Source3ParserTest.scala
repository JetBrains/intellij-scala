package org.jetbrains.plugins.scala.lang.parser

import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestAdapter, SharedTestProjectToken}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.util.Source3TestCase

class Source3ParserTest extends ScalaLightCodeInsightFixtureTestAdapter with Source3TestCase with ScalaParserTestOps {
  override def parseText(text: String): ScalaFile = {
    val fixture = getFixture
    fixture.configureByText("foo.scala", text)
    fixture.getFile.asInstanceOf[ScalaFile]
  }

  override protected def sharedProjectToken: SharedTestProjectToken =
    SharedTestProjectToken(this.getClass)

  def test_wildcard_type(): Unit = checkTree(
    """
      |val x: ? <: AnyRef = null
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScPatternDefinition: x
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: x
      |        PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    WildcardType: ? <: AnyRef
      |      PsiElement(?)('?')
      |      PsiWhiteSpace(' ')
      |      PsiElement(<:)('<:')
      |      PsiWhiteSpace(' ')
      |      SimpleType: AnyRef
      |        CodeReferenceElement: AnyRef
      |          PsiElement(identifier)('AnyRef')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    NullLiteral
      |      PsiElement(null)('null')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_imports(): Unit = checkTree(
    """
      |import a as b
      |import a.b
      |import a.*
      |import a._
      |import a.b as c
      |import a.{b as c, d as e}
      |import a.{b => c, d => e}
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScImportStatement
      |    PsiElement(import)('import')
      |    PsiWhiteSpace(' ')
      |    ImportExpression
      |      ImportSelectors
      |        ImportSelector
      |          CodeReferenceElement: a
      |            PsiElement(identifier)('a')
      |          PsiWhiteSpace(' ')
      |          PsiElement(as)('as')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('b')
      |  PsiWhiteSpace('\n')
      |  ScImportStatement
      |    PsiElement(import)('import')
      |    PsiWhiteSpace(' ')
      |    ImportExpression
      |      CodeReferenceElement: a.b
      |        CodeReferenceElement: a
      |          PsiElement(identifier)('a')
      |        PsiElement(.)('.')
      |        PsiElement(identifier)('b')
      |  PsiWhiteSpace('\n')
      |  ScImportStatement
      |    PsiElement(import)('import')
      |    PsiWhiteSpace(' ')
      |    ImportExpression
      |      CodeReferenceElement: a
      |        PsiElement(identifier)('a')
      |      PsiElement(.)('.')
      |      PsiElement(*)('*')
      |  PsiWhiteSpace('\n')
      |  ScImportStatement
      |    PsiElement(import)('import')
      |    PsiWhiteSpace(' ')
      |    ImportExpression
      |      CodeReferenceElement: a
      |        PsiElement(identifier)('a')
      |      PsiElement(.)('.')
      |      PsiElement(_)('_')
      |  PsiWhiteSpace('\n')
      |  ScImportStatement
      |    PsiElement(import)('import')
      |    PsiWhiteSpace(' ')
      |    ImportExpression
      |      CodeReferenceElement: a
      |        PsiElement(identifier)('a')
      |      PsiElement(.)('.')
      |      ImportSelectors
      |        ImportSelector
      |          CodeReferenceElement: b
      |            PsiElement(identifier)('b')
      |          PsiWhiteSpace(' ')
      |          PsiElement(as)('as')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('c')
      |  PsiWhiteSpace('\n')
      |  ScImportStatement
      |    PsiElement(import)('import')
      |    PsiWhiteSpace(' ')
      |    ImportExpression
      |      CodeReferenceElement: a
      |        PsiElement(identifier)('a')
      |      PsiElement(.)('.')
      |      ImportSelectors
      |        PsiElement({)('{')
      |        ImportSelector
      |          CodeReferenceElement: b
      |            PsiElement(identifier)('b')
      |          PsiWhiteSpace(' ')
      |          PsiElement(as)('as')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('c')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        ImportSelector
      |          CodeReferenceElement: d
      |            PsiElement(identifier)('d')
      |          PsiWhiteSpace(' ')
      |          PsiElement(as)('as')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('e')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  ScImportStatement
      |    PsiElement(import)('import')
      |    PsiWhiteSpace(' ')
      |    ImportExpression
      |      CodeReferenceElement: a
      |        PsiElement(identifier)('a')
      |      PsiElement(.)('.')
      |      ImportSelectors
      |        PsiElement({)('{')
      |        ImportSelector
      |          CodeReferenceElement: b
      |            PsiElement(identifier)('b')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=>)('=>')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('c')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        ImportSelector
      |          CodeReferenceElement: d
      |            PsiElement(identifier)('d')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=>)('=>')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('e')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_open_and_infix_soft_keywords(): Unit = checkTree(
    """
      |open infix class Test
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScClass: Test
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      PsiElement(open)('open')
      |      PsiWhiteSpace(' ')
      |      PsiElement(infix)('infix')
      |    PsiWhiteSpace(' ')
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Test')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        <empty list>
      |    ExtendsBlock
      |      <empty list>
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_vararg_slices(): Unit = checkTree(
    """
      |foo(s: _*)
      |foo(s*)
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  MethodCall
      |    ReferenceExpression: foo
      |      PsiElement(identifier)('foo')
      |    ArgumentList
      |      PsiElement(()('(')
      |      TypedStatement
      |        ReferenceExpression: s
      |          PsiElement(identifier)('s')
      |        PsiElement(:)(':')
      |        PsiWhiteSpace(' ')
      |        SequenceArgumentType
      |          PsiElement(_)('_')
      |          PsiElement(identifier)('*')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  MethodCall
      |    ReferenceExpression: foo
      |      PsiElement(identifier)('foo')
      |    ArgumentList
      |      PsiElement(()('(')
      |      TypedStatement
      |        ReferenceExpression: s
      |          PsiElement(identifier)('s')
      |        SequenceArgumentType
      |          PsiElement(identifier)('*')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_seq_wildcard_pattern(): Unit = checkTree(
    """
      |val Seq(_*) = null
      |val Seq(all*) = null
      |val Seq(all@_*) = null
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScPatternDefinition
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ConstructorPattern
      |        CodeReferenceElement: Seq
      |          PsiElement(identifier)('Seq')
      |        Pattern Argument List
      |          PsiElement(()('(')
      |          SequenceWildcardPattern: _
      |            PsiElement(_)('_')
      |            PsiElement(identifier)('*')
      |          PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    NullLiteral
      |      PsiElement(null)('null')
      |  PsiWhiteSpace('\n')
      |  ScPatternDefinition: all
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ConstructorPattern
      |        CodeReferenceElement: Seq
      |          PsiElement(identifier)('Seq')
      |        Pattern Argument List
      |          PsiElement(()('(')
      |          SequenceWildcardPattern: all
      |            PsiElement(identifier)('all')
      |            PsiElement(identifier)('*')
      |          PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    NullLiteral
      |      PsiElement(null)('null')
      |  PsiWhiteSpace('\n')
      |  ScPatternDefinition: all
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ConstructorPattern
      |        CodeReferenceElement: Seq
      |          PsiElement(identifier)('Seq')
      |        Pattern Argument List
      |          PsiElement(()('(')
      |          NamingPattern: all
      |            PsiElement(identifier)('all')
      |            PsiElement(@)('@')
      |            SequenceWildcardPattern: _
      |              PsiElement(_)('_')
      |              PsiElement(identifier)('*')
      |          PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    NullLiteral
      |      PsiElement(null)('null')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_intersection_types(): Unit = checkTree(
    """
      |val x: a & b = null
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScPatternDefinition: x
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: x
      |        PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CompoundType: a & b
      |      SimpleType: a
      |        CodeReferenceElement: a
      |          PsiElement(identifier)('a')
      |      PsiWhiteSpace(' ')
      |      PsiElement(identifier)('&')
      |      PsiWhiteSpace(' ')
      |      SimpleType: b
      |        CodeReferenceElement: b
      |          PsiElement(identifier)('b')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    NullLiteral
      |      PsiElement(null)('null')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_infix_expression(): Unit = checkTree(
    """
      |def f = c
      |  `c c` i
      |
      |def g = i +
      |  `n n`
      |
      |def basic =
      |    1
      |  + 2
      |
      |val x = 1
      |  +
      |  `a`
      |  *
      |  6
      |
      |def f =
      |  x < 0
      |  ||
      |  x > 0
      |  &&
      |  x != 3
      |
      |acc += (i * `i`)
      |`i` += 1
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScFunctionDefinition: f
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('f')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    InfixExpression
      |      ReferenceExpression: c
      |        PsiElement(identifier)('c')
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: `c c`
      |        PsiElement(identifier)('`c c`')
      |      PsiWhiteSpace(' ')
      |      ReferenceExpression: i
      |        PsiElement(identifier)('i')
      |  PsiWhiteSpace('\n\n')
      |  ScFunctionDefinition: g
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('g')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    InfixExpression
      |      ReferenceExpression: i
      |        PsiElement(identifier)('i')
      |      PsiWhiteSpace(' ')
      |      ReferenceExpression: +
      |        PsiElement(identifier)('+')
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: `n n`
      |        PsiElement(identifier)('`n n`')
      |  PsiWhiteSpace('\n\n')
      |  ScFunctionDefinition: basic
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('basic')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace('\n    ')
      |    InfixExpression
      |      IntegerLiteral
      |        PsiElement(integer)('1')
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: +
      |        PsiElement(identifier)('+')
      |      PsiWhiteSpace(' ')
      |      IntegerLiteral
      |        PsiElement(integer)('2')
      |  PsiWhiteSpace('\n\n')
      |  ScPatternDefinition: x
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: x
      |        PsiElement(identifier)('x')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    InfixExpression
      |      IntegerLiteral
      |        PsiElement(integer)('1')
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: +
      |        PsiElement(identifier)('+')
      |      PsiWhiteSpace('\n  ')
      |      InfixExpression
      |        ReferenceExpression: `a`
      |          PsiElement(identifier)('`a`')
      |        PsiWhiteSpace('\n  ')
      |        ReferenceExpression: *
      |          PsiElement(identifier)('*')
      |        PsiWhiteSpace('\n  ')
      |        IntegerLiteral
      |          PsiElement(integer)('6')
      |  PsiWhiteSpace('\n\n')
      |  ScFunctionDefinition: f
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('f')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace('\n  ')
      |    InfixExpression
      |      InfixExpression
      |        ReferenceExpression: x
      |          PsiElement(identifier)('x')
      |        PsiWhiteSpace(' ')
      |        ReferenceExpression: <
      |          PsiElement(identifier)('<')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('0')
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: ||
      |        PsiElement(identifier)('||')
      |      PsiWhiteSpace('\n  ')
      |      InfixExpression
      |        InfixExpression
      |          ReferenceExpression: x
      |            PsiElement(identifier)('x')
      |          PsiWhiteSpace(' ')
      |          ReferenceExpression: >
      |            PsiElement(identifier)('>')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('0')
      |        PsiWhiteSpace('\n  ')
      |        ReferenceExpression: &&
      |          PsiElement(identifier)('&&')
      |        PsiWhiteSpace('\n  ')
      |        InfixExpression
      |          ReferenceExpression: x
      |            PsiElement(identifier)('x')
      |          PsiWhiteSpace(' ')
      |          ReferenceExpression: !=
      |            PsiElement(identifier)('!=')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('3')
      |  PsiWhiteSpace('\n\n')
      |  InfixExpression
      |    ReferenceExpression: acc
      |      PsiElement(identifier)('acc')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: +=
      |      PsiElement(identifier)('+=')
      |    PsiWhiteSpace(' ')
      |    ExpressionInParenthesis
      |      PsiElement(()('(')
      |      InfixExpression
      |        ReferenceExpression: i
      |          PsiElement(identifier)('i')
      |        PsiWhiteSpace(' ')
      |        ReferenceExpression: *
      |          PsiElement(identifier)('*')
      |        PsiWhiteSpace(' ')
      |        ReferenceExpression: `i`
      |          PsiElement(identifier)('`i`')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  InfixExpression
      |    ReferenceExpression: `i`
      |      PsiElement(identifier)('`i`')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: +=
      |      PsiElement(identifier)('+=')
      |    PsiWhiteSpace(' ')
      |    IntegerLiteral
      |      PsiElement(integer)('1')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )
}
