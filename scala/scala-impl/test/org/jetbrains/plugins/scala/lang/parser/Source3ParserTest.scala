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
      |      CodeReferenceElement: a.*
      |        CodeReferenceElement: a
      |          PsiElement(identifier)('a')
      |        PsiElement(.)('.')
      |        PsiElement(identifier)('*')
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
      |      CodeReferenceElement: a.b
      |        CodeReferenceElement: a
      |          PsiElement(identifier)('a')
      |        PsiElement(.)('.')
      |        PsiElement(identifier)('b')
      |      PsiWhiteSpace(' ')
      |      PsiElement(as)('as')
      |      PsiWhiteSpace(' ')
      |      PsiElement(identifier)('c')
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
}
