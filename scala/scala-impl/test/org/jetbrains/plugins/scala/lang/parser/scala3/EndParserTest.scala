package org.jetbrains.plugins.scala.lang.parser.scala3

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.AssertionMatchers
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScEnd
import org.jetbrains.plugins.scala.util.PsiSelectionUtil

class EndParserTest extends SimpleScala3ParserTestBase with PsiSelectionUtil with AssertionMatchers {
  def doTest(code: String, expectedType: IElementType): Unit = {
    val file = checkParseErrors(code.stripMargin)

    val endElement = searchElement[ScEnd](file)

    val designator = endElement.tag
    designator shouldNotBe null

    val designatorType = endElement.tag.getNode.getElementType
    designatorType shouldBe expectedType
  }

  def test_end_if(): Unit = doTest(
    """
      |if (boolean)
      |  stmt1
      |  stmt2
      |else
      |  stmt3
      |  stmt4
      |end if
      |""".stripMargin,
    expectedType = ScalaTokenTypes.kIF
  )


  def test_one_expr_end_if(): Unit = checkTree(
    """
      |if (boolean)
      |  stmt
      |end if
      |""".stripMargin,
    """ScalaFile
      |  PsiWhiteSpace('\n')
      |  IfStatement
      |    PsiElement(if)('if')
      |    PsiWhiteSpace(' ')
      |    PsiElement(()('(')
      |    ReferenceExpression: boolean
      |      PsiElement(identifier)('boolean')
      |    PsiElement())(')')
      |    PsiWhiteSpace('\n  ')
      |    ReferenceExpression: stmt
      |      PsiElement(identifier)('stmt')
      |    PsiWhiteSpace('\n')
      |    End: if
      |      PsiElement(end)('end')
      |      PsiWhiteSpace(' ')
      |      PsiElement(if)('if')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_end_on_nested_if(): Unit = checkTree(
    """
      |if (boolean)
      |  if (boolean)
      |    stmt
      |end if
      |""".stripMargin,
    """ScalaFile
      |  PsiWhiteSpace('\n')
      |  IfStatement
      |    PsiElement(if)('if')
      |    PsiWhiteSpace(' ')
      |    PsiElement(()('(')
      |    ReferenceExpression: boolean
      |      PsiElement(identifier)('boolean')
      |    PsiElement())(')')
      |    PsiWhiteSpace('\n  ')
      |    IfStatement
      |      PsiElement(if)('if')
      |      PsiWhiteSpace(' ')
      |      PsiElement(()('(')
      |      ReferenceExpression: boolean
      |        PsiElement(identifier)('boolean')
      |      PsiElement())(')')
      |      PsiWhiteSpace('\n    ')
      |      ReferenceExpression: stmt
      |        PsiElement(identifier)('stmt')
      |    PsiWhiteSpace('\n')
      |    End: if
      |      PsiElement(end)('end')
      |      PsiWhiteSpace(' ')
      |      PsiElement(if)('if')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_end_while(): Unit = doTest(
    """
      |while
      |  stmt1
      |  stmt2
      |do
      |  stmt3
      |  stmt4
      |end while
      |""".stripMargin,
    expectedType = ScalaTokenTypes.kWHILE
  )

  def test_end_for(): Unit = doTest(
    """
      |for
      |  x <- xs
      |do
      |  stmt1
      |end for
      |""".stripMargin,
    expectedType = ScalaTokenTypes.kFOR
  )

  def test_end_try_finally(): Unit = doTest(
    """
      |try
      |  stmt1
      |  stmt2
      |finally
      |  stmt3
      |  stmt4
      |end try
      |""".stripMargin,
    expectedType = ScalaTokenTypes.kTRY
  )

  def test_end_try_catch(): Unit = doTest(
    """
      |try
      |  stmt1
      |  stmt2
      |catch
      |case a => stmt3
      |case b => stmt4
      |end try
      |""".stripMargin,
    expectedType = ScalaTokenTypes.kTRY
  )

  def test_end_match(): Unit = doTest(
    """
      |something match
      |case a =>  stmt1
      |case _ => stmt2
      |end match
      |""".stripMargin,
    expectedType = ScalaTokenTypes.kMATCH
  )

  def test_end_new(): Unit = doTest(
    """
      |new:
      |  stmt1
      |  stmt2
      |end new
      |""".stripMargin,
    expectedType = ScalaTokenType.NewKeyword
  )

  def test_end_class(): Unit = doTest(
    """
      |class A:
      |  stmt1
      |  stmt2
      |end A
      |""".stripMargin,
    expectedType = ScalaTokenTypes.tIDENTIFIER
  )

  def test_end_method(): Unit = doTest(
    """
      |def test() =
      |  stmt1
      |  stmt2
      |end test
      |""".stripMargin,
    expectedType = ScalaTokenTypes.tIDENTIFIER
  )

  def test_empty_trait_end(): Unit = doTest(
    """
      |trait A:
      |end A
      |""".stripMargin,
    expectedType = ScalaTokenTypes.tIDENTIFIER
  )

  def test_empty_package_end(): Unit = doTest(
    """
      |package A:
      |end A
      |""".stripMargin,
    expectedType = ScalaTokenTypes.tIDENTIFIER
  )

  def test_package_end(): Unit = checkTree(
    """package foo:
      |  package bar:
      |    object A:
      |      def foo = 1
      |  end bar
      |end foo
      |package baz:
      |  object B:
      |    def f = foo.bar.A.foo
      |end baz
      |""".stripMargin,
    """ScalaFile
      |  ScPackaging
      |    PsiElement(package)('package')
      |    PsiWhiteSpace(' ')
      |    CodeReferenceElement: foo
      |      PsiElement(identifier)('foo')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace('\n  ')
      |    ScPackaging
      |      PsiElement(package)('package')
      |      PsiWhiteSpace(' ')
      |      CodeReferenceElement: bar
      |        PsiElement(identifier)('bar')
      |      PsiElement(:)(':')
      |      PsiWhiteSpace('\n    ')
      |      ScObject: A
      |        AnnotationsList
      |          <empty list>
      |        Modifiers
      |          <empty list>
      |        PsiElement(object)('object')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('A')
      |        ExtendsBlock
      |          ScTemplateBody
      |            PsiElement(:)(':')
      |            PsiWhiteSpace('\n      ')
      |            ScFunctionDefinition: foo
      |              AnnotationsList
      |                <empty list>
      |              Modifiers
      |                <empty list>
      |              PsiElement(def)('def')
      |              PsiWhiteSpace(' ')
      |              PsiElement(identifier)('foo')
      |              Parameters
      |                <empty list>
      |              PsiWhiteSpace(' ')
      |              PsiElement(=)('=')
      |              PsiWhiteSpace(' ')
      |              IntegerLiteral
      |                PsiElement(integer)('1')
      |      PsiWhiteSpace('\n  ')
      |      End: bar
      |        PsiElement(end)('end')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('bar')
      |    PsiWhiteSpace('\n')
      |    End: foo
      |      PsiElement(end)('end')
      |      PsiWhiteSpace(' ')
      |      PsiElement(identifier)('foo')
      |  PsiWhiteSpace('\n')
      |  ScPackaging
      |    PsiElement(package)('package')
      |    PsiWhiteSpace(' ')
      |    CodeReferenceElement: baz
      |      PsiElement(identifier)('baz')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace('\n  ')
      |    ScObject: B
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      PsiElement(object)('object')
      |      PsiWhiteSpace(' ')
      |      PsiElement(identifier)('B')
      |      ExtendsBlock
      |        ScTemplateBody
      |          PsiElement(:)(':')
      |          PsiWhiteSpace('\n    ')
      |          ScFunctionDefinition: f
      |            AnnotationsList
      |              <empty list>
      |            Modifiers
      |              <empty list>
      |            PsiElement(def)('def')
      |            PsiWhiteSpace(' ')
      |            PsiElement(identifier)('f')
      |            Parameters
      |              <empty list>
      |            PsiWhiteSpace(' ')
      |            PsiElement(=)('=')
      |            PsiWhiteSpace(' ')
      |            ReferenceExpression: foo.bar.A.foo
      |              ReferenceExpression: foo.bar.A
      |                ReferenceExpression: foo.bar
      |                  ReferenceExpression: foo
      |                    PsiElement(identifier)('foo')
      |                  PsiElement(.)('.')
      |                  PsiElement(identifier)('bar')
      |                PsiElement(.)('.')
      |                PsiElement(identifier)('A')
      |              PsiElement(.)('.')
      |              PsiElement(identifier)('foo')
      |    PsiWhiteSpace('\n')
      |    End: baz
      |      PsiElement(end)('end')
      |      PsiWhiteSpace(' ')
      |      PsiElement(identifier)('baz')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  // todo: add tests for extensions and given
}
