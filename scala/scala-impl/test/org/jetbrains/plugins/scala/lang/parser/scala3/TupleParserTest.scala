package org.jetbrains.plugins.scala.lang.parser.scala3

import org.jetbrains.plugins.scala.ScalaVersion

class TupleParserTest extends SimpleScala3ParserTestBase {
  override protected def scalaVersion: ScalaVersion = ScalaVersion.Latest.Scala_3_5

  def test_tuple_type_parsing(): Unit = checkTree(
    """
      |type T1 = (Int)
      |type T2 = (Int, String)
      |type T3 = (Int, String, Boolean)
      |
      |type NT1 = (name: String)
      |type NT2 = (name: String, age: Int)
      |type NT3 = (name: String, age: Int, isMale: Boolean)
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScTypeAliasDefinition: T1
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('T1')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    TypeInParenthesis: (Int)
      |      PsiElement(()('(')
      |      SimpleType: Int
      |        CodeReferenceElement: Int
      |          PsiElement(identifier)('Int')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  ScTypeAliasDefinition: T2
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('T2')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    TupleType: (Int, String)
      |      PsiElement(()('(')
      |      TypesList
      |        SimpleType: Int
      |          CodeReferenceElement: Int
      |            PsiElement(identifier)('Int')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        SimpleType: String
      |          CodeReferenceElement: String
      |            PsiElement(identifier)('String')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  ScTypeAliasDefinition: T3
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('T3')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    TupleType: (Int, String, Boolean)
      |      PsiElement(()('(')
      |      TypesList
      |        SimpleType: Int
      |          CodeReferenceElement: Int
      |            PsiElement(identifier)('Int')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        SimpleType: String
      |          CodeReferenceElement: String
      |            PsiElement(identifier)('String')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        SimpleType: Boolean
      |          CodeReferenceElement: Boolean
      |            PsiElement(identifier)('Boolean')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n\n')
      |  ScTypeAliasDefinition: NT1
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('NT1')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    NamedTupleType: (name: String)
      |      PsiElement(()('(')
      |      NamedTupleTypeComponent: name: String
      |        PsiElement(identifier)('name')
      |        PsiElement(:)(':')
      |        PsiWhiteSpace(' ')
      |        SimpleType: String
      |          CodeReferenceElement: String
      |            PsiElement(identifier)('String')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  ScTypeAliasDefinition: NT2
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('NT2')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    NamedTupleType: (name: String, age: Int)
      |      PsiElement(()('(')
      |      NamedTupleTypeComponent: name: String
      |        PsiElement(identifier)('name')
      |        PsiElement(:)(':')
      |        PsiWhiteSpace(' ')
      |        SimpleType: String
      |          CodeReferenceElement: String
      |            PsiElement(identifier)('String')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      NamedTupleTypeComponent: age: Int
      |        PsiElement(identifier)('age')
      |        PsiElement(:)(':')
      |        PsiWhiteSpace(' ')
      |        SimpleType: Int
      |          CodeReferenceElement: Int
      |            PsiElement(identifier)('Int')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  ScTypeAliasDefinition: NT3
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('NT3')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    NamedTupleType: (name: String, age: Int, isMale: Boolean)
      |      PsiElement(()('(')
      |      NamedTupleTypeComponent: name: String
      |        PsiElement(identifier)('name')
      |        PsiElement(:)(':')
      |        PsiWhiteSpace(' ')
      |        SimpleType: String
      |          CodeReferenceElement: String
      |            PsiElement(identifier)('String')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      NamedTupleTypeComponent: age: Int
      |        PsiElement(identifier)('age')
      |        PsiElement(:)(':')
      |        PsiWhiteSpace(' ')
      |        SimpleType: Int
      |          CodeReferenceElement: Int
      |            PsiElement(identifier)('Int')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      NamedTupleTypeComponent: isMale: Boolean
      |        PsiElement(identifier)('isMale')
      |        PsiElement(:)(':')
      |        PsiWhiteSpace(' ')
      |        SimpleType: Boolean
      |          CodeReferenceElement: Boolean
      |            PsiElement(identifier)('Boolean')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |""".stripMargin,
  )

