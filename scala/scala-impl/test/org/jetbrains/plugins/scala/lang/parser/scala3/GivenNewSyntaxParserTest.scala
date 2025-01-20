package org.jetbrains.plugins.scala.lang.parser.scala3

import org.jetbrains.plugins.scala.ScalaVersion

class GivenNewSyntaxParserTest extends SimpleScala3ParserTestBase {
  override protected def scalaVersion: ScalaVersion = ScalaVersion.Latest.Scala_3_6

  def test_given_aliases(): Unit = checkTree(
    """given Ty = ()
      |given A => Ty = ()
      |given [T] => Ty = ()
      |given () => Ty = ()
      |given (A, B) => Ty = ()
      |given (x: Int) => Ty = ()
      |
      |given value: Ty = ()
      |given value: () => Ty = ()
      |given value: A => Ty = ()
      |given value: [T] => Ty = ()
      |given value: (A, B) => Ty = ()
      |given value: (x: Int) => Ty = ()
      |""".stripMargin,
    """
      |ScalaFile
      |  ScGivenAliasDefinition: given_Ty
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    SimpleType: Ty
      |      CodeReferenceElement: Ty
      |        PsiElement(identifier)('Ty')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    UnitExpression
      |      PsiElement(()('(')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  ScGivenAliasDefinition: given_Ty
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        Parameter: <anonymous>
      |          ParameterType
      |            SimpleType: A
      |              CodeReferenceElement: A
      |                PsiElement(identifier)('A')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    SimpleType: Ty
      |      CodeReferenceElement: Ty
      |        PsiElement(identifier)('Ty')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    UnitExpression
      |      PsiElement(()('(')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  ScGivenAliasDefinition: given_Ty
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=>)('=>')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    SimpleType: Ty
      |      CodeReferenceElement: Ty
      |        PsiElement(identifier)('Ty')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    UnitExpression
      |      PsiElement(()('(')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  ScGivenAliasDefinition: given_Ty
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    SimpleType: Ty
      |      CodeReferenceElement: Ty
      |        PsiElement(identifier)('Ty')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    UnitExpression
      |      PsiElement(()('(')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  ScGivenAliasDefinition: given_Ty
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: <anonymous>
      |          ParameterType
      |            SimpleType: A
      |              CodeReferenceElement: A
      |                PsiElement(identifier)('A')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        Parameter: <anonymous>
      |          ParameterType
      |            SimpleType: B
      |              CodeReferenceElement: B
      |                PsiElement(identifier)('B')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    SimpleType: Ty
      |      CodeReferenceElement: Ty
      |        PsiElement(identifier)('Ty')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    UnitExpression
      |      PsiElement(()('(')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  ScGivenAliasDefinition: given_Ty
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: x
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(identifier)('x')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          ParameterType
      |            SimpleType: Int
      |              CodeReferenceElement: Int
      |                PsiElement(identifier)('Int')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    SimpleType: Ty
      |      CodeReferenceElement: Ty
      |        PsiElement(identifier)('Ty')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    UnitExpression
      |      PsiElement(()('(')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n\n')
      |  ScGivenAliasDefinition: value
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('value')
      |    PsiElement(:)(':')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    SimpleType: Ty
      |      CodeReferenceElement: Ty
      |        PsiElement(identifier)('Ty')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    UnitExpression
      |      PsiElement(()('(')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  ScGivenAliasDefinition: value
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('value')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    SimpleType: Ty
      |      CodeReferenceElement: Ty
      |        PsiElement(identifier)('Ty')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    UnitExpression
      |      PsiElement(()('(')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  ScGivenAliasDefinition: value
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('value')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        Parameter: <anonymous>
      |          ParameterType
      |            SimpleType: A
      |              CodeReferenceElement: A
      |                PsiElement(identifier)('A')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    SimpleType: Ty
      |      CodeReferenceElement: Ty
      |        PsiElement(identifier)('Ty')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    UnitExpression
      |      PsiElement(()('(')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  ScGivenAliasDefinition: value
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('value')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=>)('=>')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    SimpleType: Ty
      |      CodeReferenceElement: Ty
      |        PsiElement(identifier)('Ty')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    UnitExpression
      |      PsiElement(()('(')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  ScGivenAliasDefinition: value
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('value')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: <anonymous>
      |          ParameterType
      |            SimpleType: A
      |              CodeReferenceElement: A
      |                PsiElement(identifier)('A')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        Parameter: <anonymous>
      |          ParameterType
      |            SimpleType: B
      |              CodeReferenceElement: B
      |                PsiElement(identifier)('B')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    SimpleType: Ty
      |      CodeReferenceElement: Ty
      |        PsiElement(identifier)('Ty')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    UnitExpression
      |      PsiElement(()('(')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  ScGivenAliasDefinition: value
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('value')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: x
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(identifier)('x')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          ParameterType
      |            SimpleType: Int
      |              CodeReferenceElement: Int
      |                PsiElement(identifier)('Int')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    SimpleType: Ty
      |      CodeReferenceElement: Ty
      |        PsiElement(identifier)('Ty')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    UnitExpression
      |      PsiElement(()('(')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_given_def_braces(): Unit = checkTree(
    """given Ty {}
      |given A => Ty {}
      |given [T] => Ty {}
      |given () => Ty {}
      |given (A, B) => Ty {}
      |given (x: Int) => Ty {}
      |
      |given value: Ty {}
      |given value: () => Ty {}
      |given value: A => Ty {}
      |given value: [T] => Ty {}
      |given value: (A, B) => Ty {}
      |given value: (x: Int) => Ty {}
      |""".stripMargin,
    """
      |ScalaFile
      |  ScGivenDefinition: given_Ty
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: Ty
      |            CodeReferenceElement: Ty
      |              PsiElement(identifier)('Ty')
      |      PsiWhiteSpace(' ')
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: given_Ty
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        Parameter: <anonymous>
      |          ParameterType
      |            SimpleType: A
      |              CodeReferenceElement: A
      |                PsiElement(identifier)('A')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: Ty
      |            CodeReferenceElement: Ty
      |              PsiElement(identifier)('Ty')
      |      PsiWhiteSpace(' ')
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: given_Ty
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=>)('=>')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: Ty
      |            CodeReferenceElement: Ty
      |              PsiElement(identifier)('Ty')
      |      PsiWhiteSpace(' ')
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: given_Ty
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: Ty
      |            CodeReferenceElement: Ty
      |              PsiElement(identifier)('Ty')
      |      PsiWhiteSpace(' ')
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: given_Ty
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: <anonymous>
      |          ParameterType
      |            SimpleType: A
      |              CodeReferenceElement: A
      |                PsiElement(identifier)('A')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        Parameter: <anonymous>
      |          ParameterType
      |            SimpleType: B
      |              CodeReferenceElement: B
      |                PsiElement(identifier)('B')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: Ty
      |            CodeReferenceElement: Ty
      |              PsiElement(identifier)('Ty')
      |      PsiWhiteSpace(' ')
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: given_Ty
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: x
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(identifier)('x')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          ParameterType
      |            SimpleType: Int
      |              CodeReferenceElement: Int
      |                PsiElement(identifier)('Int')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: Ty
      |            CodeReferenceElement: Ty
      |              PsiElement(identifier)('Ty')
      |      PsiWhiteSpace(' ')
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n\n')
      |  ScGivenDefinition: value
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('value')
      |    PsiElement(:)(':')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: Ty
      |            CodeReferenceElement: Ty
      |              PsiElement(identifier)('Ty')
      |      PsiWhiteSpace(' ')
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: value
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('value')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: Ty
      |            CodeReferenceElement: Ty
      |              PsiElement(identifier)('Ty')
      |      PsiWhiteSpace(' ')
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: value
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('value')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        Parameter: <anonymous>
      |          ParameterType
      |            SimpleType: A
      |              CodeReferenceElement: A
      |                PsiElement(identifier)('A')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: Ty
      |            CodeReferenceElement: Ty
      |              PsiElement(identifier)('Ty')
      |      PsiWhiteSpace(' ')
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: value
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('value')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=>)('=>')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: Ty
      |            CodeReferenceElement: Ty
      |              PsiElement(identifier)('Ty')
      |      PsiWhiteSpace(' ')
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: value
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('value')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: <anonymous>
      |          ParameterType
      |            SimpleType: A
      |              CodeReferenceElement: A
      |                PsiElement(identifier)('A')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        Parameter: <anonymous>
      |          ParameterType
      |            SimpleType: B
      |              CodeReferenceElement: B
      |                PsiElement(identifier)('B')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: Ty
      |            CodeReferenceElement: Ty
      |              PsiElement(identifier)('Ty')
      |      PsiWhiteSpace(' ')
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: value
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('value')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: x
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(identifier)('x')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          ParameterType
      |            SimpleType: Int
      |              CodeReferenceElement: Int
      |                PsiElement(identifier)('Int')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: Ty
      |            CodeReferenceElement: Ty
      |              PsiElement(identifier)('Ty')
      |      PsiWhiteSpace(' ')
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_given_def_indentation(): Unit = checkTree(
    """given Ty + Ty:
      |  def test = 0
      |given A => Ty + Ty:
      |  def test = 0
      |given [T] => Ty + Ty:
      |  def test = 0
      |given () => Ty + Ty:
      |  def test = 0
      |given (A, B) => Ty + Ty:
      |  def test = 0
      |given (x: Int) => Ty + Ty:
      |  def test = 0
      |
      |given value: Ty + Ty:
      |  def test = 0
      |given value: () => Ty + Ty:
      |  def test = 0
      |given value: A => Ty + Ty:
      |  def test = 0
      |given value: [T] => Ty + Ty:
      |  def test = 0
      |given value: (A, B) => Ty + Ty:
      |  def test = 0
      |given value: (x: Int) => Ty + Ty:
      |  def test = 0
      |""".stripMargin,
    """
      |ScalaFile
      |  ScGivenDefinition: given_+_Ty_Ty
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          InfixType: Ty + Ty
      |            SimpleType: Ty
      |              CodeReferenceElement: Ty
      |                PsiElement(identifier)('Ty')
      |            PsiWhiteSpace(' ')
      |            CodeReferenceElement: +
      |              PsiElement(identifier)('+')
      |            PsiWhiteSpace(' ')
      |            SimpleType: Ty
      |              CodeReferenceElement: Ty
      |                PsiElement(identifier)('Ty')
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: test
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('test')
      |          Parameters
      |            <empty list>
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: given_+_Ty_Ty
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        Parameter: <anonymous>
      |          ParameterType
      |            SimpleType: A
      |              CodeReferenceElement: A
      |                PsiElement(identifier)('A')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          InfixType: Ty + Ty
      |            SimpleType: Ty
      |              CodeReferenceElement: Ty
      |                PsiElement(identifier)('Ty')
      |            PsiWhiteSpace(' ')
      |            CodeReferenceElement: +
      |              PsiElement(identifier)('+')
      |            PsiWhiteSpace(' ')
      |            SimpleType: Ty
      |              CodeReferenceElement: Ty
      |                PsiElement(identifier)('Ty')
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: test
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('test')
      |          Parameters
      |            <empty list>
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: given_+_Ty_Ty
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=>)('=>')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          InfixType: Ty + Ty
      |            SimpleType: Ty
      |              CodeReferenceElement: Ty
      |                PsiElement(identifier)('Ty')
      |            PsiWhiteSpace(' ')
      |            CodeReferenceElement: +
      |              PsiElement(identifier)('+')
      |            PsiWhiteSpace(' ')
      |            SimpleType: Ty
      |              CodeReferenceElement: Ty
      |                PsiElement(identifier)('Ty')
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: test
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('test')
      |          Parameters
      |            <empty list>
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: given_+_Ty_Ty
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          InfixType: Ty + Ty
      |            SimpleType: Ty
      |              CodeReferenceElement: Ty
      |                PsiElement(identifier)('Ty')
      |            PsiWhiteSpace(' ')
      |            CodeReferenceElement: +
      |              PsiElement(identifier)('+')
      |            PsiWhiteSpace(' ')
      |            SimpleType: Ty
      |              CodeReferenceElement: Ty
      |                PsiElement(identifier)('Ty')
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: test
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('test')
      |          Parameters
      |            <empty list>
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: given_+_Ty_Ty
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: <anonymous>
      |          ParameterType
      |            SimpleType: A
      |              CodeReferenceElement: A
      |                PsiElement(identifier)('A')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        Parameter: <anonymous>
      |          ParameterType
      |            SimpleType: B
      |              CodeReferenceElement: B
      |                PsiElement(identifier)('B')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          InfixType: Ty + Ty
      |            SimpleType: Ty
      |              CodeReferenceElement: Ty
      |                PsiElement(identifier)('Ty')
      |            PsiWhiteSpace(' ')
      |            CodeReferenceElement: +
      |              PsiElement(identifier)('+')
      |            PsiWhiteSpace(' ')
      |            SimpleType: Ty
      |              CodeReferenceElement: Ty
      |                PsiElement(identifier)('Ty')
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: test
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('test')
      |          Parameters
      |            <empty list>
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: given_+_Ty_Ty
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: x
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(identifier)('x')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          ParameterType
      |            SimpleType: Int
      |              CodeReferenceElement: Int
      |                PsiElement(identifier)('Int')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          InfixType: Ty + Ty
      |            SimpleType: Ty
      |              CodeReferenceElement: Ty
      |                PsiElement(identifier)('Ty')
      |            PsiWhiteSpace(' ')
      |            CodeReferenceElement: +
      |              PsiElement(identifier)('+')
      |            PsiWhiteSpace(' ')
      |            SimpleType: Ty
      |              CodeReferenceElement: Ty
      |                PsiElement(identifier)('Ty')
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: test
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('test')
      |          Parameters
      |            <empty list>
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('0')
      |  PsiWhiteSpace('\n\n')
      |  ScGivenDefinition: value
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('value')
      |    PsiElement(:)(':')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          InfixType: Ty + Ty
      |            SimpleType: Ty
      |              CodeReferenceElement: Ty
      |                PsiElement(identifier)('Ty')
      |            PsiWhiteSpace(' ')
      |            CodeReferenceElement: +
      |              PsiElement(identifier)('+')
      |            PsiWhiteSpace(' ')
      |            SimpleType: Ty
      |              CodeReferenceElement: Ty
      |                PsiElement(identifier)('Ty')
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: test
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('test')
      |          Parameters
      |            <empty list>
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: value
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('value')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          InfixType: Ty + Ty
      |            SimpleType: Ty
      |              CodeReferenceElement: Ty
      |                PsiElement(identifier)('Ty')
      |            PsiWhiteSpace(' ')
      |            CodeReferenceElement: +
      |              PsiElement(identifier)('+')
      |            PsiWhiteSpace(' ')
      |            SimpleType: Ty
      |              CodeReferenceElement: Ty
      |                PsiElement(identifier)('Ty')
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: test
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('test')
      |          Parameters
      |            <empty list>
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: value
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('value')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        Parameter: <anonymous>
      |          ParameterType
      |            SimpleType: A
      |              CodeReferenceElement: A
      |                PsiElement(identifier)('A')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          InfixType: Ty + Ty
      |            SimpleType: Ty
      |              CodeReferenceElement: Ty
      |                PsiElement(identifier)('Ty')
      |            PsiWhiteSpace(' ')
      |            CodeReferenceElement: +
      |              PsiElement(identifier)('+')
      |            PsiWhiteSpace(' ')
      |            SimpleType: Ty
      |              CodeReferenceElement: Ty
      |                PsiElement(identifier)('Ty')
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: test
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('test')
      |          Parameters
      |            <empty list>
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: value
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('value')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=>)('=>')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          InfixType: Ty + Ty
      |            SimpleType: Ty
      |              CodeReferenceElement: Ty
      |                PsiElement(identifier)('Ty')
      |            PsiWhiteSpace(' ')
      |            CodeReferenceElement: +
      |              PsiElement(identifier)('+')
      |            PsiWhiteSpace(' ')
      |            SimpleType: Ty
      |              CodeReferenceElement: Ty
      |                PsiElement(identifier)('Ty')
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: test
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('test')
      |          Parameters
      |            <empty list>
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: value
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('value')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: <anonymous>
      |          ParameterType
      |            SimpleType: A
      |              CodeReferenceElement: A
      |                PsiElement(identifier)('A')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        Parameter: <anonymous>
      |          ParameterType
      |            SimpleType: B
      |              CodeReferenceElement: B
      |                PsiElement(identifier)('B')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          InfixType: Ty + Ty
      |            SimpleType: Ty
      |              CodeReferenceElement: Ty
      |                PsiElement(identifier)('Ty')
      |            PsiWhiteSpace(' ')
      |            CodeReferenceElement: +
      |              PsiElement(identifier)('+')
      |            PsiWhiteSpace(' ')
      |            SimpleType: Ty
      |              CodeReferenceElement: Ty
      |                PsiElement(identifier)('Ty')
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: test
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('test')
      |          Parameters
      |            <empty list>
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: value
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('value')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: x
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(identifier)('x')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          ParameterType
      |            SimpleType: Int
      |              CodeReferenceElement: Int
      |                PsiElement(identifier)('Int')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          InfixType: Ty + Ty
      |            SimpleType: Ty
      |              CodeReferenceElement: Ty
      |                PsiElement(identifier)('Ty')
      |            PsiWhiteSpace(' ')
      |            CodeReferenceElement: +
      |              PsiElement(identifier)('+')
      |            PsiWhiteSpace(' ')
      |            SimpleType: Ty
      |              CodeReferenceElement: Ty
      |                PsiElement(identifier)('Ty')
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: test
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('test')
      |          Parameters
      |            <empty list>
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_given_def_constr_app(): Unit = checkTree(
    """given Ty(arg):
      |  def test = 0
      |given A => Ty(arg):
      |  def test = 0
      |given [T] => Ty(arg):
      |  def test = 0
      |given () => Ty(arg):
      |  def test = 0
      |given (A, B) => Ty(arg):
      |  def test = 0
      |given (x: Int) => Ty(arg):
      |  def test = 0
      |
      |given value: Ty(arg):
      |  def test = 0
      |given value: () => Ty(arg):
      |  def test = 0
      |given value: A => Ty(arg):
      |  def test = 0
      |given value: [T] => Ty(arg):
      |  def test = 0
      |given value: (A, B) => Ty(arg):
      |  def test = 0
      |given value: (x: Int) => Ty(arg):
      |  def test = 0
      |""".stripMargin,
    """
      |ScalaFile
      |  ScGivenDefinition: given_Ty
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: Ty
      |            CodeReferenceElement: Ty
      |              PsiElement(identifier)('Ty')
      |          ArgumentList
      |            PsiElement(()('(')
      |            ReferenceExpression: arg
      |              PsiElement(identifier)('arg')
      |            PsiElement())(')')
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: test
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('test')
      |          Parameters
      |            <empty list>
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: given_Ty
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        Parameter: <anonymous>
      |          ParameterType
      |            SimpleType: A
      |              CodeReferenceElement: A
      |                PsiElement(identifier)('A')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: Ty
      |            CodeReferenceElement: Ty
      |              PsiElement(identifier)('Ty')
      |          ArgumentList
      |            PsiElement(()('(')
      |            ReferenceExpression: arg
      |              PsiElement(identifier)('arg')
      |            PsiElement())(')')
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: test
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('test')
      |          Parameters
      |            <empty list>
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: given_Ty
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=>)('=>')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: Ty
      |            CodeReferenceElement: Ty
      |              PsiElement(identifier)('Ty')
      |          ArgumentList
      |            PsiElement(()('(')
      |            ReferenceExpression: arg
      |              PsiElement(identifier)('arg')
      |            PsiElement())(')')
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: test
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('test')
      |          Parameters
      |            <empty list>
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: given_Ty
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: Ty
      |            CodeReferenceElement: Ty
      |              PsiElement(identifier)('Ty')
      |          ArgumentList
      |            PsiElement(()('(')
      |            ReferenceExpression: arg
      |              PsiElement(identifier)('arg')
      |            PsiElement())(')')
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: test
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('test')
      |          Parameters
      |            <empty list>
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: given_Ty
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: <anonymous>
      |          ParameterType
      |            SimpleType: A
      |              CodeReferenceElement: A
      |                PsiElement(identifier)('A')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        Parameter: <anonymous>
      |          ParameterType
      |            SimpleType: B
      |              CodeReferenceElement: B
      |                PsiElement(identifier)('B')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: Ty
      |            CodeReferenceElement: Ty
      |              PsiElement(identifier)('Ty')
      |          ArgumentList
      |            PsiElement(()('(')
      |            ReferenceExpression: arg
      |              PsiElement(identifier)('arg')
      |            PsiElement())(')')
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: test
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('test')
      |          Parameters
      |            <empty list>
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: given_Ty
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: x
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(identifier)('x')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          ParameterType
      |            SimpleType: Int
      |              CodeReferenceElement: Int
      |                PsiElement(identifier)('Int')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: Ty
      |            CodeReferenceElement: Ty
      |              PsiElement(identifier)('Ty')
      |          ArgumentList
      |            PsiElement(()('(')
      |            ReferenceExpression: arg
      |              PsiElement(identifier)('arg')
      |            PsiElement())(')')
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: test
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('test')
      |          Parameters
      |            <empty list>
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('0')
      |  PsiWhiteSpace('\n\n')
      |  ScGivenDefinition: value
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('value')
      |    PsiElement(:)(':')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: Ty
      |            CodeReferenceElement: Ty
      |              PsiElement(identifier)('Ty')
      |          ArgumentList
      |            PsiElement(()('(')
      |            ReferenceExpression: arg
      |              PsiElement(identifier)('arg')
      |            PsiElement())(')')
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: test
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('test')
      |          Parameters
      |            <empty list>
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: value
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('value')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: Ty
      |            CodeReferenceElement: Ty
      |              PsiElement(identifier)('Ty')
      |          ArgumentList
      |            PsiElement(()('(')
      |            ReferenceExpression: arg
      |              PsiElement(identifier)('arg')
      |            PsiElement())(')')
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: test
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('test')
      |          Parameters
      |            <empty list>
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: value
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('value')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        Parameter: <anonymous>
      |          ParameterType
      |            SimpleType: A
      |              CodeReferenceElement: A
      |                PsiElement(identifier)('A')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: Ty
      |            CodeReferenceElement: Ty
      |              PsiElement(identifier)('Ty')
      |          ArgumentList
      |            PsiElement(()('(')
      |            ReferenceExpression: arg
      |              PsiElement(identifier)('arg')
      |            PsiElement())(')')
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: test
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('test')
      |          Parameters
      |            <empty list>
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: value
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('value')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=>)('=>')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: Ty
      |            CodeReferenceElement: Ty
      |              PsiElement(identifier)('Ty')
      |          ArgumentList
      |            PsiElement(()('(')
      |            ReferenceExpression: arg
      |              PsiElement(identifier)('arg')
      |            PsiElement())(')')
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: test
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('test')
      |          Parameters
      |            <empty list>
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: value
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('value')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: <anonymous>
      |          ParameterType
      |            SimpleType: A
      |              CodeReferenceElement: A
      |                PsiElement(identifier)('A')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        Parameter: <anonymous>
      |          ParameterType
      |            SimpleType: B
      |              CodeReferenceElement: B
      |                PsiElement(identifier)('B')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: Ty
      |            CodeReferenceElement: Ty
      |              PsiElement(identifier)('Ty')
      |          ArgumentList
      |            PsiElement(()('(')
      |            ReferenceExpression: arg
      |              PsiElement(identifier)('arg')
      |            PsiElement())(')')
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: test
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('test')
      |          Parameters
      |            <empty list>
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: value
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('value')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: x
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(identifier)('x')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          ParameterType
      |            SimpleType: Int
      |              CodeReferenceElement: Int
      |                PsiElement(identifier)('Int')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: Ty
      |            CodeReferenceElement: Ty
      |              PsiElement(identifier)('Ty')
      |          ArgumentList
      |            PsiElement(()('(')
      |            ReferenceExpression: arg
      |              PsiElement(identifier)('arg')
      |            PsiElement())(')')
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: test
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('test')
      |          Parameters
      |            <empty list>
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_multi_conditionals(): Unit = checkTree(
    """
      |given [T] => () => (T) => Ord[T] => T = 0
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenAliasDefinition: given_T
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |      PsiWhiteSpace(' ')
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: <anonymous>
      |          ParameterType
      |            SimpleType: T
      |              CodeReferenceElement: T
      |                PsiElement(identifier)('T')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |      PsiWhiteSpace(' ')
      |      ParametersClause
      |        Parameter: <anonymous>
      |          ParameterType
      |            ParametrizedType: Ord[T]
      |              SimpleType: Ord
      |                CodeReferenceElement: Ord
      |                  PsiElement(identifier)('Ord')
      |              TypeArgumentsList
      |                PsiElement([)('[')
      |                SimpleType: T
      |                  CodeReferenceElement: T
      |                    PsiElement(identifier)('T')
      |                PsiElement(])(']')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    SimpleType: T
      |      CodeReferenceElement: T
      |        PsiElement(identifier)('T')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    IntegerLiteral
      |      PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_indentation(): Unit = checkTree(
    """
      |given Test:
      | def blub = 3
      |end given
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: given_Test
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: Test
      |            CodeReferenceElement: Test
      |              PsiElement(identifier)('Test')
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n ')
      |        ScFunctionDefinition: blub
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('blub')
      |          Parameters
      |            <empty list>
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('3')
      |        PsiWhiteSpace('\n')
      |        End: given
      |          PsiElement(end)('end')
      |          PsiWhiteSpace(' ')
      |          PsiElement(given)('given')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_indentation_1(): Unit = checkTree(
    """
      |given Test:
      | println(42)
      | def blub = 3
      |end given
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: given_Test
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: Test
      |            CodeReferenceElement: Test
      |              PsiElement(identifier)('Test')
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n ')
      |        MethodCall
      |          ReferenceExpression: println
      |            PsiElement(identifier)('println')
      |          ArgumentList
      |            PsiElement(()('(')
      |            IntegerLiteral
      |              PsiElement(integer)('42')
      |            PsiElement())(')')
      |        PsiWhiteSpace('\n ')
      |        ScFunctionDefinition: blub
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('blub')
      |          Parameters
      |            <empty list>
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('3')
      |        PsiWhiteSpace('\n')
      |        End: given
      |          PsiElement(end)('end')
      |          PsiWhiteSpace(' ')
      |          PsiElement(given)('given')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  // can occur during typing, unindented expressions shouldn't be parsed as template body statements
  def test_indentation_incomplete_body_followed_by_unindented_expressions(): Unit = checkTree(
    """given Test:
      |println(1)
      |println(2)
      |""".stripMargin,
    """
      |ScalaFile
      |  ScGivenDefinition: given_Test
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: Test
      |            CodeReferenceElement: Test
      |              PsiElement(identifier)('Test')
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiErrorElement:Indented definitions expected
      |          <empty list>
      |  PsiWhiteSpace('\n')
      |  MethodCall
      |    ReferenceExpression: println
      |      PsiElement(identifier)('println')
      |    ArgumentList
      |      PsiElement(()('(')
      |      IntegerLiteral
      |        PsiElement(integer)('1')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  MethodCall
      |    ReferenceExpression: println
      |      PsiElement(identifier)('println')
      |    ArgumentList
      |      PsiElement(()('(')
      |      IntegerLiteral
      |        PsiElement(integer)('2')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_indentation_incomplete_body_followed_by_unindented_expressions_1(): Unit = checkTree(
    """given intOrd3: Ord[Int] with MarkerTrait(42):
      |println(1)
      |println(2)
      |""".stripMargin,
    """
      |ScalaFile
      |  ScGivenDefinition: intOrd3
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('intOrd3')
      |    PsiElement(:)(':')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          ParametrizedType: Ord[Int]
      |            SimpleType: Ord
      |              CodeReferenceElement: Ord
      |                PsiElement(identifier)('Ord')
      |            TypeArgumentsList
      |              PsiElement([)('[')
      |              SimpleType: Int
      |                CodeReferenceElement: Int
      |                  PsiElement(identifier)('Int')
      |              PsiElement(])(']')
      |        PsiWhiteSpace(' ')
      |        PsiElement(with)('with')
      |        PsiWhiteSpace(' ')
      |        ConstructorInvocation
      |          SimpleType: MarkerTrait
      |            CodeReferenceElement: MarkerTrait
      |              PsiElement(identifier)('MarkerTrait')
      |          ArgumentList
      |            PsiElement(()('(')
      |            IntegerLiteral
      |              PsiElement(integer)('42')
      |            PsiElement())(')')
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiErrorElement:Indented definitions expected
      |          <empty list>
      |  PsiWhiteSpace('\n')
      |  MethodCall
      |    ReferenceExpression: println
      |      PsiElement(identifier)('println')
      |    ArgumentList
      |      PsiElement(()('(')
      |      IntegerLiteral
      |        PsiElement(integer)('1')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  MethodCall
      |    ReferenceExpression: println
      |      PsiElement(identifier)('println')
      |    ArgumentList
      |      PsiElement(()('(')
      |      IntegerLiteral
      |        PsiElement(integer)('2')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_incomplete_given_alias_definition_without_type_annotation(): Unit = checkTree(
    """given value: = ???
      |given value: (x: Int) =>  = ???
      |""".stripMargin,
    """
      |ScalaFile
      |  ScGivenAliasDefinition: value
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('value')
      |    PsiElement(:)(':')
      |    Parameters
      |      <empty list>
      |    PsiErrorElement:Type expected
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n')
      |  ScGivenAliasDefinition: value
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('value')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: x
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(identifier)('x')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          ParameterType
      |            SimpleType: Int
      |              CodeReferenceElement: Int
      |                PsiElement(identifier)('Int')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiErrorElement:Type expected
      |      <empty list>
      |    PsiWhiteSpace('  ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_incomplete_given_impl_without_constructor_invocation(): Unit = checkTree(
    """given value: with MyTrait {}
      |""".stripMargin,
    """ScalaFile
      |  ScGivenDefinition: value
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('value')
      |    PsiElement(:)(':')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      PsiErrorElement:Type expected
      |        <empty list>
      |      PsiElement(with)('with')
      |      PsiWhiteSpace(' ')
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: MyTrait
      |            CodeReferenceElement: MyTrait
      |              PsiElement(identifier)('MyTrait')
      |      PsiWhiteSpace(' ')
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_full(): Unit = checkTree(
    """
      |given Test: [T] => (Ord[T]) => Ord[Int] {}
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: Test
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Test')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: <anonymous>
      |          ParameterType
      |            ParametrizedType: Ord[T]
      |              SimpleType: Ord
      |                CodeReferenceElement: Ord
      |                  PsiElement(identifier)('Ord')
      |              TypeArgumentsList
      |                PsiElement([)('[')
      |                SimpleType: T
      |                  CodeReferenceElement: T
      |                    PsiElement(identifier)('T')
      |                PsiElement(])(']')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          ParametrizedType: Ord[Int]
      |            SimpleType: Ord
      |              CodeReferenceElement: Ord
      |                PsiElement(identifier)('Ord')
      |            TypeArgumentsList
      |              PsiElement([)('[')
      |              SimpleType: Int
      |                CodeReferenceElement: Int
      |                  PsiElement(identifier)('Int')
      |              PsiElement(])(']')
      |      PsiWhiteSpace(' ')
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_constr_app(): Unit = checkTree(
    """
      |given Foo()
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: given_Foo
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: Foo
      |            CodeReferenceElement: Foo
      |              PsiElement(identifier)('Foo')
      |          ArgumentList
      |            PsiElement(()('(')
      |            PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  /********************************** with Template body *********************************************/

  def test_wrong_using_in_param_clause(): Unit = checkTree(
    """
      |given [T] => (using Ord[T]) => Ord[Int] = ???
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenAliasDefinition: given_Ord_Int
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        PsiElement(using)('using')
      |        PsiWhiteSpace(' ')
      |        Parameter: <anonymous>
      |          ParameterType
      |            ParametrizedType: Ord[T]
      |              SimpleType: Ord
      |                CodeReferenceElement: Ord
      |                  PsiElement(identifier)('Ord')
      |              TypeArgumentsList
      |                PsiElement([)('[')
      |                SimpleType: T
      |                  CodeReferenceElement: T
      |                    PsiElement(identifier)('T')
      |                PsiElement(])(']')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    ParametrizedType: Ord[Int]
      |      SimpleType: Ord
      |        CodeReferenceElement: Ord
      |          PsiElement(identifier)('Ord')
      |      TypeArgumentsList
      |        PsiElement([)('[')
      |        SimpleType: Int
      |          CodeReferenceElement: Int
      |            PsiElement(identifier)('Int')
      |        PsiElement(])(']')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_incomplete_given_aliases(): Unit = checkTree(
    """
      |object A {
      |  given
      |  given []
      |  given [T] =>
      |  given [T] => (x: Int
      |  given [T] => (x: Int) =>
      |  given [T] => (x: Int) => Int
      |  given [T] => (x: Int) => Int =
      |}
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScObject: A
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(object)('object')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('A')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiWhiteSpace('\n  ')
      |        ScGivenDefinition: given_
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(given)('given')
      |          Parameters
      |            <empty list>
      |          ExtendsBlock
      |            TemplateParents
      |              PsiErrorElement:Identifier expected
      |                <empty list>
      |        PsiWhiteSpace('\n  ')
      |        ScGivenDefinition: given_
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(given)('given')
      |          PsiWhiteSpace(' ')
      |          TypeParameterClause
      |            PsiElement([)('[')
      |            PsiErrorElement:Wrong parameter
      |              <empty list>
      |            PsiElement(])(']')
      |          PsiErrorElement:'=>' expected
      |            <empty list>
      |          Parameters
      |            <empty list>
      |          ExtendsBlock
      |            TemplateParents
      |              <empty list>
      |        PsiWhiteSpace('\n  ')
      |        ScGivenDefinition: given_
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(given)('given')
      |          PsiWhiteSpace(' ')
      |          TypeParameterClause
      |            PsiElement([)('[')
      |            TypeParameter: T
      |              PsiElement(identifier)('T')
      |            PsiElement(])(']')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=>)('=>')
      |          Parameters
      |            <empty list>
      |          ExtendsBlock
      |            TemplateParents
      |              PsiErrorElement:Identifier expected
      |                <empty list>
      |        PsiWhiteSpace('\n  ')
      |        ScGivenDefinition: given_
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(given)('given')
      |          PsiWhiteSpace(' ')
      |          TypeParameterClause
      |            PsiElement([)('[')
      |            TypeParameter: T
      |              PsiElement(identifier)('T')
      |            PsiElement(])(']')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=>)('=>')
      |          PsiWhiteSpace(' ')
      |          Parameters
      |            ParametersClause
      |              PsiElement(()('(')
      |              Parameter: x
      |                AnnotationsList
      |                  <empty list>
      |                Modifiers
      |                  <empty list>
      |                PsiElement(identifier)('x')
      |                PsiElement(:)(':')
      |                PsiWhiteSpace(' ')
      |                ParameterType
      |                  SimpleType: Int
      |                    CodeReferenceElement: Int
      |                      PsiElement(identifier)('Int')
      |              PsiErrorElement:')' expected
      |                <empty list>
      |          ExtendsBlock
      |            TemplateParents
      |              <empty list>
      |        PsiWhiteSpace('\n  ')
      |        ScGivenDefinition: given_
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(given)('given')
      |          PsiWhiteSpace(' ')
      |          TypeParameterClause
      |            PsiElement([)('[')
      |            TypeParameter: T
      |              PsiElement(identifier)('T')
      |            PsiElement(])(']')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=>)('=>')
      |          PsiWhiteSpace(' ')
      |          Parameters
      |            ParametersClause
      |              PsiElement(()('(')
      |              Parameter: x
      |                AnnotationsList
      |                  <empty list>
      |                Modifiers
      |                  <empty list>
      |                PsiElement(identifier)('x')
      |                PsiElement(:)(':')
      |                PsiWhiteSpace(' ')
      |                ParameterType
      |                  SimpleType: Int
      |                    CodeReferenceElement: Int
      |                      PsiElement(identifier)('Int')
      |              PsiElement())(')')
      |            PsiWhiteSpace(' ')
      |            PsiElement(=>)('=>')
      |          ExtendsBlock
      |            TemplateParents
      |              PsiErrorElement:Identifier expected
      |                <empty list>
      |        PsiWhiteSpace('\n  ')
      |        ScGivenDefinition: given_Int
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(given)('given')
      |          PsiWhiteSpace(' ')
      |          TypeParameterClause
      |            PsiElement([)('[')
      |            TypeParameter: T
      |              PsiElement(identifier)('T')
      |            PsiElement(])(']')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=>)('=>')
      |          PsiWhiteSpace(' ')
      |          Parameters
      |            ParametersClause
      |              PsiElement(()('(')
      |              Parameter: x
      |                AnnotationsList
      |                  <empty list>
      |                Modifiers
      |                  <empty list>
      |                PsiElement(identifier)('x')
      |                PsiElement(:)(':')
      |                PsiWhiteSpace(' ')
      |                ParameterType
      |                  SimpleType: Int
      |                    CodeReferenceElement: Int
      |                      PsiElement(identifier)('Int')
      |              PsiElement())(')')
      |            PsiWhiteSpace(' ')
      |            PsiElement(=>)('=>')
      |          PsiWhiteSpace(' ')
      |          ExtendsBlock
      |            TemplateParents
      |              ConstructorInvocation
      |                SimpleType: Int
      |                  CodeReferenceElement: Int
      |                    PsiElement(identifier)('Int')
      |        PsiWhiteSpace('\n  ')
      |        ScGivenAliasDefinition: given_Int
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(given)('given')
      |          PsiWhiteSpace(' ')
      |          TypeParameterClause
      |            PsiElement([)('[')
      |            TypeParameter: T
      |              PsiElement(identifier)('T')
      |            PsiElement(])(']')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=>)('=>')
      |          PsiWhiteSpace(' ')
      |          Parameters
      |            ParametersClause
      |              PsiElement(()('(')
      |              Parameter: x
      |                AnnotationsList
      |                  <empty list>
      |                Modifiers
      |                  <empty list>
      |                PsiElement(identifier)('x')
      |                PsiElement(:)(':')
      |                PsiWhiteSpace(' ')
      |                ParameterType
      |                  SimpleType: Int
      |                    CodeReferenceElement: Int
      |                      PsiElement(identifier)('Int')
      |              PsiElement())(')')
      |            PsiWhiteSpace(' ')
      |            PsiElement(=>)('=>')
      |          PsiWhiteSpace(' ')
      |          SimpleType: Int
      |            CodeReferenceElement: Int
      |              PsiElement(identifier)('Int')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiErrorElement:Expression expected
      |            <empty list>
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  // SCL-23314
  def testInfixAs(): Unit = checkTree(
    """
      |val conv: (String as Int) = ???
      |given instance: (String as Int) = ???
      |def test(ev: (String as Int)) = ???
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScPatternDefinition: conv
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: conv
      |        PsiElement(identifier)('conv')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    TypeInParenthesis: (String as Int)
      |      PsiElement(()('(')
      |      InfixType: String as Int
      |        SimpleType: String
      |          CodeReferenceElement: String
      |            PsiElement(identifier)('String')
      |        PsiWhiteSpace(' ')
      |        CodeReferenceElement: as
      |          PsiElement(identifier)('as')
      |        PsiWhiteSpace(' ')
      |        SimpleType: Int
      |          CodeReferenceElement: Int
      |            PsiElement(identifier)('Int')
      |      PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n')
      |  ScGivenAliasDefinition: instance
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('instance')
      |    PsiElement(:)(':')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    TypeInParenthesis: (String as Int)
      |      PsiElement(()('(')
      |      InfixType: String as Int
      |        SimpleType: String
      |          CodeReferenceElement: String
      |            PsiElement(identifier)('String')
      |        PsiWhiteSpace(' ')
      |        CodeReferenceElement: as
      |          PsiElement(identifier)('as')
      |        PsiWhiteSpace(' ')
      |        SimpleType: Int
      |          CodeReferenceElement: Int
      |            PsiElement(identifier)('Int')
      |      PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n')
      |  ScFunctionDefinition: test
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('test')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: ev
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(identifier)('ev')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          ParameterType
      |            TypeInParenthesis: (String as Int)
      |              PsiElement(()('(')
      |              InfixType: String as Int
      |                SimpleType: String
      |                  CodeReferenceElement: String
      |                    PsiElement(identifier)('String')
      |                PsiWhiteSpace(' ')
      |                CodeReferenceElement: as
      |                  PsiElement(identifier)('as')
      |                PsiWhiteSpace(' ')
      |                SimpleType: Int
      |                  CodeReferenceElement: Int
      |                    PsiElement(identifier)('Int')
      |              PsiElement())(')')
      |        PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )
}
