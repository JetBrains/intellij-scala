package org.jetbrains.plugins.scala.lang.parser.scala3

class ImportParserTest extends SimpleScala3ParserTestBase {
  def test_simple(): Unit = checkTree(
    """
      |import x.y
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScImportStatement
      |    PsiElement(import)('import')
      |    PsiWhiteSpace(' ')
      |    ImportExpression
      |      CodeReferenceElement: x.y
      |        CodeReferenceElement: x
      |          PsiElement(identifier)('x')
      |        PsiElement(.)('.')
      |        PsiElement(identifier)('y')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_wildcard(): Unit = checkTree(
    """
      |import x.y.*
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScImportStatement
      |    PsiElement(import)('import')
      |    PsiWhiteSpace(' ')
      |    ImportExpression
      |      CodeReferenceElement: x.y
      |        CodeReferenceElement: x
      |          PsiElement(identifier)('x')
      |        PsiElement(.)('.')
      |        PsiElement(identifier)('y')
      |      PsiElement(.)('.')
      |      PsiElement(*)('*')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_given(): Unit = checkTree(
    """
      |import x.y.given Int
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScImportStatement
      |    PsiElement(import)('import')
      |    PsiWhiteSpace(' ')
      |    ImportExpression
      |      CodeReferenceElement: x.y
      |        CodeReferenceElement: x
      |          PsiElement(identifier)('x')
      |        PsiElement(.)('.')
      |        PsiElement(identifier)('y')
      |      PsiElement(.)('.')
      |      PsiElement(given)('given')
      |      PsiWhiteSpace(' ')
      |      SimpleType: Int
      |        CodeReferenceElement: Int
      |          PsiElement(identifier)('Int')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_local_rename(): Unit = checkTree(
    """
      |import x as y
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScImportStatement
      |    PsiElement(import)('import')
      |    PsiWhiteSpace(' ')
      |    ImportExpression
      |      ImportSelectors
      |        ImportSelector
      |          CodeReferenceElement: x
      |            PsiElement(identifier)('x')
      |          PsiWhiteSpace(' ')
      |          PsiElement(as)('as')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('y')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_as(): Unit = checkTree(
    """
      |import x.y as yy
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScImportStatement
      |    PsiElement(import)('import')
      |    PsiWhiteSpace(' ')
      |    ImportExpression
      |      CodeReferenceElement: x
      |        PsiElement(identifier)('x')
      |      PsiElement(.)('.')
      |      ImportSelectors
      |        ImportSelector
      |          CodeReferenceElement: y
      |            PsiElement(identifier)('y')
      |          PsiWhiteSpace(' ')
      |          PsiElement(as)('as')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('yy')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_selector(): Unit = checkTree(
    """
      |import x.y.{a as b, given Int, given Test, *}
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScImportStatement
      |    PsiElement(import)('import')
      |    PsiWhiteSpace(' ')
      |    ImportExpression
      |      CodeReferenceElement: x.y
      |        CodeReferenceElement: x
      |          PsiElement(identifier)('x')
      |        PsiElement(.)('.')
      |        PsiElement(identifier)('y')
      |      PsiElement(.)('.')
      |      ImportSelectors
      |        PsiElement({)('{')
      |        ImportSelector
      |          CodeReferenceElement: a
      |            PsiElement(identifier)('a')
      |          PsiWhiteSpace(' ')
      |          PsiElement(as)('as')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('b')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        ImportSelector
      |          PsiElement(given)('given')
      |          PsiWhiteSpace(' ')
      |          SimpleType: Int
      |            CodeReferenceElement: Int
      |              PsiElement(identifier)('Int')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        ImportSelector
      |          PsiElement(given)('given')
      |          PsiWhiteSpace(' ')
      |          SimpleType: Test
      |            CodeReferenceElement: Test
      |              PsiElement(identifier)('Test')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        ImportSelector
      |          PsiElement(*)('*')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )
}