  def test_tuple_type_trailing_comma_on_new_line(): Unit = checkTree(
    """
      |type T1 = (
      |  Int,
      |)
      |type T2 = (
      |  Int,
      |  String,
      |)
      |type T3 = (
      |  Int,
      |  String,
      |  Boolean,
      |)
      |
      |type NT1 = (
      |  name: String,
      |)
      |type NT2 = (
      |  name: String,
      |  age: Int,
      |)
      |type NT3 = (name: String, age: Int, isMale: Boolean,
      |)
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScTypeAliasDefinition: T1
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('T1')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    TypeInParenthesis: (
      |  Int,
      |)
      |      PsiElement(()('(')
      |      PsiWhiteSpace('\n  ')
      |      SimpleType: Int
      |        CodeReferenceElement: Int
      |          PsiElement(identifier)('Int')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace('\n')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  ScTypeAliasDefinition: T2
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('T2')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    TupleType: (
      |  Int,
      |  String,
      |)
      |      PsiElement(()('(')
      |      PsiWhiteSpace('\n  ')
      |      TypesList
      |        SimpleType: Int
      |          CodeReferenceElement: Int
      |            PsiElement(identifier)('Int')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace('\n  ')
      |        SimpleType: String
      |          CodeReferenceElement: String
      |            PsiElement(identifier)('String')
      |        PsiElement(,)(',')
      |      PsiWhiteSpace('\n')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  ScTypeAliasDefinition: T3
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('T3')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    TupleType: (
      |  Int,
      |  String,
      |  Boolean,
      |)
      |      PsiElement(()('(')
      |      PsiWhiteSpace('\n  ')
      |      TypesList
      |        SimpleType: Int
      |          CodeReferenceElement: Int
      |            PsiElement(identifier)('Int')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace('\n  ')
      |        SimpleType: String
      |          CodeReferenceElement: String
      |            PsiElement(identifier)('String')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace('\n  ')
      |        SimpleType: Boolean
      |          CodeReferenceElement: Boolean
      |            PsiElement(identifier)('Boolean')
      |        PsiElement(,)(',')
      |      PsiWhiteSpace('\n')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n\n')
      |  ScTypeAliasDefinition: NT1
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('NT1')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    NamedTupleType: (
      |  name: String,
      |)
      |      PsiElement(()('(')
      |      PsiWhiteSpace('\n  ')
      |      NamedTupleTypeComponent: name: String
      |        PsiElement(identifier)('name')
      |        PsiElement(:)(':')
      |        PsiWhiteSpace(' ')
      |        SimpleType: String
      |          CodeReferenceElement: String
      |            PsiElement(identifier)('String')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace('\n')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  ScTypeAliasDefinition: NT2
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('NT2')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    NamedTupleType: (
      |  name: String,
      |  age: Int,
      |)
      |      PsiElement(()('(')
      |      PsiWhiteSpace('\n  ')
      |      NamedTupleTypeComponent: name: String
      |        PsiElement(identifier)('name')
      |        PsiElement(:)(':')
      |        PsiWhiteSpace(' ')
      |        SimpleType: String
      |          CodeReferenceElement: String
      |            PsiElement(identifier)('String')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace('\n  ')
      |      NamedTupleTypeComponent: age: Int
      |        PsiElement(identifier)('age')
      |        PsiElement(:)(':')
      |        PsiWhiteSpace(' ')
      |        SimpleType: Int
      |          CodeReferenceElement: Int
      |            PsiElement(identifier)('Int')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace('\n')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  ScTypeAliasDefinition: NT3
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('NT3')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    NamedTupleType: (name: String, age: Int, isMale: Boolean,
      |)
      |      PsiElement(()('(')
      |      NamedTupleTypeComponent: name: String
      |        PsiElement(identifier)('name')
      |        PsiElement(:)(':')
      |        PsiWhiteSpace(' ')
      |        SimpleType: String
      |          CodeReferenceElement: String
      |            PsiElement(identifier)('String')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      NamedTupleTypeComponent: age: Int
      |        PsiElement(identifier)('age')
      |        PsiElement(:)(':')
      |        PsiWhiteSpace(' ')
      |        SimpleType: Int
      |          CodeReferenceElement: Int
      |            PsiElement(identifier)('Int')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      NamedTupleTypeComponent: isMale: Boolean
      |        PsiElement(identifier)('isMale')
      |        PsiElement(:)(':')
      |        PsiWhiteSpace(' ')
      |        SimpleType: Boolean
      |          CodeReferenceElement: Boolean
      |            PsiElement(identifier)('Boolean')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace('\n')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |""".stripMargin,
  )

