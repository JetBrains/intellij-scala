package org.jetbrains.plugins.scala.lang.scaladoc.reflinks.parser

import com.intellij.lang.{LanguageParserDefinitions, PsiBuilderFactory}
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.DebugUtil.psiToString
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.scaladoc.reflinks.ScalaDocRefLinkLanguage
import org.junit.Assert.assertEquals

class ScalaDocRefLinkParserTest extends SimpleTestCase {

  private def parseRefLink(text: String): PsiElement = {
    val parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(ScalaDocRefLinkLanguage.INSTANCE)
    val lexer = parserDefinition.createLexer(ctx.getProject)
    val parser = parserDefinition.createParser(ctx.getProject)
    val builder = PsiBuilderFactory.getInstance().createBuilder(parserDefinition, lexer, text)

    val node = parser.parse(parserDefinition.getFileNodeType, builder)
    node.getPsi
  }

  private def checkTree(text: String, expectedTree: String): Unit = {
    val link = parseRefLink(text)
    val resultTree = psiToString(link, true)
    assertEquals(expectedTree.trim.withNormalizedSeparator, resultTree.trim)
  }

  def test_simple_identifier(): Unit = checkTree(
    "Foo",
    """
      |ASTWrapperPsiElement(FILE)
      |  ScDocRefQuerySegment('Foo')
      |    PsiElement(identifier)('Foo')
      |""".stripMargin
  )

  def test_qualified_id_with_dot(): Unit = checkTree(
    "scala.collection.List",
    """
      |ASTWrapperPsiElement(FILE)
      |  ScDocRefQuerySegment('List')
      |    ScDocRefQuerySegment('collection')
      |      ScDocRefQuerySegment('scala')
      |        PsiElement(identifier)('scala')
      |      PsiElement(.)('.')
      |      PsiElement(identifier)('collection')
      |    PsiElement(.)('.')
      |    PsiElement(identifier)('List')
      |""".stripMargin
  )

  def test_qualified_id_with_hash(): Unit = checkTree(
    "scala.collection.List#map",
    """
      |ASTWrapperPsiElement(FILE)
      |  ScDocRefQuerySegment('map')
      |    ScDocRefQuerySegment('List')
      |      ScDocRefQuerySegment('collection')
      |        ScDocRefQuerySegment('scala')
      |          PsiElement(identifier)('scala')
      |        PsiElement(.)('.')
      |        PsiElement(identifier)('collection')
      |      PsiElement(.)('.')
      |      PsiElement(identifier)('List')
      |    PsiElement(#)('#')
      |    PsiElement(identifier)('map')
      |""".stripMargin
  )

  def test_strict_member_id(): Unit = checkTree(
    "#toString",
    """
      |ASTWrapperPsiElement(FILE)
      |  ScDocRefStrictMemberIdQuery(toString)
      |    PsiElement(#)('#')
      |    PsiElement(identifier)('toString')
      |""".stripMargin
  )

  def test_strict_member_id_escaped(): Unit = checkTree(
    "#`toString`",
    """
      |ASTWrapperPsiElement(FILE)
      |  ScDocRefStrictMemberIdQuery(`toString`)
      |    PsiElement(#)('#')
      |    PsiElement(identifier)('`toString`')
      |""".stripMargin
  )

  def test_this_reference(): Unit = checkTree(
    "this.foo",
    """
      |ASTWrapperPsiElement(FILE)
      |  ScDocRefQuerySegment('foo')
      |    ScDocRefThisQuery
      |      PsiElement(this)('this')
      |    PsiElement(.)('.')
      |    PsiElement(identifier)('foo')
      |""".stripMargin
  )

  def test_package_reference(): Unit = checkTree(
    "package.Bar",
    """
      |ASTWrapperPsiElement(FILE)
      |  ScDocRefQuerySegment('Bar')
      |    ScPackageQuery
      |      PsiElement(package)('package')
      |    PsiElement(.)('.')
      |    PsiElement(identifier)('Bar')
      |""".stripMargin
  )

