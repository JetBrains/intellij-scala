package org.jetbrains.plugins.scala.lang.parser.scala3

import org.jetbrains.plugins.scala.ScalaVersion

class CaptureCheckingParserTest extends SimpleScala3ParserTestBase {
  override protected def scalaVersion: ScalaVersion = ScalaVersion.Latest.Scala_3_8

  def test_capture_type(): Unit = checkTree(
    """
      |x: A^
      |x: A^{}
      |x: left ^ right
      |x: left ^ (right)
      |x: left ^ 1
      |x: left ^ "literal"
      |x: (left^) ^ right
      |x: arg^ -> ret
      |x: arg^ ?-> ret
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    InfixType: left ^ right
      |      SimpleType: left
      |        CodeReferenceElement: left
      |          PsiElement(identifier)('left')
      |      PsiWhiteSpace(' ')
      |      CodeReferenceElement: ^
      |        PsiElement(identifier)('^')
      |      PsiWhiteSpace(' ')
      |      SimpleType: right
      |        CodeReferenceElement: right
      |          PsiElement(identifier)('right')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    InfixType: left ^ (right)
      |      SimpleType: left
      |        CodeReferenceElement: left
      |          PsiElement(identifier)('left')
      |      PsiWhiteSpace(' ')
      |      CodeReferenceElement: ^
      |        PsiElement(identifier)('^')
      |      PsiWhiteSpace(' ')
      |      TypeInParenthesis: (right)
      |        PsiElement(()('(')
      |        SimpleType: right
      |          CodeReferenceElement: right
      |            PsiElement(identifier)('right')
      |        PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    InfixType: left ^ 1
      |      SimpleType: left
      |        CodeReferenceElement: left
      |          PsiElement(identifier)('left')
      |      PsiWhiteSpace(' ')
      |      CodeReferenceElement: ^
      |        PsiElement(identifier)('^')
      |      PsiWhiteSpace(' ')
      |      LiteralType: 1
      |        IntegerLiteral
      |          PsiElement(integer)('1')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    InfixType: left ^ "literal"
      |      SimpleType: left
      |        CodeReferenceElement: left
      |          PsiElement(identifier)('left')
      |      PsiWhiteSpace(' ')
      |      CodeReferenceElement: ^
      |        PsiElement(identifier)('^')
      |      PsiWhiteSpace(' ')
      |      LiteralType: "literal"
      |        StringLiteral
      |          PsiElement(string content)('"literal"')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    InfixType: (left^) ^ right
      |      TypeInParenthesis: (left^)
      |        PsiElement(()('(')
      |        CaptureType: left^
      |          SimpleType: left
      |            CodeReferenceElement: left
      |              PsiElement(identifier)('left')
      |          PsiElement(^)('^')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      CodeReferenceElement: ^
      |        PsiElement(identifier)('^')
      |      PsiWhiteSpace(' ')
      |      SimpleType: right
      |        CodeReferenceElement: right
      |          PsiElement(identifier)('right')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    FunctionalType: arg^ -> ret
      |      CaptureType: arg^
      |        SimpleType: arg
      |          CodeReferenceElement: arg
      |            PsiElement(identifier)('arg')
      |        PsiElement(^)('^')
      |      PsiWhiteSpace(' ')
      |      PsiElement(->)('->')
      |      PsiWhiteSpace(' ')
      |      SimpleType: ret
      |        CodeReferenceElement: ret
      |          PsiElement(identifier)('ret')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    FunctionalType: arg^ ?-> ret
      |      CaptureType: arg^
      |        SimpleType: arg
      |          CodeReferenceElement: arg
      |            PsiElement(identifier)('arg')
      |        PsiElement(^)('^')
      |      PsiWhiteSpace(' ')
      |      PsiElement(?->)('?->')
      |      PsiWhiteSpace(' ')
      |      SimpleType: ret
      |        CodeReferenceElement: ret
      |          PsiElement(identifier)('ret')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

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

  def test_capture_set_on_functions(): Unit = checkTree(
    """
      |x: Int ->{} Int
      |x: Int ?->{cap} Int
      |x: Int =>{this, x} Int
      |x: Int ?=>{bullshit{{} x} haha()} Int
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    FunctionalType: Int ->{} Int
      |      SimpleType: Int
      |        CodeReferenceElement: Int
      |          PsiElement(identifier)('Int')
      |      PsiWhiteSpace(' ')
      |      PsiElement(->)('->')
      |      CaptureSet
      |        PsiElement({)('{')
      |        PsiElement(})('}')
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
      |    FunctionalType: Int ?->{cap} Int
      |      SimpleType: Int
      |        CodeReferenceElement: Int
      |          PsiElement(identifier)('Int')
      |      PsiWhiteSpace(' ')
      |      PsiElement(?->)('?->')
      |      CaptureSet
      |        PsiElement({)('{')
      |        PsiElement(identifier)('cap')
      |        PsiElement(})('}')
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
      |    FunctionalType: Int =>{this, x} Int
      |      SimpleType: Int
      |        CodeReferenceElement: Int
      |          PsiElement(identifier)('Int')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |      CaptureSet
      |        PsiElement({)('{')
      |        PsiElement(this)('this')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('x')
      |        PsiElement(})('}')
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
      |    FunctionalType: Int ?=>{bullshit{{} x} haha()} Int
      |      SimpleType: Int
      |        CodeReferenceElement: Int
      |          PsiElement(identifier)('Int')
      |      PsiWhiteSpace(' ')
      |      PsiElement(?=>)('?=>')
      |      CaptureSet
      |        PsiElement({)('{')
      |        PsiElement(identifier)('bullshit')
      |        PsiElement({)('{')
      |        PsiElement({)('{')
      |        PsiElement(})('}')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('x')
      |        PsiElement(})('}')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('haha')
      |        PsiElement(()('(')
      |        PsiElement())(')')
      |        PsiElement(})('}')
      |      PsiWhiteSpace(' ')
      |      SimpleType: Int
      |        CodeReferenceElement: Int
      |          PsiElement(identifier)('Int')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )
}
