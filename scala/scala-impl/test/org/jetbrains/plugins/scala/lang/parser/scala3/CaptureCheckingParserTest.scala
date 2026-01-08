package org.jetbrains.plugins.scala.lang.parser.scala3

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.project.ScalaFeatures

class CaptureCheckingParserTest extends SimpleScala3ParserTestBase {
  override def scalaCodeParsingFeatures: ScalaFeatures.SerializableScalaFeatures =
    ScalaFeatures.custom(ScalaVersion.Latest.Scala_3_8, hasCaptureCheckingEnabled = true)
  //override protected def scalaVersion: ScalaVersion = ScalaVersion.Latest.Scala_3_8

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
      |x: Int =>{blub, x} Int
      |x: Int ?=>{} Int
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
      |        CaptureRef
      |          CodeReferenceElement: cap
      |            PsiElement(identifier)('cap')
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
      |    FunctionalType: Int =>{blub, x} Int
      |      SimpleType: Int
      |        CodeReferenceElement: Int
      |          PsiElement(identifier)('Int')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: blub
      |            PsiElement(identifier)('blub')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        CaptureRef
      |          CodeReferenceElement: x
      |            PsiElement(identifier)('x')
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
      |    FunctionalType: Int ?=>{} Int
      |      SimpleType: Int
      |        CodeReferenceElement: Int
      |          PsiElement(identifier)('Int')
      |      PsiWhiteSpace(' ')
      |      PsiElement(?=>)('?=>')
      |      CaptureSet
      |        PsiElement({)('{')
      |        PsiElement(})('}')
      |      PsiWhiteSpace(' ')
      |      SimpleType: Int
      |        CodeReferenceElement: Int
      |          PsiElement(identifier)('Int')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_capture_set(): Unit = checkTree(
    """
      |x: A^{}
      |x: A^{this}
      |x: A^{super.id}
      |x: A^{id.this.this}
      |x: A^{id.super.id.super[x].id}
      |x: A^{id}
      |x: A^{id.id}
      |x: A^{id, id, id.id}
      |x: A^{id.id.rd}
      |x: A^{id.id.as[B]}
      |x: A^{id.id.as[B].rd}
      |x: A^{id.id*}
      |x: A^{id.id*.rd}
      |x: A^{id.id*.as[B]}
      |x: A^{id.id*.as[B].rd}
      |""".stripMargin,
    """
      |ScalaFile
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
      |    CaptureType: A^{this}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          ThisReference
      |            PsiElement(this)('this')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{super.id}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: super.id
      |            SuperReference
      |              PsiElement(super)('super')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('id')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id.this.this}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          ThisReference
      |            ThisReference
      |              CodeReferenceElement: id
      |                PsiElement(identifier)('id')
      |              PsiElement(.)('.')
      |              PsiElement(this)('this')
      |            PsiElement(.)('.')
      |            PsiElement(this)('this')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id.super.id.super[x].id}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id.super.id.super[x].id
      |            SuperReference
      |              CodeReferenceElement: id.super.id
      |                SuperReference
      |                  CodeReferenceElement: id
      |                    PsiElement(identifier)('id')
      |                  PsiElement(.)('.')
      |                  PsiElement(super)('super')
      |                PsiElement(.)('.')
      |                PsiElement(identifier)('id')
      |              PsiElement(.)('.')
      |              PsiElement(super)('super')
      |              PsiElement([)('[')
      |              PsiElement(identifier)('x')
      |              PsiElement(])(']')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('id')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id
      |            PsiElement(identifier)('id')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id.id}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id.id
      |            CodeReferenceElement: id
      |              PsiElement(identifier)('id')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('id')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id, id, id.id}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id
      |            PsiElement(identifier)('id')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        CaptureRef
      |          CodeReferenceElement: id
      |            PsiElement(identifier)('id')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        CaptureRef
      |          CodeReferenceElement: id.id
      |            CodeReferenceElement: id
      |              PsiElement(identifier)('id')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('id')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id.id.rd}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id.id
      |            CodeReferenceElement: id
      |              PsiElement(identifier)('id')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('id')
      |          PsiElement(.)('.')
      |          PsiElement(rd)('rd')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id.id.as[B]}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id.id
      |            CodeReferenceElement: id
      |              PsiElement(identifier)('id')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('id')
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(as)('as')
      |            PsiElement([)('[')
      |            CodeReferenceElement: B
      |              PsiElement(identifier)('B')
      |            PsiElement(])(']')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id.id.as[B].rd}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id.id
      |            CodeReferenceElement: id
      |              PsiElement(identifier)('id')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('id')
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(as)('as')
      |            PsiElement([)('[')
      |            CodeReferenceElement: B
      |              PsiElement(identifier)('B')
      |            PsiElement(])(']')
      |          PsiElement(.)('.')
      |          PsiElement(rd)('rd')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id.id*}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id.id
      |            CodeReferenceElement: id
      |              PsiElement(identifier)('id')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('id')
      |          PsiElement(*)('*')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id.id*.rd}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id.id
      |            CodeReferenceElement: id
      |              PsiElement(identifier)('id')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('id')
      |          PsiElement(*)('*')
      |          PsiElement(.)('.')
      |          PsiElement(rd)('rd')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id.id*.as[B]}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id.id
      |            CodeReferenceElement: id
      |              PsiElement(identifier)('id')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('id')
      |          PsiElement(*)('*')
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(as)('as')
      |            PsiElement([)('[')
      |            CodeReferenceElement: B
      |              PsiElement(identifier)('B')
      |            PsiElement(])(']')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id.id*.as[B].rd}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id.id
      |            CodeReferenceElement: id
      |              PsiElement(identifier)('id')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('id')
      |          PsiElement(*)('*')
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(as)('as')
      |            PsiElement([)('[')
      |            CodeReferenceElement: B
      |              PsiElement(identifier)('B')
      |            PsiElement(])(']')
      |          PsiElement(.)('.')
      |          PsiElement(rd)('rd')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_incomplete_capture_set(): Unit = checkTree(
    """
      |x: A^{.}
      |x: A^{,}
      |x: A^{, id}
      |x: A^{.rd}
      |x: A^{.as[x]}
      |x: A^{id.}
      |x: A^{id.as}
      |x: A^{id.as[}
      |x: A^{id.as[]}
      |x: A^{id.as[x}
      |x: A^{id.as[x].}
      |x: A^{id id}
      |x: A^{super}
      |x: A^{super.}
      |x: A^{super.this}
      |x: A^{super.[x]}
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{.}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          PsiErrorElement:Capture reference expected
      |            <empty list>
      |          PsiElement(.)('.')
      |          PsiErrorElement:'as' or 'rd' expected
      |            <empty list>
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{,}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        PsiErrorElement:Capture reference expected
      |          <empty list>
      |        PsiElement(,)(',')
      |        PsiErrorElement:Capture reference expected
      |          <empty list>
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{, id}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        PsiErrorElement:Capture reference expected
      |          <empty list>
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        CaptureRef
      |          CodeReferenceElement: id
      |            PsiElement(identifier)('id')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{.rd}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          PsiErrorElement:Capture reference expected
      |            <empty list>
      |          PsiElement(.)('.')
      |          PsiElement(rd)('rd')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{.as[x]}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          PsiErrorElement:Capture reference expected
      |            <empty list>
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(as)('as')
      |            PsiElement([)('[')
      |            CodeReferenceElement: x
      |              PsiElement(identifier)('x')
      |            PsiElement(])(']')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id.}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id.
      |            CodeReferenceElement: id
      |              PsiElement(identifier)('id')
      |            PsiElement(.)('.')
      |            PsiErrorElement:Identifier expected
      |              <empty list>
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id.as}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id
      |            PsiElement(identifier)('id')
      |          PsiElement(.)('.')
      |          PsiElement(as)('as')
      |          PsiErrorElement:'[' expected
      |            <empty list>
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id.as[}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id
      |            PsiElement(identifier)('id')
      |          PsiElement(.)('.')
      |          PsiElement(as)('as')
      |          PsiElement([)('[')
      |          PsiErrorElement:Wrong qualified identifier
      |            <empty list>
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id.as[]}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id
      |            PsiElement(identifier)('id')
      |          PsiElement(.)('.')
      |          PsiElement(as)('as')
      |          PsiElement([)('[')
      |          PsiErrorElement:Wrong qualified identifier
      |            <empty list>
      |          PsiElement(])(']')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id.as[x}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id
      |            PsiElement(identifier)('id')
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(as)('as')
      |            PsiElement([)('[')
      |            CodeReferenceElement: x
      |              PsiElement(identifier)('x')
      |            PsiErrorElement:']' expected
      |              <empty list>
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id.as[x].}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id
      |            PsiElement(identifier)('id')
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(as)('as')
      |            PsiElement([)('[')
      |            CodeReferenceElement: x
      |              PsiElement(identifier)('x')
      |            PsiElement(])(']')
      |          PsiElement(.)('.')
      |          PsiErrorElement:'rd' expected
      |            <empty list>
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id id}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id
      |            PsiElement(identifier)('id')
      |        PsiErrorElement:',' or '}' expected
      |          <empty list>
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('id')
      |        PsiErrorElement:Capture reference expected
      |          <empty list>
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{super}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          SuperReference
      |            PsiElement(super)('super')
      |            PsiErrorElement:'.' or class qualifier expected
      |              <empty list>
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{super.}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: super.
      |            SuperReference
      |              PsiElement(super)('super')
      |            PsiElement(.)('.')
      |            PsiErrorElement:Identifier expected
      |              <empty list>
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{super.this}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: super.
      |            SuperReference
      |              PsiElement(super)('super')
      |            PsiElement(.)('.')
      |            PsiErrorElement:Identifier expected
      |              <empty list>
      |        PsiElement(this)('this')
      |        PsiErrorElement:Capture reference expected
      |          <empty list>
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{super.[x]}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: super.
      |            SuperReference
      |              PsiElement(super)('super')
      |            PsiElement(.)('.')
      |            PsiErrorElement:Identifier expected
      |              <empty list>
      |        PsiElement([)('[')
      |        CaptureRef
      |          CodeReferenceElement: x
      |            PsiElement(identifier)('x')
      |        PsiErrorElement:',' or '}' expected
      |          <empty list>
      |        PsiElement(])(']')
      |        PsiErrorElement:Capture reference expected
      |          <empty list>
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def testParsingAtEof(): Unit = checkTree(
    """
      |x: A^{
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        PsiErrorElement:Capture reference expected
      |          <empty list>
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )
}
