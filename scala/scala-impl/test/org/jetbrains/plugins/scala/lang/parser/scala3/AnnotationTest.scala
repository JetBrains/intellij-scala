package org.jetbrains.plugins.scala.lang.parser.scala3

class AnnotationTest extends SimpleScala3ParserTestBase{

  def test_annotated_constructor(): Unit = checkTree(
    """
      |// 1 arg
      |class A @ann()
      |
      |// 1 arg + 1 param
      |class B @ann() ()
      |
      |// 1 arg + 2 param
      |class C @ann() ()()
      |
      |// 1 arg + 1 arg + 1 param
      |class D @ann() @ann() ()
      |
      |// 2 param
      |class E @ann (i: Int)()
      |
      |// 1 arg + 1 param
      |class F @ann(1: Int) ()
      |
      |// 1 arg
      |class G @ann(named = true)
      |
      |// 1 param
      |class H @ann (override val named: Int)
      |
      |// 1 arg + 1 param + error
      |class I @ann() () @ann
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScClass: A
      |    PsiComment(comment)('// 1 arg')
      |    PsiWhiteSpace('\n')
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('A')
      |    PsiWhiteSpace(' ')
      |    PrimaryConstructor
      |      AnnotationsList
      |        Annotation
      |          PsiElement(@)('@')
      |          AnnotationExpression
      |            ConstructorInvocation
      |              SimpleType: ann
      |                CodeReferenceElement: ann
      |                  PsiElement(identifier)('ann')
      |              ArgumentList
      |                PsiElement(()('(')
      |                PsiElement())(')')
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        <empty list>
      |    ExtendsBlock
      |      <empty list>
      |  PsiWhiteSpace('\n\n')
      |  ScClass: B
      |    PsiComment(comment)('// 1 arg + 1 param')
      |    PsiWhiteSpace('\n')
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('B')
      |    PsiWhiteSpace(' ')
      |    PrimaryConstructor
      |      AnnotationsList
      |        Annotation
      |          PsiElement(@)('@')
      |          AnnotationExpression
      |            ConstructorInvocation
      |              SimpleType: ann
      |                CodeReferenceElement: ann
      |                  PsiElement(identifier)('ann')
      |              ArgumentList
      |                PsiElement(()('(')
      |                PsiElement())(')')
      |      Modifiers
      |        <empty list>
      |      PsiWhiteSpace(' ')
      |      Parameters
      |        ParametersClause
      |          PsiElement(()('(')
      |          PsiElement())(')')
      |    ExtendsBlock
      |      <empty list>
      |  PsiWhiteSpace('\n\n')
      |  ScClass: C
      |    PsiComment(comment)('// 1 arg + 2 param')
      |    PsiWhiteSpace('\n')
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('C')
      |    PsiWhiteSpace(' ')
      |    PrimaryConstructor
      |      AnnotationsList
      |        Annotation
      |          PsiElement(@)('@')
      |          AnnotationExpression
      |            ConstructorInvocation
      |              SimpleType: ann
      |                CodeReferenceElement: ann
      |                  PsiElement(identifier)('ann')
      |              ArgumentList
      |                PsiElement(()('(')
      |                PsiElement())(')')
      |      Modifiers
      |        <empty list>
      |      PsiWhiteSpace(' ')
      |      Parameters
      |        ParametersClause
      |          PsiElement(()('(')
      |          PsiElement())(')')
      |        ParametersClause
      |          PsiElement(()('(')
      |          PsiElement())(')')
      |    ExtendsBlock
      |      <empty list>
      |  PsiWhiteSpace('\n\n')
      |  ScClass: D
      |    PsiComment(comment)('// 1 arg + 1 arg + 1 param')
      |    PsiWhiteSpace('\n')
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('D')
      |    PsiWhiteSpace(' ')
      |    PrimaryConstructor
      |      AnnotationsList
      |        Annotation
      |          PsiElement(@)('@')
      |          AnnotationExpression
      |            ConstructorInvocation
      |              SimpleType: ann
      |                CodeReferenceElement: ann
      |                  PsiElement(identifier)('ann')
      |              ArgumentList
      |                PsiElement(()('(')
      |                PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        Annotation
      |          PsiElement(@)('@')
      |          AnnotationExpression
      |            ConstructorInvocation
      |              SimpleType: ann
      |                CodeReferenceElement: ann
      |                  PsiElement(identifier)('ann')
      |              ArgumentList
      |                PsiElement(()('(')
      |                PsiElement())(')')
      |      Modifiers
      |        <empty list>
      |      PsiWhiteSpace(' ')
      |      Parameters
      |        ParametersClause
      |          PsiElement(()('(')
      |          PsiElement())(')')
      |    ExtendsBlock
      |      <empty list>
      |  PsiWhiteSpace('\n\n')
      |  ScClass: E
      |    PsiComment(comment)('// 2 param')
      |    PsiWhiteSpace('\n')
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('E')
      |    PsiWhiteSpace(' ')
      |    PrimaryConstructor
      |      AnnotationsList
      |        Annotation
      |          PsiElement(@)('@')
      |          AnnotationExpression
      |            ConstructorInvocation
      |              SimpleType: ann
      |                CodeReferenceElement: ann
      |                  PsiElement(identifier)('ann')
      |      Modifiers
      |        <empty list>
      |      PsiWhiteSpace(' ')
      |      Parameters
      |        ParametersClause
      |          PsiElement(()('(')
      |          ClassParameter: i
      |            AnnotationsList
      |              <empty list>
      |            Modifiers
      |              <empty list>
      |            PsiElement(identifier)('i')
      |            PsiElement(:)(':')
      |            PsiWhiteSpace(' ')
      |            ParameterType
      |              SimpleType: Int
      |                CodeReferenceElement: Int
      |                  PsiElement(identifier)('Int')
      |          PsiElement())(')')
      |        ParametersClause
      |          PsiElement(()('(')
      |          PsiElement())(')')
      |    ExtendsBlock
      |      <empty list>
      |  PsiWhiteSpace('\n\n')
      |  ScClass: F
      |    PsiComment(comment)('// 1 arg + 1 param')
      |    PsiWhiteSpace('\n')
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('F')
      |    PsiWhiteSpace(' ')
      |    PrimaryConstructor
      |      AnnotationsList
      |        Annotation
      |          PsiElement(@)('@')
      |          AnnotationExpression
      |            ConstructorInvocation
      |              SimpleType: ann
      |                CodeReferenceElement: ann
      |                  PsiElement(identifier)('ann')
      |              ArgumentList
      |                PsiElement(()('(')
      |                TypedExpression
      |                  IntegerLiteral
      |                    PsiElement(integer)('1')
      |                  PsiElement(:)(':')
      |                  PsiWhiteSpace(' ')
      |                  SimpleType: Int
      |                    CodeReferenceElement: Int
      |                      PsiElement(identifier)('Int')
      |                PsiElement())(')')
      |      Modifiers
      |        <empty list>
      |      PsiWhiteSpace(' ')
      |      Parameters
      |        ParametersClause
      |          PsiElement(()('(')
      |          PsiElement())(')')
      |    ExtendsBlock
      |      <empty list>
      |  PsiWhiteSpace('\n\n')
      |  ScClass: G
      |    PsiComment(comment)('// 1 arg')
      |    PsiWhiteSpace('\n')
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('G')
      |    PsiWhiteSpace(' ')
      |    PrimaryConstructor
      |      AnnotationsList
      |        Annotation
      |          PsiElement(@)('@')
      |          AnnotationExpression
      |            ConstructorInvocation
      |              SimpleType: ann
      |                CodeReferenceElement: ann
      |                  PsiElement(identifier)('ann')
      |              ArgumentList
      |                PsiElement(()('(')
      |                AssignStatement
      |                  ReferenceExpression: named
      |                    PsiElement(identifier)('named')
      |                  PsiWhiteSpace(' ')
      |                  PsiElement(=)('=')
      |                  PsiWhiteSpace(' ')
      |                  BooleanLiteral
      |                    PsiElement(true)('true')
      |                PsiElement())(')')
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        <empty list>
      |    ExtendsBlock
      |      <empty list>
      |  PsiWhiteSpace('\n\n')
      |  ScClass: H
      |    PsiComment(comment)('// 1 param')
      |    PsiWhiteSpace('\n')
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('H')
      |    PsiWhiteSpace(' ')
      |    PrimaryConstructor
      |      AnnotationsList
      |        Annotation
      |          PsiElement(@)('@')
      |          AnnotationExpression
      |            ConstructorInvocation
      |              SimpleType: ann
      |                CodeReferenceElement: ann
      |                  PsiElement(identifier)('ann')
      |      Modifiers
      |        <empty list>
      |      PsiWhiteSpace(' ')
      |      Parameters
      |        ParametersClause
      |          PsiElement(()('(')
      |          ClassParameter: named
      |            AnnotationsList
      |              <empty list>
      |            Modifiers
      |              PsiElement(override)('override')
      |            PsiWhiteSpace(' ')
      |            PsiElement(val)('val')
      |            PsiWhiteSpace(' ')
      |            PsiElement(identifier)('named')
      |            PsiElement(:)(':')
      |            PsiWhiteSpace(' ')
      |            ParameterType
      |              SimpleType: Int
      |                CodeReferenceElement: Int
      |                  PsiElement(identifier)('Int')
      |          PsiElement())(')')
      |    ExtendsBlock
      |      <empty list>
      |  PsiWhiteSpace('\n\n')
      |  ScClass: I
      |    PsiComment(comment)('// 1 arg + 1 param + error')
      |    PsiWhiteSpace('\n')
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('I')
      |    PsiWhiteSpace(' ')
      |    PrimaryConstructor
      |      AnnotationsList
      |        Annotation
      |          PsiElement(@)('@')
      |          AnnotationExpression
      |            ConstructorInvocation
      |              SimpleType: ann
      |                CodeReferenceElement: ann
      |                  PsiElement(identifier)('ann')
      |              ArgumentList
      |                PsiElement(()('(')
      |                PsiElement())(')')
      |      Modifiers
      |        <empty list>
      |      PsiWhiteSpace(' ')
      |      Parameters
      |        ParametersClause
      |          PsiElement(()('(')
      |          PsiElement())(')')
      |    ExtendsBlock
      |      <empty list>
      |  PsiErrorElement:';' or newline expected
      |    <empty list>
      |  PsiWhiteSpace(' ')
      |  Annotation
      |    PsiElement(@)('@')
      |    AnnotationExpression
      |      ConstructorInvocation
      |        SimpleType: ann
      |          CodeReferenceElement: ann
      |            PsiElement(identifier)('ann')
      |  PsiErrorElement:Missing statement for annotation
      |    <empty list>
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )
}
