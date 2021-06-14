package org.jetbrains.plugins.scala.lang.parser.scala3

class ExtensionMethodsParserTest extends SimpleScala3ParserTestBase {
  def test_parameterless_with_period(): Unit = checkTree(
    """
      |def (c: Circle).circumference: Double = 0.0
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScFunctionDefinition: circumference
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    ParametersClause
      |      PsiElement(()('(')
      |      Parameter: c
      |        AnnotationsList
      |          <empty list>
      |        Modifiers
      |          <empty list>
      |        PsiElement(identifier)('c')
      |        PsiElement(:)(':')
      |        PsiWhiteSpace(' ')
      |        ParameterType
      |          SimpleType: Circle
      |            CodeReferenceElement: Circle
      |              PsiElement(identifier)('Circle')
      |      PsiElement())(')')
      |    PsiElement(.)('.')
      |    PsiElement(identifier)('circumference')
      |    Parameters
      |      <empty list>
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    SimpleType: Double
      |      CodeReferenceElement: Double
      |        PsiElement(identifier)('Double')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    DoubleLiteral
      |      PsiElement(double)('0.0')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_parameterless_without_period(): Unit = checkTree(
    """
      |def (c: Circle) circumference: Double = 0.0
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScFunctionDefinition: circumference
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    ParametersClause
      |      PsiElement(()('(')
      |      Parameter: c
      |        AnnotationsList
      |          <empty list>
      |        Modifiers
      |          <empty list>
      |        PsiElement(identifier)('c')
      |        PsiElement(:)(':')
      |        PsiWhiteSpace(' ')
      |        ParameterType
      |          SimpleType: Circle
      |            CodeReferenceElement: Circle
      |              PsiElement(identifier)('Circle')
      |      PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('circumference')
      |    Parameters
      |      <empty list>
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    SimpleType: Double
      |      CodeReferenceElement: Double
      |        PsiElement(identifier)('Double')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    DoubleLiteral
      |      PsiElement(double)('0.0')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_parameters(): Unit = checkTree(
    """
      |def [T <: AnyRef](base: T) circumference(some: T)(using Int): Double = 0.0
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScFunctionDefinition: circumference
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<:)('<:')
      |        PsiWhiteSpace(' ')
      |        SimpleType: AnyRef
      |          CodeReferenceElement: AnyRef
      |            PsiElement(identifier)('AnyRef')
      |      PsiElement(])(']')
      |    ParametersClause
      |      PsiElement(()('(')
      |      Parameter: base
      |        AnnotationsList
      |          <empty list>
      |        Modifiers
      |          <empty list>
      |        PsiElement(identifier)('base')
      |        PsiElement(:)(':')
      |        PsiWhiteSpace(' ')
      |        ParameterType
      |          SimpleType: T
      |            CodeReferenceElement: T
      |              PsiElement(identifier)('T')
      |      PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('circumference')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: some
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(identifier)('some')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          ParameterType
      |            SimpleType: T
      |              CodeReferenceElement: T
      |                PsiElement(identifier)('T')
      |        PsiElement())(')')
      |      ParametersClause
      |        PsiElement(()('(')
      |        PsiElement(using)('using')
      |        PsiWhiteSpace(' ')
      |        Parameter: _
      |          ParameterType
      |            SimpleType: Int
      |              CodeReferenceElement: Int
      |                PsiElement(identifier)('Int')
      |        PsiElement())(')')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    SimpleType: Double
      |      CodeReferenceElement: Double
      |        PsiElement(identifier)('Double')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    DoubleLiteral
      |      PsiElement(double)('0.0')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )
}
