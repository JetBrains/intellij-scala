package org.jetbrains.plugins.scala.lang.parser.scala3

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.project.ScalaFeatures

class TypeParserTest extends SimpleScala3ParserTestBase {
  override def scalaCodeParsingFeatures: ScalaFeatures = ScalaFeatures.onlyByVersion(ScalaVersion.Latest.Scala_3_Next_RC)

  // SCL-21210
  def testHK(): Unit = checkTree(
    """
      |type A = { }#l
      |type B = HKT[{ type l[a] = Option[a] }#l]
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
      |    TypeProjection: { }#l
      |      CompoundType: { }
      |        Refinement
      |          PsiElement({)('{')
      |          PsiWhiteSpace(' ')
      |          PsiElement(})('}')
      |      PsiElement(#)('#')
      |      PsiElement(identifier)('l')
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
      |    ParametrizedType: HKT[{ type l[a] = Option[a] }#l]
      |      SimpleType: HKT
      |        CodeReferenceElement: HKT
      |          PsiElement(identifier)('HKT')
      |      TypeArgumentsList
      |        PsiElement([)('[')
      |        TypeArgument
      |          TypeProjection: { type l[a] = Option[a] }#l
      |            CompoundType: { type l[a] = Option[a] }
      |              Refinement
      |                PsiElement({)('{')
      |                PsiWhiteSpace(' ')
      |                ScTypeAliasDefinition: l
      |                  AnnotationsList
      |                    <empty list>
      |                  Modifiers
      |                    <empty list>
      |                  PsiElement(type)('type')
      |                  PsiWhiteSpace(' ')
      |                  PsiElement(identifier)('l')
      |                  TypeParameterClause
      |                    PsiElement([)('[')
      |                    TypeParameter: a
      |                      PsiElement(identifier)('a')
      |                    PsiElement(])(']')
      |                  PsiWhiteSpace(' ')
      |                  PsiElement(=)('=')
      |                  PsiWhiteSpace(' ')
      |                  ParametrizedType: Option[a]
      |                    SimpleType: Option
      |                      CodeReferenceElement: Option
      |                        PsiElement(identifier)('Option')
      |                    TypeArgumentsList
      |                      PsiElement([)('[')
      |                      TypeArgument
      |                        SimpleType: a
      |                          CodeReferenceElement: a
      |                            PsiElement(identifier)('a')
      |                      PsiElement(])(']')
      |                PsiWhiteSpace(' ')
      |                PsiElement(})('}')
      |            PsiElement(#)('#')
      |            PsiElement(identifier)('l')
      |        PsiElement(])(']')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_named_tuple(): Unit = checkTree(
    """
      |type Person = (name: String, age: Int)
      |val Bob: Person = (name = "Bob", age = 33)
      |Bob match
      |  case (name = x, age = y) => Test
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScTypeAliasDefinition: Person
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Person')
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
      |  ScPatternDefinition: Bob
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: Bob
      |        PsiElement(identifier)('Bob')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    SimpleType: Person
      |      CodeReferenceElement: Person
      |        PsiElement(identifier)('Person')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    NamedTuple
      |      PsiElement(()('(')
      |      ScNamedTupleExprComponentImpl(named tuple component)
      |        PsiElement(identifier)('name')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=)('=')
      |        PsiWhiteSpace(' ')
      |        StringLiteral
      |          PsiElement(string content)('"Bob"')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      ScNamedTupleExprComponentImpl(named tuple component)
      |        PsiElement(identifier)('age')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=)('=')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('33')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  MatchStatement
      |    ReferenceExpression: Bob
      |      PsiElement(identifier)('Bob')
      |    PsiWhiteSpace(' ')
      |    PsiElement(match)('match')
      |    PsiWhiteSpace('\n  ')
      |    CaseClauses
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
      |            ReferencePattern: x
      |              PsiElement(identifier)('x')
      |          PsiElement(,)(',')
      |          PsiWhiteSpace(' ')
      |          ScNamedTuplePatternComponentImpl(named tuple pattern component)
      |            PsiElement(identifier)('age')
      |            PsiWhiteSpace(' ')
      |            PsiElement(=)('=')
      |            PsiWhiteSpace(' ')
      |            ReferencePattern: y
      |              PsiElement(identifier)('y')
      |          PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        PsiWhiteSpace(' ')
      |        BlockOfExpressions
      |          ReferenceExpression: Test
      |            PsiElement(identifier)('Test')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_incomplete_type_func(): Unit = checkTree(
    """
      |[A]
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  PolyFunctionExpression
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: A
      |        PsiElement(identifier)('A')
      |      PsiElement(])(']')
      |    PsiErrorElement:'=>' expected
      |      <empty list>
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_opaque_abstract_type_member(): Unit = checkTree(
    """
      |opaque type A
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScTypeAliasDeclaration: A
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      PsiElement(opaque)('opaque')
      |    PsiWhiteSpace(' ')
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('A')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )
}
