package org.jetbrains.plugins.scala.lang.parser.scala3

import org.jetbrains.plugins.scala.ScalaVersion

class CaptureCheckingParserTest extends SimpleScala3ParserTestBase {
  override protected def scalaVersion: ScalaVersion = ScalaVersion.Latest.Scala_3_8

  def test_pure_function(): Unit = checkTree(
    """
      |// pure function
      |x: Int  -> Int   -> Int
      |x: Int  ?-> Int  ?-> Int
      |x: (Int -> Int)  -> Int
      |x: (Int ?-> Int) ?-> Int
      |// dependent functional type
      |x: (x: Int) ?-> x
      |x: (x: Int) ?=> x
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  PsiComment(comment)('// pure function')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    FunctionalType: Int  -> Int   -> Int
      |      SimpleType: Int
      |        CodeReferenceElement: Int
      |          PsiElement(identifier)('Int')
      |      PsiWhiteSpace('  ')
      |      PsiElement(->)('->')
      |      PsiWhiteSpace(' ')
      |      FunctionalType: Int   -> Int
      |        SimpleType: Int
      |          CodeReferenceElement: Int
      |            PsiElement(identifier)('Int')
      |        PsiWhiteSpace('   ')
      |        PsiElement(->)('->')
      |        PsiWhiteSpace(' ')
      |        SimpleType: Int
      |          CodeReferenceElement: Int
      |            PsiElement(identifier)('Int')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    FunctionalType: Int  ?-> Int  ?-> Int
      |      SimpleType: Int
      |        CodeReferenceElement: Int
      |          PsiElement(identifier)('Int')
      |      PsiWhiteSpace('  ')
      |      PsiElement(?->)('?->')
      |      PsiWhiteSpace(' ')
      |      FunctionalType: Int  ?-> Int
      |        SimpleType: Int
      |          CodeReferenceElement: Int
      |            PsiElement(identifier)('Int')
      |        PsiWhiteSpace('  ')
      |        PsiElement(?->)('?->')
      |        PsiWhiteSpace(' ')
      |        SimpleType: Int
      |          CodeReferenceElement: Int
      |            PsiElement(identifier)('Int')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    FunctionalType: (Int -> Int)  -> Int
      |      TypeInParenthesis: (Int -> Int)
      |        PsiElement(()('(')
      |        FunctionalType: Int -> Int
      |          SimpleType: Int
      |            CodeReferenceElement: Int
      |              PsiElement(identifier)('Int')
      |          PsiWhiteSpace(' ')
      |          PsiElement(->)('->')
      |          PsiWhiteSpace(' ')
      |          SimpleType: Int
      |            CodeReferenceElement: Int
      |              PsiElement(identifier)('Int')
      |        PsiElement())(')')
      |      PsiWhiteSpace('  ')
      |      PsiElement(->)('->')
      |      PsiWhiteSpace(' ')
      |      SimpleType: Int
      |        CodeReferenceElement: Int
      |          PsiElement(identifier)('Int')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    FunctionalType: (Int ?-> Int) ?-> Int
      |      TypeInParenthesis: (Int ?-> Int)
      |        PsiElement(()('(')
      |        FunctionalType: Int ?-> Int
      |          SimpleType: Int
      |            CodeReferenceElement: Int
      |              PsiElement(identifier)('Int')
      |          PsiWhiteSpace(' ')
      |          PsiElement(?->)('?->')
      |          PsiWhiteSpace(' ')
      |          SimpleType: Int
      |            CodeReferenceElement: Int
      |              PsiElement(identifier)('Int')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(?->)('?->')
      |      PsiWhiteSpace(' ')
      |      SimpleType: Int
      |        CodeReferenceElement: Int
      |          PsiElement(identifier)('Int')
      |  PsiWhiteSpace('\n')
      |  PsiComment(comment)('// dependent functional type')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    DependentFunctionType: (x: Int) ?-> x
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: x
      |          PsiElement(identifier)('x')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          ParameterType
      |            SimpleType: Int
      |              CodeReferenceElement: Int
      |                PsiElement(identifier)('Int')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(?->)('?->')
      |      PsiWhiteSpace(' ')
      |      SimpleType: x
      |        CodeReferenceElement: x
      |          PsiElement(identifier)('x')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    DependentFunctionType: (x: Int) ?=> x
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: x
      |          PsiElement(identifier)('x')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          ParameterType
      |            SimpleType: Int
      |              CodeReferenceElement: Int
      |                PsiElement(identifier)('Int')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(?=>)('?=>')
      |      PsiWhiteSpace(' ')
      |      SimpleType: x
      |        CodeReferenceElement: x
      |          PsiElement(identifier)('x')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_arrow_in_pattern(): Unit = checkTree(
    """
      |val a -> b = ???
      |val a ?-> b = ???
      |val (x: A -> B) = ???
      |val (x: A ?-> B) = ???
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScPatternDefinition: a, b
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      InfixPattern
      |        ReferencePattern: a
      |          PsiElement(identifier)('a')
      |        PsiWhiteSpace(' ')
      |        CodeReferenceElement: ->
      |          PsiElement(identifier)('->')
      |        PsiWhiteSpace(' ')
      |        ReferencePattern: b
      |          PsiElement(identifier)('b')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n')
      |  ScPatternDefinition: a, b
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      InfixPattern
      |        ReferencePattern: a
      |          PsiElement(identifier)('a')
      |        PsiWhiteSpace(' ')
      |        CodeReferenceElement: ?->
      |          PsiElement(identifier)('?->')
      |        PsiWhiteSpace(' ')
      |        ReferencePattern: b
      |          PsiElement(identifier)('b')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n')
      |  ScPatternDefinition: x
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      PatternInParenthesis
      |        PsiElement(()('(')
      |        Scala3 TypedPattern
      |          ReferencePattern: x
      |            PsiElement(identifier)('x')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          TypePattern
      |            InfixType: A -> B
      |              SimpleType: A
      |                CodeReferenceElement: A
      |                  PsiElement(identifier)('A')
      |              PsiWhiteSpace(' ')
      |              CodeReferenceElement: ->
      |                PsiElement(identifier)('->')
      |              PsiWhiteSpace(' ')
      |              SimpleType: B
      |                CodeReferenceElement: B
      |                  PsiElement(identifier)('B')
      |        PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n')
      |  ScPatternDefinition: x
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      PatternInParenthesis
      |        PsiElement(()('(')
      |        Scala3 TypedPattern
      |          ReferencePattern: x
      |            PsiElement(identifier)('x')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          TypePattern
      |            InfixType: A ?-> B
      |              SimpleType: A
      |                CodeReferenceElement: A
      |                  PsiElement(identifier)('A')
      |              PsiWhiteSpace(' ')
      |              CodeReferenceElement: ?->
      |                PsiElement(identifier)('?->')
      |              PsiWhiteSpace(' ')
      |              SimpleType: B
      |                CodeReferenceElement: B
      |                  PsiElement(identifier)('B')
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