  def test_quoted_identifier(): Unit = checkTree(
    "`type`.Foo",
    """
      |ASTWrapperPsiElement(FILE)
      |  ScDocRefQuerySegment('Foo')
      |    ScDocRefQuerySegment('`type`')
      |      PsiElement(identifier)('`type`')
      |    PsiElement(.)('.')
      |    PsiElement(identifier)('Foo')
      |""".stripMargin
  )

  def test_with_type_parameters(): Unit = checkTree(
    "List[Int]",
    """
      |ASTWrapperPsiElement(FILE)
      |  ScDocRefQuerySegment('List')
      |    PsiElement(identifier)('List')
      |  PsiElement([)('[')
      |  PsiElement(identifier)('Int')
      |  PsiElement(])(']')
      |""".stripMargin
  )

  def test_with_method_parameters(): Unit = checkTree(
    "foo(bar)",
    """
      |ASTWrapperPsiElement(FILE)
      |  ScDocRefQuerySegment('foo')
      |    PsiElement(identifier)('foo')
      |  PsiElement(()('(')
      |  PsiElement(identifier)('bar')
      |  PsiElement())(')')
      |""".stripMargin
  )

  def test_complex_qualified_path(): Unit = checkTree(
    "scala.collection.immutable.List#map",
    """
      |ASTWrapperPsiElement(FILE)
      |  ScDocRefQuerySegment('map')
      |    ScDocRefQuerySegment('List')
      |      ScDocRefQuerySegment('immutable')
      |        ScDocRefQuerySegment('collection')
      |          ScDocRefQuerySegment('scala')
      |            PsiElement(identifier)('scala')
      |          PsiElement(.)('.')
      |          PsiElement(identifier)('collection')
      |        PsiElement(.)('.')
      |        PsiElement(identifier)('immutable')
      |      PsiElement(.)('.')
      |      PsiElement(identifier)('List')
      |    PsiElement(#)('#')
      |    PsiElement(identifier)('map')
      |""".stripMargin
  )

  def test_escaped_identifier(): Unit = checkTree(
    "foo\\.bar.baz",
    """
      |ASTWrapperPsiElement(FILE)
      |  ScDocRefQuerySegment('baz')
      |    ScDocRefQuerySegment('foo.bar')
      |      PsiElement(identifier)('foo\.bar')
      |    PsiElement(.)('.')
      |    PsiElement(identifier)('baz')
      |""".stripMargin
  )

  def test_escaped_backlash(): Unit = checkTree(
    "`foo\\bar`",
    """
      |ASTWrapperPsiElement(FILE)
      |  ScDocRefQuerySegment('`foo\bar`')
      |    PsiElement(identifier)('`foo\bar`')
      |""".stripMargin
  )

  def test_dot_after_dot(): Unit = checkTree(
    "foo..bar",
    """
      |ASTWrapperPsiElement(FILE)
      |  ScDocRefQuerySegment('bar')
      |    ScDocRefQuerySegment(<error>)
      |      ScDocRefQuerySegment('foo')
      |        PsiElement(identifier)('foo')
      |      PsiElement(.)('.')
      |      PsiErrorElement:Identifier, 'this', or 'package' expected
      |        <empty list>
      |    PsiElement(.)('.')
      |    PsiElement(identifier)('bar')
      |""".stripMargin
  )

  def test_dot_at_beginning(): Unit = checkTree(
    ".bar",
    """
      |ASTWrapperPsiElement(FILE)
      |  ScDocRefQuerySegment('bar')
      |    ScDocRefQuerySegment(<error>)
      |      PsiErrorElement:Identifier, 'this', or 'package' expected
      |        <empty list>
      |    PsiElement(.)('.')
      |    PsiElement(identifier)('bar')
      |""".stripMargin
  )

  def test_dot_at_end(): Unit = checkTree(
    "foo.",
    """
      |ASTWrapperPsiElement(FILE)
      |  ScDocRefQuerySegment(<error>)
      |    ScDocRefQuerySegment('foo')
      |      PsiElement(identifier)('foo')
      |    PsiElement(.)('.')
      |    PsiErrorElement:Identifier, 'this', or 'package' expected
      |      <empty list>
      |""".stripMargin
  )
}