  def test_tuple_type_parsing_errors(): Unit = checkTree(
    """
      |type A = (1, arg: T, true)
      |type B = (arg: T, a, true)
      |type C = (arg:)
      |type D = (arg:, arg2: T)
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScTypeAliasDefinition: A
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('A')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    TupleType: (1, arg
      |      PsiElement(()('(')
      |      TypesList
      |        LiteralType: 1
      |          IntegerLiteral
      |            PsiElement(integer)('1')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        SimpleType: arg
      |          CodeReferenceElement: arg
      |            PsiElement(identifier)('arg')
      |      PsiErrorElement:')' expected
      |        <empty list>
      |  PsiElement(:)(':')
      |  PsiWhiteSpace(' ')
      |  ReferenceExpression: T
      |    PsiElement(identifier)('T')
      |  PsiErrorElement:';' or newline expected
      |    <empty list>
      |  PsiElement(,)(',')
      |  PsiWhiteSpace(' ')
      |  BooleanLiteral
      |    PsiElement(true)('true')
      |  PsiErrorElement:';' or newline expected
      |    <empty list>
      |  PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  ScTypeAliasDefinition: B
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('B')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    NamedTupleType: (arg: T, a, true)
      |      PsiElement(()('(')
      |      NamedTupleTypeComponent: arg: T
      |        PsiElement(identifier)('arg')
      |        PsiElement(:)(':')
      |        PsiWhiteSpace(' ')
      |        SimpleType: T
      |          CodeReferenceElement: T
      |            PsiElement(identifier)('T')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      NamedTupleTypeComponent: a
      |        PsiElement(identifier)('a')
      |        PsiErrorElement:':' expected
      |          <empty list>
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      NamedTupleTypeComponent: true
      |        PsiErrorElement:Identifier expected
      |          <empty list>
      |        LiteralType: true
      |          BooleanLiteral
      |            PsiElement(true)('true')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  ScTypeAliasDefinition: C
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('C')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    NamedTupleType: (arg:)
      |      PsiElement(()('(')
      |      NamedTupleTypeComponent: arg:
      |        PsiElement(identifier)('arg')
      |        PsiElement(:)(':')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  ScTypeAliasDefinition: D
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('D')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    NamedTupleType: (arg:, arg2: T)
      |      PsiElement(()('(')
      |      NamedTupleTypeComponent: arg:
      |        PsiElement(identifier)('arg')
      |        PsiElement(:)(':')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      NamedTupleTypeComponent: arg2: T
      |        PsiElement(identifier)('arg2')
      |        PsiElement(:)(':')
      |        PsiWhiteSpace(' ')
      |        SimpleType: T
      |          CodeReferenceElement: T
      |            PsiElement(identifier)('T')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_tuple_expr_parsing(): Unit = checkTree(
    """
      |(id)
      |(id, 1)
      |(id, 1, true)
      |
      |(name = A)
      |(name = A, age = B)
      |(name = A, age = B, isMale = C)
    """.stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ExpressionInParenthesis
      |    PsiElement(()('(')
      |    ReferenceExpression: id
      |      PsiElement(identifier)('id')
      |    PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  Tuple
      |    PsiElement(()('(')
      |    ReferenceExpression: id
      |      PsiElement(identifier)('id')
      |    PsiElement(,)(',')
      |    PsiWhiteSpace(' ')
      |    IntegerLiteral
      |      PsiElement(integer)('1')
      |    PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  Tuple
      |    PsiElement(()('(')
      |    ReferenceExpression: id
      |      PsiElement(identifier)('id')
      |    PsiElement(,)(',')
      |    PsiWhiteSpace(' ')
      |    IntegerLiteral
      |      PsiElement(integer)('1')
      |    PsiElement(,)(',')
      |    PsiWhiteSpace(' ')
      |    BooleanLiteral
      |      PsiElement(true)('true')
      |    PsiElement())(')')
      |  PsiWhiteSpace('\n\n')
      |  NamedTuple
      |    PsiElement(()('(')
      |    ScNamedTupleExprComponentImpl(named tuple component)
      |      PsiElement(identifier)('name')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=)('=')
      |      PsiWhiteSpace(' ')
      |      ReferenceExpression: A
      |        PsiElement(identifier)('A')
      |    PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  NamedTuple
      |    PsiElement(()('(')
      |    ScNamedTupleExprComponentImpl(named tuple component)
      |      PsiElement(identifier)('name')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=)('=')
      |      PsiWhiteSpace(' ')
      |      ReferenceExpression: A
      |        PsiElement(identifier)('A')
      |    PsiElement(,)(',')
      |    PsiWhiteSpace(' ')
      |    ScNamedTupleExprComponentImpl(named tuple component)
      |      PsiElement(identifier)('age')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=)('=')
      |      PsiWhiteSpace(' ')
      |      ReferenceExpression: B
      |        PsiElement(identifier)('B')
      |    PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  NamedTuple
      |    PsiElement(()('(')
      |    ScNamedTupleExprComponentImpl(named tuple component)
      |      PsiElement(identifier)('name')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=)('=')
      |      PsiWhiteSpace(' ')
      |      ReferenceExpression: A
      |        PsiElement(identifier)('A')
      |    PsiElement(,)(',')
      |    PsiWhiteSpace(' ')
      |    ScNamedTupleExprComponentImpl(named tuple component)
      |      PsiElement(identifier)('age')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=)('=')
      |      PsiWhiteSpace(' ')
      |      ReferenceExpression: B
      |        PsiElement(identifier)('B')
      |    PsiElement(,)(',')
      |    PsiWhiteSpace(' ')
      |    ScNamedTupleExprComponentImpl(named tuple component)
      |      PsiElement(identifier)('isMale')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=)('=')
      |      PsiWhiteSpace(' ')
      |      ReferenceExpression: C
      |        PsiElement(identifier)('C')
      |    PsiElement())(')')
      |  PsiWhiteSpace('\n    ')
      |""".stripMargin
  )

