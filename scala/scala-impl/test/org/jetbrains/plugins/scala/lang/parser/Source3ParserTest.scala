package org.jetbrains.plugins.scala.lang.parser

import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestAdapter, SharedTestProjectToken}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration

class Source3ParserTest extends ScalaLightCodeInsightFixtureTestAdapter with ScalaParserTestOps {
  override def parseText(text: String): ScalaFile = {
    val fixture = getFixture
    fixture.configureByText("foo.scala", text)
    fixture.getFile.asInstanceOf[ScalaFile]
  }

  override def setUp(): Unit = {
    super.setUp()
    val defaultProfile = ScalaCompilerConfiguration.instanceIn(getProject).defaultProfile
    val newSettings    = defaultProfile.getSettings.copy(
      additionalCompilerOptions = Seq("-Xsource:3")
    )
    defaultProfile.setSettings(newSettings)
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
}
