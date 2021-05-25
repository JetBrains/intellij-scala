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
}