  def test_tuple_expr_error_parsing(): Unit = checkTree(
    """
      |(id, name = A, 1)
      |(id = 1, name =, = 1, =, 1)
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  Tuple
      |    PsiElement(()('(')
      |    ReferenceExpression: id
      |      PsiElement(identifier)('id')
      |    PsiElement(,)(',')
      |    PsiWhiteSpace(' ')
      |    AssignStatement
      |      ReferenceExpression: name
      |        PsiElement(identifier)('name')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=)('=')
      |      PsiWhiteSpace(' ')
      |      ReferenceExpression: A
      |        PsiElement(identifier)('A')
      |    PsiElement(,)(',')
      |    PsiWhiteSpace(' ')
      |    IntegerLiteral
      |      PsiElement(integer)('1')
      |    PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  NamedTuple
      |    PsiElement(()('(')
      |    ScNamedTupleExprComponentImpl(named tuple component)
      |      PsiElement(identifier)('id')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=)('=')
      |      PsiWhiteSpace(' ')
      |      IntegerLiteral
      |        PsiElement(integer)('1')
      |    PsiElement(,)(',')
      |    PsiWhiteSpace(' ')
      |    ScNamedTupleExprComponentImpl(named tuple component)
      |      PsiElement(identifier)('name')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=)('=')
      |      PsiErrorElement:Expression expected
      |        <empty list>
      |    PsiElement(,)(',')
      |    PsiWhiteSpace(' ')
      |    ScNamedTupleExprComponentImpl(named tuple component)
      |      PsiErrorElement:Identifier expected
      |        <empty list>
      |      PsiElement(=)('=')
      |      PsiWhiteSpace(' ')
      |      IntegerLiteral
      |        PsiElement(integer)('1')
      |    PsiElement(,)(',')
      |    PsiErrorElement:Identifier expected
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiErrorElement:Expression expected
      |      <empty list>
      |    PsiElement(,)(',')
      |    PsiWhiteSpace(' ')
      |    ScNamedTupleExprComponentImpl(named tuple component)
      |      PsiErrorElement:Identifier expected
      |        <empty list>
      |      IntegerLiteral
      |        PsiElement(integer)('1')
      |    PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_tuple_expr_trailing_comma_on_new_line(): Unit = checkTree(
    """
      |(
      |  Int,
      |)
      |(
      |  Int,
      |  String,
      |)
      |(
      |  Int,
      |  String,
      |  Boolean,
      |)
      |
      |(
      |  name = String,
      |)
      |(
      |  name = String,
      |  age = Int,
      |)
      |(name = String, age = Int, isMale = Boolean,
      |)
      |
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ExpressionInParenthesis
      |    PsiElement(()('(')
      |    PsiWhiteSpace('\n  ')
      |    ReferenceExpression: Int
      |      PsiElement(identifier)('Int')
      |    PsiElement(,)(',')
      |    PsiWhiteSpace('\n')
      |    PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  Tuple
      |    PsiElement(()('(')
      |    PsiWhiteSpace('\n  ')
      |    ReferenceExpression: Int
      |      PsiElement(identifier)('Int')
      |    PsiElement(,)(',')
      |    PsiWhiteSpace('\n  ')
      |    ReferenceExpression: String
      |      PsiElement(identifier)('String')
      |    PsiElement(,)(',')
      |    PsiWhiteSpace('\n')
      |    PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  Tuple
      |    PsiElement(()('(')
      |    PsiWhiteSpace('\n  ')
      |    ReferenceExpression: Int
      |      PsiElement(identifier)('Int')
      |    PsiElement(,)(',')
      |    PsiWhiteSpace('\n  ')
      |    ReferenceExpression: String
      |      PsiElement(identifier)('String')
      |    PsiElement(,)(',')
      |    PsiWhiteSpace('\n  ')
      |    ReferenceExpression: Boolean
      |      PsiElement(identifier)('Boolean')
      |    PsiElement(,)(',')
      |    PsiWhiteSpace('\n')
      |    PsiElement())(')')
      |  PsiWhiteSpace('\n\n')
      |  NamedTuple
      |    PsiElement(()('(')
      |    PsiWhiteSpace('\n  ')
      |    ScNamedTupleExprComponentImpl(named tuple component)
      |      PsiElement(identifier)('name')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=)('=')
      |      PsiWhiteSpace(' ')
      |      ReferenceExpression: String
      |        PsiElement(identifier)('String')
      |    PsiElement(,)(',')
      |    PsiWhiteSpace('\n')
      |    PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  NamedTuple
      |    PsiElement(()('(')
      |    PsiWhiteSpace('\n  ')
      |    ScNamedTupleExprComponentImpl(named tuple component)
      |      PsiElement(identifier)('name')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=)('=')
      |      PsiWhiteSpace(' ')
      |      ReferenceExpression: String
      |        PsiElement(identifier)('String')
      |    PsiElement(,)(',')
      |    PsiWhiteSpace('\n  ')
      |    ScNamedTupleExprComponentImpl(named tuple component)
      |      PsiElement(identifier)('age')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=)('=')
      |      PsiWhiteSpace(' ')
      |      ReferenceExpression: Int
      |        PsiElement(identifier)('Int')
      |    PsiElement(,)(',')
      |    PsiWhiteSpace('\n')
      |    PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  NamedTuple
      |    PsiElement(()('(')
      |    ScNamedTupleExprComponentImpl(named tuple component)
      |      PsiElement(identifier)('name')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=)('=')
      |      PsiWhiteSpace(' ')
      |      ReferenceExpression: String
      |        PsiElement(identifier)('String')
      |    PsiElement(,)(',')
      |    PsiWhiteSpace(' ')
      |    ScNamedTupleExprComponentImpl(named tuple component)
      |      PsiElement(identifier)('age')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=)('=')
      |      PsiWhiteSpace(' ')
      |      ReferenceExpression: Int
      |        PsiElement(identifier)('Int')
      |    PsiElement(,)(',')
      |    PsiWhiteSpace(' ')
      |    ScNamedTupleExprComponentImpl(named tuple component)
      |      PsiElement(identifier)('isMale')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=)('=')
      |      PsiWhiteSpace(' ')
      |      ReferenceExpression: Boolean
      |        PsiElement(identifier)('Boolean')
      |    PsiElement(,)(',')
      |    PsiWhiteSpace('\n')
      |    PsiElement())(')')
      |  PsiWhiteSpace('\n\n')
      |""".stripMargin,
  )

  def test_tuple_expr_trailing_comma_on_same_line(): Unit = checkTree(
    """
      |(Int,)
      |(Int, String,)
      |
      |(name = String,)
      |(name = String, age = Int,)
      |
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  Tuple
      |    PsiElement(()('(')
      |    ReferenceExpression: Int
      |      PsiElement(identifier)('Int')
      |    PsiElement(,)(',')
      |    PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  Tuple
      |    PsiElement(()('(')
      |    ReferenceExpression: Int
      |      PsiElement(identifier)('Int')
      |    PsiElement(,)(',')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: String
      |      PsiElement(identifier)('String')
      |    PsiElement(,)(',')
      |    PsiElement())(')')
      |  PsiWhiteSpace('\n\n')
      |  NamedTuple
      |    PsiElement(()('(')
      |    ScNamedTupleExprComponentImpl(named tuple component)
      |      PsiElement(identifier)('name')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=)('=')
      |      PsiWhiteSpace(' ')
      |      ReferenceExpression: String
      |        PsiElement(identifier)('String')
      |    PsiElement(,)(',')
      |    PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  NamedTuple
      |    PsiElement(()('(')
      |    ScNamedTupleExprComponentImpl(named tuple component)
      |      PsiElement(identifier)('name')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=)('=')
      |      PsiWhiteSpace(' ')
      |      ReferenceExpression: String
      |        PsiElement(identifier)('String')
      |    PsiElement(,)(',')
      |    PsiWhiteSpace(' ')
      |    ScNamedTupleExprComponentImpl(named tuple component)
      |      PsiElement(identifier)('age')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=)('=')
      |      PsiWhiteSpace(' ')
      |      ReferenceExpression: Int
      |        PsiElement(identifier)('Int')
      |    PsiElement(,)(',')
      |    PsiElement())(')')
      |  PsiWhiteSpace('\n\n')
      |""".stripMargin
  )

  def test_tuple_pattern_parsing(): Unit = checkTree(
    """
      |??? match {
      |  case (name) =>
      |  case (name, 3) =>
      |  case (name, 3, _) =>
      |
      |  case (name = _) => ???
      |  case (name = name, age = 3) => ???
      |  case (name = name, age = 3, isMale = _) => ???
      |}
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  MatchStatement
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |    PsiWhiteSpace(' ')
      |    PsiElement(match)('match')
      |    PsiWhiteSpace(' ')
      |    PsiElement({)('{')
      |    PsiWhiteSpace('\n  ')
      |    CaseClauses
      |      CaseClause
      |        PsiElement(case)('case')
      |        PsiWhiteSpace(' ')
      |        PatternInParenthesis
      |          PsiElement(()('(')
      |          ReferencePattern: name
      |            PsiElement(identifier)('name')
      |          PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        BlockOfExpressions
      |          <empty list>
      |      PsiWhiteSpace('\n  ')
      |      CaseClause
      |        PsiElement(case)('case')
      |        PsiWhiteSpace(' ')
      |        TuplePattern
      |          PsiElement(()('(')
      |          ArgumentPatterns
      |            ReferencePattern: name
      |              PsiElement(identifier)('name')
      |            PsiElement(,)(',')
      |            PsiWhiteSpace(' ')
      |            LiteralPattern
      |              IntegerLiteral
      |                PsiElement(integer)('3')
      |          PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        BlockOfExpressions
      |          <empty list>
      |      PsiWhiteSpace('\n  ')
      |      CaseClause
      |        PsiElement(case)('case')
      |        PsiWhiteSpace(' ')
      |        TuplePattern
      |          PsiElement(()('(')
      |          ArgumentPatterns
      |            ReferencePattern: name
      |              PsiElement(identifier)('name')
      |            PsiElement(,)(',')
      |            PsiWhiteSpace(' ')
      |            LiteralPattern
      |              IntegerLiteral
      |                PsiElement(integer)('3')
      |            PsiElement(,)(',')
      |            PsiWhiteSpace(' ')
      |            WildcardPattern
      |              PsiElement(_)('_')
      |          PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        BlockOfExpressions
      |          <empty list>
      |      PsiWhiteSpace('\n\n  ')
      |      CaseClause
      |        PsiElement(case)('case')
      |        PsiWhiteSpace(' ')
      |        NamedTuplePattern
      |          PsiElement(()('(')
      |          ScNamedTuplePatternComponentImpl(named tuple pattern component)
      |            PsiElement(identifier)('name')
      |            PsiWhiteSpace(' ')
      |            PsiElement(=)('=')
      |            PsiWhiteSpace(' ')
      |            WildcardPattern
      |              PsiElement(_)('_')
      |          PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        PsiWhiteSpace(' ')
      |        BlockOfExpressions
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')
      |      PsiWhiteSpace('\n  ')
      |      CaseClause
      |        PsiElement(case)('case')
      |        PsiWhiteSpace(' ')
      |        NamedTuplePattern
      |          PsiElement(()('(')
      |          ScNamedTuplePatternComponentImpl(named tuple pattern component)
      |            PsiElement(identifier)('name')
      |            PsiWhiteSpace(' ')
      |            PsiElement(=)('=')
      |            PsiWhiteSpace(' ')
      |            ReferencePattern: name
      |              PsiElement(identifier)('name')
      |          PsiElement(,)(',')
      |          PsiWhiteSpace(' ')
      |          ScNamedTuplePatternComponentImpl(named tuple pattern component)
      |            PsiElement(identifier)('age')
      |            PsiWhiteSpace(' ')
      |            PsiElement(=)('=')
      |            PsiWhiteSpace(' ')
      |            LiteralPattern
      |              IntegerLiteral
      |                PsiElement(integer)('3')
      |          PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        PsiWhiteSpace(' ')
      |        BlockOfExpressions
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')
      |      PsiWhiteSpace('\n  ')
      |      CaseClause
      |        PsiElement(case)('case')
      |        PsiWhiteSpace(' ')
      |        NamedTuplePattern
      |          PsiElement(()('(')
      |          ScNamedTuplePatternComponentImpl(named tuple pattern component)
      |            PsiElement(identifier)('name')
      |            PsiWhiteSpace(' ')
      |            PsiElement(=)('=')
      |            PsiWhiteSpace(' ')
      |            ReferencePattern: name
      |              PsiElement(identifier)('name')
      |          PsiElement(,)(',')
      |          PsiWhiteSpace(' ')
      |          ScNamedTuplePatternComponentImpl(named tuple pattern component)
      |            PsiElement(identifier)('age')
      |            PsiWhiteSpace(' ')
      |            PsiElement(=)('=')
      |            PsiWhiteSpace(' ')
      |            LiteralPattern
      |              IntegerLiteral
      |                PsiElement(integer)('3')
      |          PsiElement(,)(',')
      |          PsiWhiteSpace(' ')
      |          ScNamedTuplePatternComponentImpl(named tuple pattern component)
      |            PsiElement(identifier)('isMale')
      |            PsiWhiteSpace(' ')
      |            PsiElement(=)('=')
      |            PsiWhiteSpace(' ')
      |            WildcardPattern
      |              PsiElement(_)('_')
      |          PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        PsiWhiteSpace(' ')
      |        BlockOfExpressions
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')
      |    PsiWhiteSpace('\n')
      |    PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )


  def test_tuple_pattern_trailing_comma_on_new_line(): Unit = checkTree(
    """
      |val (
      |  name,
      |) = ???
      |val (
      |  name,
      |  1,
      |)
      |
      |val (
      |  name = n,
      |) = ???
      |val (
      |  name = n,
      |  age = 1,
      |) = ???
      |val (name = n, age = 1, isMale = _,
      |) = ???
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScPatternDefinition: name
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      TuplePattern
      |        PsiElement(()('(')
      |        PsiWhiteSpace('\n  ')
      |        ArgumentPatterns
      |          ReferencePattern: name
      |            PsiElement(identifier)('name')
      |          PsiElement(,)(',')
      |        PsiWhiteSpace('\n')
      |        PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n')
      |  PsiElement(val)('val')
      |  PsiErrorElement:Identifier expected
      |    <empty list>
      |  PsiWhiteSpace(' ')
      |  Tuple
      |    PsiElement(()('(')
      |    PsiWhiteSpace('\n  ')
      |    ReferenceExpression: name
      |      PsiElement(identifier)('name')
      |    PsiElement(,)(',')
      |    PsiWhiteSpace('\n  ')
      |    IntegerLiteral
      |      PsiElement(integer)('1')
      |    PsiElement(,)(',')
      |    PsiWhiteSpace('\n')
      |    PsiElement())(')')
      |  PsiWhiteSpace('\n\n')
      |  ScPatternDefinition: n
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      NamedTuplePattern
      |        PsiElement(()('(')
      |        PsiWhiteSpace('\n  ')
      |        ScNamedTuplePatternComponentImpl(named tuple pattern component)
      |          PsiElement(identifier)('name')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          ReferencePattern: n
      |            PsiElement(identifier)('n')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace('\n')
      |        PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n')
      |  ScPatternDefinition: n
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      NamedTuplePattern
      |        PsiElement(()('(')
      |        PsiWhiteSpace('\n  ')
      |        ScNamedTuplePatternComponentImpl(named tuple pattern component)
      |          PsiElement(identifier)('name')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          ReferencePattern: n
      |            PsiElement(identifier)('n')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace('\n  ')
      |        ScNamedTuplePatternComponentImpl(named tuple pattern component)
      |          PsiElement(identifier)('age')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          LiteralPattern
      |            IntegerLiteral
      |              PsiElement(integer)('1')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace('\n')
      |        PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n')
      |  ScPatternDefinition: n
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      NamedTuplePattern
      |        PsiElement(()('(')
      |        ScNamedTuplePatternComponentImpl(named tuple pattern component)
      |          PsiElement(identifier)('name')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          ReferencePattern: n
      |            PsiElement(identifier)('n')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        ScNamedTuplePatternComponentImpl(named tuple pattern component)
      |          PsiElement(identifier)('age')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          LiteralPattern
      |            IntegerLiteral
      |              PsiElement(integer)('1')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        ScNamedTuplePatternComponentImpl(named tuple pattern component)
      |          PsiElement(identifier)('isMale')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          WildcardPattern
      |            PsiElement(_)('_')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace('\n')
      |        PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )


  def test_tuple_pattern_trailing_comma_on_same_line(): Unit = checkTree(
    """
      |
      |??? match {
      |  case (name,) =>
      |  case (name, 3,) =>
      |  case (name, 3, _,) =>
      |
      |  case (name = _,) => ???
      |  case (name = name, age = 3,) => ???
      |  case (name = name, age = 3, isMale = _,) => ???
      |}
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n\n')
      |  MatchStatement
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |    PsiWhiteSpace(' ')
      |    PsiElement(match)('match')
      |    PsiWhiteSpace(' ')
      |    PsiElement({)('{')
      |    PsiWhiteSpace('\n  ')
      |    CaseClauses
      |      CaseClause
      |        PsiElement(case)('case')
      |        PsiWhiteSpace(' ')
      |        TuplePattern
      |          PsiElement(()('(')
      |          ArgumentPatterns
      |            ReferencePattern: name
      |              PsiElement(identifier)('name')
      |            PsiElement(,)(',')
      |          PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        BlockOfExpressions
      |          <empty list>
      |      PsiWhiteSpace('\n  ')
      |      CaseClause
      |        PsiElement(case)('case')
      |        PsiWhiteSpace(' ')
      |        TuplePattern
      |          PsiElement(()('(')
      |          ArgumentPatterns
      |            ReferencePattern: name
      |              PsiElement(identifier)('name')
      |            PsiElement(,)(',')
      |            PsiWhiteSpace(' ')
      |            LiteralPattern
      |              IntegerLiteral
      |                PsiElement(integer)('3')
      |            PsiElement(,)(',')
      |          PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        BlockOfExpressions
      |          <empty list>
      |      PsiWhiteSpace('\n  ')
      |      CaseClause
      |        PsiElement(case)('case')
      |        PsiWhiteSpace(' ')
      |        TuplePattern
      |          PsiElement(()('(')
      |          ArgumentPatterns
      |            ReferencePattern: name
      |              PsiElement(identifier)('name')
      |            PsiElement(,)(',')
      |            PsiWhiteSpace(' ')
      |            LiteralPattern
      |              IntegerLiteral
      |                PsiElement(integer)('3')
      |            PsiElement(,)(',')
      |            PsiWhiteSpace(' ')
      |            WildcardPattern
      |              PsiElement(_)('_')
      |            PsiElement(,)(',')
      |          PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        BlockOfExpressions
      |          <empty list>
      |      PsiWhiteSpace('\n\n  ')
      |      CaseClause
      |        PsiElement(case)('case')
      |        PsiWhiteSpace(' ')
      |        NamedTuplePattern
      |          PsiElement(()('(')
      |          ScNamedTuplePatternComponentImpl(named tuple pattern component)
      |            PsiElement(identifier)('name')
      |            PsiWhiteSpace(' ')
      |            PsiElement(=)('=')
      |            PsiWhiteSpace(' ')
      |            WildcardPattern
      |              PsiElement(_)('_')
      |          PsiElement(,)(',')
      |          PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        PsiWhiteSpace(' ')
      |        BlockOfExpressions
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')
      |      PsiWhiteSpace('\n  ')
      |      CaseClause
      |        PsiElement(case)('case')
      |        PsiWhiteSpace(' ')
      |        NamedTuplePattern
      |          PsiElement(()('(')
      |          ScNamedTuplePatternComponentImpl(named tuple pattern component)
      |            PsiElement(identifier)('name')
      |            PsiWhiteSpace(' ')
      |            PsiElement(=)('=')
      |            PsiWhiteSpace(' ')
      |            ReferencePattern: name
      |              PsiElement(identifier)('name')
      |          PsiElement(,)(',')
      |          PsiWhiteSpace(' ')
      |          ScNamedTuplePatternComponentImpl(named tuple pattern component)
      |            PsiElement(identifier)('age')
      |            PsiWhiteSpace(' ')
      |            PsiElement(=)('=')
      |            PsiWhiteSpace(' ')
      |            LiteralPattern
      |              IntegerLiteral
      |                PsiElement(integer)('3')
      |          PsiElement(,)(',')
      |          PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        PsiWhiteSpace(' ')
      |        BlockOfExpressions
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')
      |      PsiWhiteSpace('\n  ')
      |      CaseClause
      |        PsiElement(case)('case')
      |        PsiWhiteSpace(' ')
      |        NamedTuplePattern
      |          PsiElement(()('(')
      |          ScNamedTuplePatternComponentImpl(named tuple pattern component)
      |            PsiElement(identifier)('name')
      |            PsiWhiteSpace(' ')
      |            PsiElement(=)('=')
      |            PsiWhiteSpace(' ')
      |            ReferencePattern: name
      |              PsiElement(identifier)('name')
      |          PsiElement(,)(',')
      |          PsiWhiteSpace(' ')
      |          ScNamedTuplePatternComponentImpl(named tuple pattern component)
      |            PsiElement(identifier)('age')
      |            PsiWhiteSpace(' ')
      |            PsiElement(=)('=')
      |            PsiWhiteSpace(' ')
      |            LiteralPattern
      |              IntegerLiteral
      |                PsiElement(integer)('3')
      |          PsiElement(,)(',')
      |          PsiWhiteSpace(' ')
      |          ScNamedTuplePatternComponentImpl(named tuple pattern component)
      |            PsiElement(identifier)('isMale')
      |            PsiWhiteSpace(' ')
      |            PsiElement(=)('=')
      |            PsiWhiteSpace(' ')
      |            WildcardPattern
      |              PsiElement(_)('_')
      |          PsiElement(,)(',')
      |          PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        PsiWhiteSpace(' ')
      |        BlockOfExpressions
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')
      |    PsiWhiteSpace('\n')
      |    PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )
}
