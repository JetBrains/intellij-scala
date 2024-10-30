package org.jetbrains.plugins.scala.lang.parser.scala3

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class Scala3_6_NewGivensParserTest extends SimpleScala3ParserTestBase {
  override protected def scalaVersion: ScalaVersion = LatestScalaVersions.Scala_3_6

  def testSimpleEmptyType(): Unit = checkTree(
    "given Ord[Int]",
    """ScalaFile
      |  ScGivenDefinition: given_Ord_Int
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
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
      |              PsiElement(])(']')""".stripMargin
  )

  def testStillParsedAsAbstract(): Unit = checkTree(
    "given ord: Ord[Int]",
    """ScalaFile
      |  ScGivenAliasDeclaration: ord
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('ord')
      |    Parameters
      |      <empty list>
      |    PsiElement(:)(':')
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
      |        PsiElement(])(']')""".stripMargin
  )

  def testSimpleInstance(): Unit = checkTree(
    """given Ord[Int]:
      |  def compare(x: Int, y: Int) = ???""".stripMargin,
    """ScalaFile
      |  ScGivenAliasDeclaration: Ord
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Ord')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: Int
      |        PsiElement(identifier)('Int')
      |      PsiElement(])(']')
      |    Parameters
      |      <empty list>
      |    PsiElement(:)(':')
      |    PsiErrorElement:Wrong type
      |      <empty list>
      |  PsiWhiteSpace('\n  ')
      |  ScFunctionDefinition: compare
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('compare')
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
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        Parameter: y
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(identifier)('y')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          ParameterType
      |            SimpleType: Int
      |              CodeReferenceElement: Int
      |                PsiElement(identifier)('Int')
      |        PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')""".stripMargin
  )

  def testSimpleInstanceNamed(): Unit = checkTree(
    """given ordInt: Ord[Int]:
      |  def compare(x: Int, y: Int) = ???""".stripMargin,
    """ScalaFile
      |  ScGivenDefinition: ordInt
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('ordInt')
      |    Parameters
      |      <empty list>
      |    PsiElement(:)(':')
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
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: compare
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('compare')
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
      |              PsiElement(,)(',')
      |              PsiWhiteSpace(' ')
      |              Parameter: y
      |                AnnotationsList
      |                  <empty list>
      |                Modifiers
      |                  <empty list>
      |                PsiElement(identifier)('y')
      |                PsiElement(:)(':')
      |                PsiWhiteSpace(' ')
      |                ParameterType
      |                  SimpleType: Int
      |                    CodeReferenceElement: Int
      |                      PsiElement(identifier)('Int')
      |              PsiElement())(')')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')""".stripMargin
  )

  def testParameterizedInstanceWithContextBound(): Unit = checkTree(
    """given [A: Ord] => Ord[List[A]]:
      |  def compare(x: List[A], y: List[A]) = ???""".stripMargin,
    """ScalaFile
      |  ScGivenDefinition: given_Ord_List
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: A
      |        PsiElement(identifier)('A')
      |        PsiElement(:)(':')
      |        PsiWhiteSpace(' ')
      |        ScContextBoundImpl(context bound)
      |          SimpleType: Ord
      |            CodeReferenceElement: Ord
      |              PsiElement(identifier)('Ord')
      |      PsiElement(])(']')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=>)('=>')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          ParametrizedType: Ord[List[A]]
      |            SimpleType: Ord
      |              CodeReferenceElement: Ord
      |                PsiElement(identifier)('Ord')
      |            TypeArgumentsList
      |              PsiElement([)('[')
      |              ParametrizedType: List[A]
      |                SimpleType: List
      |                  CodeReferenceElement: List
      |                    PsiElement(identifier)('List')
      |                TypeArgumentsList
      |                  PsiElement([)('[')
      |                  SimpleType: A
      |                    CodeReferenceElement: A
      |                      PsiElement(identifier)('A')
      |                  PsiElement(])(']')
      |              PsiElement(])(']')
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: compare
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('compare')
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
      |                  ParametrizedType: List[A]
      |                    SimpleType: List
      |                      CodeReferenceElement: List
      |                        PsiElement(identifier)('List')
      |                    TypeArgumentsList
      |                      PsiElement([)('[')
      |                      SimpleType: A
      |                        CodeReferenceElement: A
      |                          PsiElement(identifier)('A')
      |                      PsiElement(])(']')
      |              PsiElement(,)(',')
      |              PsiWhiteSpace(' ')
      |              Parameter: y
      |                AnnotationsList
      |                  <empty list>
      |                Modifiers
      |                  <empty list>
      |                PsiElement(identifier)('y')
      |                PsiElement(:)(':')
      |                PsiWhiteSpace(' ')
      |                ParameterType
      |                  ParametrizedType: List[A]
      |                    SimpleType: List
      |                      CodeReferenceElement: List
      |                        PsiElement(identifier)('List')
      |                    TypeArgumentsList
      |                      PsiElement([)('[')
      |                      SimpleType: A
      |                        CodeReferenceElement: A
      |                          PsiElement(identifier)('A')
      |                      PsiElement(])(']')
      |              PsiElement())(')')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')""".stripMargin
  )

  def testParameterizedInstanceWithContextBoundNamed(): Unit = checkTree(
    """given listOrd: [A: Ord] => Ord[List[A]]:
      |  def compare(x: List[A], y: List[A]) = ???""".stripMargin,
    """ScalaFile
      |  ScGivenDefinition: listOrd
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('listOrd')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: A
      |        PsiElement(identifier)('A')
      |        PsiElement(:)(':')
      |        PsiWhiteSpace(' ')
      |        ScContextBoundImpl(context bound)
      |          SimpleType: Ord
      |            CodeReferenceElement: Ord
      |              PsiElement(identifier)('Ord')
      |      PsiElement(])(']')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=>)('=>')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          ParametrizedType: Ord[List[A]]
      |            SimpleType: Ord
      |              CodeReferenceElement: Ord
      |                PsiElement(identifier)('Ord')
      |            TypeArgumentsList
      |              PsiElement([)('[')
      |              ParametrizedType: List[A]
      |                SimpleType: List
      |                  CodeReferenceElement: List
      |                    PsiElement(identifier)('List')
      |                TypeArgumentsList
      |                  PsiElement([)('[')
      |                  SimpleType: A
      |                    CodeReferenceElement: A
      |                      PsiElement(identifier)('A')
      |                  PsiElement(])(']')
      |              PsiElement(])(']')
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: compare
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('compare')
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
      |                  ParametrizedType: List[A]
      |                    SimpleType: List
      |                      CodeReferenceElement: List
      |                        PsiElement(identifier)('List')
      |                    TypeArgumentsList
      |                      PsiElement([)('[')
      |                      SimpleType: A
      |                        CodeReferenceElement: A
      |                          PsiElement(identifier)('A')
      |                      PsiElement(])(']')
      |              PsiElement(,)(',')
      |              PsiWhiteSpace(' ')
      |              Parameter: y
      |                AnnotationsList
      |                  <empty list>
      |                Modifiers
      |                  <empty list>
      |                PsiElement(identifier)('y')
      |                PsiElement(:)(':')
      |                PsiWhiteSpace(' ')
      |                ParameterType
      |                  ParametrizedType: List[A]
      |                    SimpleType: List
      |                      CodeReferenceElement: List
      |                        PsiElement(identifier)('List')
      |                    TypeArgumentsList
      |                      PsiElement([)('[')
      |                      SimpleType: A
      |                        CodeReferenceElement: A
      |                          PsiElement(identifier)('A')
      |                      PsiElement(])(']')
      |              PsiElement())(')')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')""".stripMargin
  )

  def testParameterizedInstanceWithContextParameter(): Unit = checkTree(
    """given [A] => Ord[A] => Ord[List[A]]:
      |  def compare(x: List[A], y: List[A]) = ???""".stripMargin,
    """ScalaFile
      |  ScGivenDefinition: given_Ord_List
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: A
      |        PsiElement(identifier)('A')
      |      PsiElement(])(']')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        Parameter: <anonymous>
      |          ParameterType
      |            ParametrizedType: Ord[A]
      |              SimpleType: Ord
      |                CodeReferenceElement: Ord
      |                  PsiElement(identifier)('Ord')
      |              TypeArgumentsList
      |                PsiElement([)('[')
      |                SimpleType: A
      |                  CodeReferenceElement: A
      |                    PsiElement(identifier)('A')
      |                PsiElement(])(']')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          ParametrizedType: Ord[List[A]]
      |            SimpleType: Ord
      |              CodeReferenceElement: Ord
      |                PsiElement(identifier)('Ord')
      |            TypeArgumentsList
      |              PsiElement([)('[')
      |              ParametrizedType: List[A]
      |                SimpleType: List
      |                  CodeReferenceElement: List
      |                    PsiElement(identifier)('List')
      |                TypeArgumentsList
      |                  PsiElement([)('[')
      |                  SimpleType: A
      |                    CodeReferenceElement: A
      |                      PsiElement(identifier)('A')
      |                  PsiElement(])(']')
      |              PsiElement(])(']')
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: compare
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('compare')
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
      |                  ParametrizedType: List[A]
      |                    SimpleType: List
      |                      CodeReferenceElement: List
      |                        PsiElement(identifier)('List')
      |                    TypeArgumentsList
      |                      PsiElement([)('[')
      |                      SimpleType: A
      |                        CodeReferenceElement: A
      |                          PsiElement(identifier)('A')
      |                      PsiElement(])(']')
      |              PsiElement(,)(',')
      |              PsiWhiteSpace(' ')
      |              Parameter: y
      |                AnnotationsList
      |                  <empty list>
      |                Modifiers
      |                  <empty list>
      |                PsiElement(identifier)('y')
      |                PsiElement(:)(':')
      |                PsiWhiteSpace(' ')
      |                ParameterType
      |                  ParametrizedType: List[A]
      |                    SimpleType: List
      |                      CodeReferenceElement: List
      |                        PsiElement(identifier)('List')
      |                    TypeArgumentsList
      |                      PsiElement([)('[')
      |                      SimpleType: A
      |                        CodeReferenceElement: A
      |                          PsiElement(identifier)('A')
      |                      PsiElement(])(']')
      |              PsiElement())(')')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')""".stripMargin
  )

  def testParameterizedInstanceWithNamedContextParameter(): Unit = checkTree(
    """given [A] => (ord: Ord[A]) => Ord[List[A]]:
      |  def compare(x: List[A], y: List[A]) = ???""".stripMargin,
    """ScalaFile
      |  ScGivenDefinition: given_Ord_List
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: A
      |        PsiElement(identifier)('A')
      |      PsiElement(])(']')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: ord
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(identifier)('ord')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          ParameterType
      |            ParametrizedType: Ord[A]
      |              SimpleType: Ord
      |                CodeReferenceElement: Ord
      |                  PsiElement(identifier)('Ord')
      |              TypeArgumentsList
      |                PsiElement([)('[')
      |                SimpleType: A
      |                  CodeReferenceElement: A
      |                    PsiElement(identifier)('A')
      |                PsiElement(])(']')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          ParametrizedType: Ord[List[A]]
      |            SimpleType: Ord
      |              CodeReferenceElement: Ord
      |                PsiElement(identifier)('Ord')
      |            TypeArgumentsList
      |              PsiElement([)('[')
      |              ParametrizedType: List[A]
      |                SimpleType: List
      |                  CodeReferenceElement: List
      |                    PsiElement(identifier)('List')
      |                TypeArgumentsList
      |                  PsiElement([)('[')
      |                  SimpleType: A
      |                    CodeReferenceElement: A
      |                      PsiElement(identifier)('A')
      |                  PsiElement(])(']')
      |              PsiElement(])(']')
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: compare
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('compare')
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
      |                  ParametrizedType: List[A]
      |                    SimpleType: List
      |                      CodeReferenceElement: List
      |                        PsiElement(identifier)('List')
      |                    TypeArgumentsList
      |                      PsiElement([)('[')
      |                      SimpleType: A
      |                        CodeReferenceElement: A
      |                          PsiElement(identifier)('A')
      |                      PsiElement(])(']')
      |              PsiElement(,)(',')
      |              PsiWhiteSpace(' ')
      |              Parameter: y
      |                AnnotationsList
      |                  <empty list>
      |                Modifiers
      |                  <empty list>
      |                PsiElement(identifier)('y')
      |                PsiElement(:)(':')
      |                PsiWhiteSpace(' ')
      |                ParameterType
      |                  ParametrizedType: List[A]
      |                    SimpleType: List
      |                      CodeReferenceElement: List
      |                        PsiElement(identifier)('List')
      |                    TypeArgumentsList
      |                      PsiElement([)('[')
      |                      SimpleType: A
      |                        CodeReferenceElement: A
      |                          PsiElement(identifier)('A')
      |                      PsiElement(])(']')
      |              PsiElement())(')')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')""".stripMargin
  )

  def testParameterizedInstanceWithContextParameterNamed(): Unit = checkTree(
    """given listOrd: [A] => Ord[A] => Ord[List[A]]:
      |  def compare(x: List[A], y: List[A]) = ???""".stripMargin,
    """ScalaFile
      |  ScGivenDefinition: listOrd
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('listOrd')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: A
      |        PsiElement(identifier)('A')
      |      PsiElement(])(']')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        Parameter: <anonymous>
      |          ParameterType
      |            ParametrizedType: Ord[A]
      |              SimpleType: Ord
      |                CodeReferenceElement: Ord
      |                  PsiElement(identifier)('Ord')
      |              TypeArgumentsList
      |                PsiElement([)('[')
      |                SimpleType: A
      |                  CodeReferenceElement: A
      |                    PsiElement(identifier)('A')
      |                PsiElement(])(']')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          ParametrizedType: Ord[List[A]]
      |            SimpleType: Ord
      |              CodeReferenceElement: Ord
      |                PsiElement(identifier)('Ord')
      |            TypeArgumentsList
      |              PsiElement([)('[')
      |              ParametrizedType: List[A]
      |                SimpleType: List
      |                  CodeReferenceElement: List
      |                    PsiElement(identifier)('List')
      |                TypeArgumentsList
      |                  PsiElement([)('[')
      |                  SimpleType: A
      |                    CodeReferenceElement: A
      |                      PsiElement(identifier)('A')
      |                  PsiElement(])(']')
      |              PsiElement(])(']')
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: compare
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('compare')
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
      |                  ParametrizedType: List[A]
      |                    SimpleType: List
      |                      CodeReferenceElement: List
      |                        PsiElement(identifier)('List')
      |                    TypeArgumentsList
      |                      PsiElement([)('[')
      |                      SimpleType: A
      |                        CodeReferenceElement: A
      |                          PsiElement(identifier)('A')
      |                      PsiElement(])(']')
      |              PsiElement(,)(',')
      |              PsiWhiteSpace(' ')
      |              Parameter: y
      |                AnnotationsList
      |                  <empty list>
      |                Modifiers
      |                  <empty list>
      |                PsiElement(identifier)('y')
      |                PsiElement(:)(':')
      |                PsiWhiteSpace(' ')
      |                ParameterType
      |                  ParametrizedType: List[A]
      |                    SimpleType: List
      |                      CodeReferenceElement: List
      |                        PsiElement(identifier)('List')
      |                    TypeArgumentsList
      |                      PsiElement([)('[')
      |                      SimpleType: A
      |                        CodeReferenceElement: A
      |                          PsiElement(identifier)('A')
      |                      PsiElement(])(']')
      |              PsiElement())(')')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')""".stripMargin
  )

  def testParameterizedAliasWithContextBound(): Unit = checkTree(
    """ given [A: Ord] => Ord[List[A]] =
      |    ListOrd[A]""".stripMargin,
    """ScalaFile
      |  PsiWhiteSpace(' ')
      |  ScGivenAliasDefinition: given_Ord_List
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: A
      |        PsiElement(identifier)('A')
      |        PsiElement(:)(':')
      |        PsiWhiteSpace(' ')
      |        ScContextBoundImpl(context bound)
      |          SimpleType: Ord
      |            CodeReferenceElement: Ord
      |              PsiElement(identifier)('Ord')
      |      PsiElement(])(']')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=>)('=>')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    ParametrizedType: Ord[List[A]]
      |      SimpleType: Ord
      |        CodeReferenceElement: Ord
      |          PsiElement(identifier)('Ord')
      |      TypeArgumentsList
      |        PsiElement([)('[')
      |        ParametrizedType: List[A]
      |          SimpleType: List
      |            CodeReferenceElement: List
      |              PsiElement(identifier)('List')
      |          TypeArgumentsList
      |            PsiElement([)('[')
      |            SimpleType: A
      |              CodeReferenceElement: A
      |                PsiElement(identifier)('A')
      |            PsiElement(])(']')
      |        PsiElement(])(']')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace('\n    ')
      |    GenericCall
      |      ReferenceExpression: ListOrd
      |        PsiElement(identifier)('ListOrd')
      |      TypeArgumentsList
      |        PsiElement([)('[')
      |        SimpleType: A
      |          CodeReferenceElement: A
      |            PsiElement(identifier)('A')
      |        PsiElement(])(']')""".stripMargin
  )

  def testParameterizedAliasWithContextBoundNamed(): Unit = checkTree(
    """ given listOrd: [A: Ord] => Ord[List[A]] =
      |    ListOrd[A]""".stripMargin,
    """ScalaFile
      |  PsiWhiteSpace(' ')
      |  ScGivenAliasDefinition: listOrd
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('listOrd')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: A
      |        PsiElement(identifier)('A')
      |        PsiElement(:)(':')
      |        PsiWhiteSpace(' ')
      |        ScContextBoundImpl(context bound)
      |          SimpleType: Ord
      |            CodeReferenceElement: Ord
      |              PsiElement(identifier)('Ord')
      |      PsiElement(])(']')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=>)('=>')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    ParametrizedType: Ord[List[A]]
      |      SimpleType: Ord
      |        CodeReferenceElement: Ord
      |          PsiElement(identifier)('Ord')
      |      TypeArgumentsList
      |        PsiElement([)('[')
      |        ParametrizedType: List[A]
      |          SimpleType: List
      |            CodeReferenceElement: List
      |              PsiElement(identifier)('List')
      |          TypeArgumentsList
      |            PsiElement([)('[')
      |            SimpleType: A
      |              CodeReferenceElement: A
      |                PsiElement(identifier)('A')
      |            PsiElement(])(']')
      |        PsiElement(])(']')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace('\n    ')
      |    GenericCall
      |      ReferenceExpression: ListOrd
      |        PsiElement(identifier)('ListOrd')
      |      TypeArgumentsList
      |        PsiElement([)('[')
      |        SimpleType: A
      |          CodeReferenceElement: A
      |            PsiElement(identifier)('A')
      |        PsiElement(])(']')""".stripMargin
  )

  def testByNameGiven(): Unit = checkTree(
    "given context: () => Context = curCtx",
    """ScalaFile
      |  ScGivenAliasDefinition: context
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('context')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    SimpleType: Context
      |      CodeReferenceElement: Context
      |        PsiElement(identifier)('Context')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: curCtx
      |      PsiElement(identifier)('curCtx')""".stripMargin
  )

  def testBodyWithJustBraces(): Unit = checkTree(
    """given [A: Ord] => Ord[List[A]] {
      |  def compare(x: List[A], y: List[A]) = ???
      |}""".stripMargin,
    """ScalaFile
      |  ScGivenDefinition: given_Ord_List
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: A
      |        PsiElement(identifier)('A')
      |        PsiElement(:)(':')
      |        PsiWhiteSpace(' ')
      |        ScContextBoundImpl(context bound)
      |          SimpleType: Ord
      |            CodeReferenceElement: Ord
      |              PsiElement(identifier)('Ord')
      |      PsiElement(])(']')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=>)('=>')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          ParametrizedType: Ord[List[A]]
      |            SimpleType: Ord
      |              CodeReferenceElement: Ord
      |                PsiElement(identifier)('Ord')
      |            TypeArgumentsList
      |              PsiElement([)('[')
      |              ParametrizedType: List[A]
      |                SimpleType: List
      |                  CodeReferenceElement: List
      |                    PsiElement(identifier)('List')
      |                TypeArgumentsList
      |                  PsiElement([)('[')
      |                  SimpleType: A
      |                    CodeReferenceElement: A
      |                      PsiElement(identifier)('A')
      |                  PsiElement(])(']')
      |              PsiElement(])(']')
      |      PsiWhiteSpace(' ')
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: compare
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('compare')
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
      |                  ParametrizedType: List[A]
      |                    SimpleType: List
      |                      CodeReferenceElement: List
      |                        PsiElement(identifier)('List')
      |                    TypeArgumentsList
      |                      PsiElement([)('[')
      |                      SimpleType: A
      |                        CodeReferenceElement: A
      |                          PsiElement(identifier)('A')
      |                      PsiElement(])(']')
      |              PsiElement(,)(',')
      |              PsiWhiteSpace(' ')
      |              Parameter: y
      |                AnnotationsList
      |                  <empty list>
      |                Modifiers
      |                  <empty list>
      |                PsiElement(identifier)('y')
      |                PsiElement(:)(':')
      |                PsiWhiteSpace(' ')
      |                ParameterType
      |                  ParametrizedType: List[A]
      |                    SimpleType: List
      |                      CodeReferenceElement: List
      |                        PsiElement(identifier)('List')
      |                    TypeArgumentsList
      |                      PsiElement([)('[')
      |                      SimpleType: A
      |                        CodeReferenceElement: A
      |                          PsiElement(identifier)('A')
      |                      PsiElement(])(']')
      |              PsiElement())(')')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')""".stripMargin
  )

  def testMultipleParents(): Unit = checkTree(
    """
      |given [A, B, X] => (x: X) => Ord[A], Show[B] {
      |}
      |""".stripMargin,
    """ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: given_Ord_A_Show_B
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: A
      |        PsiElement(identifier)('A')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: B
      |        PsiElement(identifier)('B')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: X
      |        PsiElement(identifier)('X')
      |      PsiElement(])(']')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=>)('=>')
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
      |            SimpleType: X
      |              CodeReferenceElement: X
      |                PsiElement(identifier)('X')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          ParametrizedType: Ord[A]
      |            SimpleType: Ord
      |              CodeReferenceElement: Ord
      |                PsiElement(identifier)('Ord')
      |            TypeArgumentsList
      |              PsiElement([)('[')
      |              SimpleType: A
      |                CodeReferenceElement: A
      |                  PsiElement(identifier)('A')
      |              PsiElement(])(']')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        ConstructorInvocation
      |          ParametrizedType: Show[B]
      |            SimpleType: Show
      |              CodeReferenceElement: Show
      |                PsiElement(identifier)('Show')
      |            TypeArgumentsList
      |              PsiElement([)('[')
      |              SimpleType: B
      |                CodeReferenceElement: B
      |                  PsiElement(identifier)('B')
      |              PsiElement(])(']')
      |      PsiWhiteSpace(' ')
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')""".stripMargin
  )
}
