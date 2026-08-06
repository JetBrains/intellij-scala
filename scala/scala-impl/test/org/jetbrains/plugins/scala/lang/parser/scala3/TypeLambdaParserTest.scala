package org.jetbrains.plugins.scala.lang.parser.scala3

class TypeLambdaParserTest extends SimpleScala3ParserTestBase {
  def testSimple(): Unit = checkParseErrors(
    """
      |trait Monad[F[_]]
      |def tupleMonad[A]: Monad[[B] =>> (A, B)] = ???
      |""".stripMargin
  )

  def testCurried(): Unit = checkParseErrors(
    "type TL = [X] =>> [Y] =>> (X, Y)"
  )

  def testIllegalVariance(): Unit = checkParseErrors(
    """
      |type TL = [X, [[Err(Variance annotation is not allowed here)]]-Y] =>> Map[Y, X]
      |""".stripMargin
  )

  def testBounds(): Unit = checkParseErrors(
    """
      |trait T[F[_]]
      |type TL = [F[_] <: Seq[_]] =>> T[F]
      |""".stripMargin
  )

  def testAllTogether(): Unit = checkParseErrors(
    "type TL = [A, F[A] >: Option[A] <: List[A]] =>> F[A] => A"
  )

  def testIncompleteParameterList(): Unit = checkTree(
    "type TL = [T]",
    """
      |ScalaFile
      |  ScTypeAliasDefinition: TL
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('TL')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    PolymorphicFunctionType: [T]
      |      TypeParameterClause
      |        PsiElement([)('[')
      |        TypeParameter: T
      |          PsiElement(identifier)('T')
      |        PsiElement(])(']')
      |      PsiErrorElement:'=>>' expected
      |        <empty list>
      |""".stripMargin
  )
}

