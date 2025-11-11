package org.jetbrains.plugins.scala.lang.scaladoc.parser

import com.intellij.psi.impl.DebugUtil.psiToString
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.extensions.{PsiNamedElementExt, StringExt}
import org.jetbrains.plugins.scala.lang.parser.scala3.SimpleScala3ParserTestBase
import org.junit.Assert.assertEquals

class MarkdownScalaDocParserTest extends SimpleScala3ParserTestBase {

  def checkBoth(scala2and3Code: String, expected: String): Unit =
    checkBoth(scala2and3Code, scala2and3Code, expected)

  def checkBoth(scala3Code: String, scala2Code: String, expected: String): Unit = {
    val scala3File = parseScalaFile(scala3Code, ScalaVersion.Latest.Scala_3)
    val scala3Tree = psiToString(scala3File, true).replace(": " + scala3File.name, "")
    assertEquals(expected.trim, scala3Tree.trim.withNormalizedSeparator)

    val scala2File = parseScalaFile(scala2Code, ScalaVersion.Latest.Scala_2)
    val scala2Tree = psiToString(scala2File, true).replace(": " + scala2File.name, "")
    val transformed = transformScala2TreeToScala3Tree(scala2Tree)
    assertEquals(expected.trim, transformed.trim.withNormalizedSeparator)
  }

  def transformScala2TreeToScala3Tree(code: String): String = code
    .replace("ScPsiDocToken(DOC_BOLD_TAG 1)(''''')", "ScPsiDocToken(DOC_BOLD_TAG 1)('**')")
    .replace("ScPsiDocToken(DOC_ITALIC_TAG 2)('''')", "ScPsiDocToken(DOC_ITALIC_TAG 2)('*')")

  def test_basic_markdown(): Unit = checkBoth(
    """
      |/**
      | * A basic textual comment
      | */
      |""".stripMargin,
    """ScalaFile
      |  PsiWhiteSpace('\n')
      |  DocComment
      |    ScPsiDocToken(DOC_COMMENT_START)('/**')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('A basic textual comment')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_in_one_line(): Unit = checkBoth(
    """
      |/** A basic textual comment */
      |""".stripMargin,
    """ScalaFile
      |  PsiWhiteSpace('\n')
      |  DocComment
      |    ScPsiDocToken(DOC_COMMENT_START)('/**')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('A basic textual comment')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  // Basic formatting tests
  def test_bold_text(): Unit = checkBoth(
      """
        |/**
        | * This is **bold** text
        | */
        |""".stripMargin,
      """
        |/**
        | * This is '''bold''' text
        | */
        |""".stripMargin,
      """ScalaFile
        |  PsiWhiteSpace('\n')
        |  DocComment
        |    ScPsiDocToken(DOC_COMMENT_START)('/**')
        |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
        |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
        |    ScDocParagraph
        |      ScPsiDocToken(DOC_WHITESPACE)(' ')
        |      ScPsiDocToken(DOC_COMMENT_DATA)('This is ')
        |      DocSyntaxElement 1
        |        ScPsiDocToken(DOC_BOLD_TAG 1)('**')
        |        ScPsiDocToken(DOC_COMMENT_DATA)('bold')
        |        ScPsiDocToken(DOC_BOLD_TAG 1)('**')
        |      ScPsiDocToken(DOC_COMMENT_DATA)(' text')
        |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
        |    ScPsiDocToken(DOC_COMMENT_END)('*/')
        |  PsiWhiteSpace('\n')""".stripMargin
    )

  def test_italic_text(): Unit = checkBoth(
    """
      |/**
      | * This is *italic* text
      | */
      |""".stripMargin,
    """
      |/**
      | * This is ''italic'' text
      | */
      |""".stripMargin,
    """ScalaFile
      |  PsiWhiteSpace('\n')
      |  DocComment
      |    ScPsiDocToken(DOC_COMMENT_START)('/**')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('This is ')
      |      DocSyntaxElement 2
      |        ScPsiDocToken(DOC_ITALIC_TAG 2)('*')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('italic')
      |        ScPsiDocToken(DOC_ITALIC_TAG 2)('*')
      |      ScPsiDocToken(DOC_COMMENT_DATA)(' text')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_unordered_list(): Unit = checkTree(
    """
      |/**
      | * Unordered list:
      | *  - Item 1
      | *  - Item 2
      | *  - Item 3
      | */
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  DocComment
      |    ScPsiDocToken(DOC_COMMENT_START)('/**')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('Unordered list:')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)('  ')
      |    ScDocList
      |      ScDocListItem
      |        ScPsiDocToken(DOC_LIST_ITEM_HEAD)('- ')
      |        ScDocParagraph
      |          ScPsiDocToken(DOC_COMMENT_DATA)('Item 1')
      |          ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |          ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |          ScPsiDocToken(DOC_WHITESPACE)('  ')
      |      ScDocListItem
      |        ScPsiDocToken(DOC_LIST_ITEM_HEAD)('- ')
      |        ScDocParagraph
      |          ScPsiDocToken(DOC_COMMENT_DATA)('Item 2')
      |          ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |          ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |          ScPsiDocToken(DOC_WHITESPACE)('  ')
      |      ScDocListItem
      |        ScPsiDocToken(DOC_LIST_ITEM_HEAD)('- ')
      |        ScDocParagraph
      |          ScPsiDocToken(DOC_COMMENT_DATA)('Item 3')
      |          ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_ordered_list(): Unit = checkTree(
    """
      |/**
      | * Ordered list:
      | *  1. First item
      | *  1. Second item
      | *  1. Third item
      | */
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  DocComment
      |    ScPsiDocToken(DOC_COMMENT_START)('/**')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('Ordered list:')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)('  ')
      |    ScDocList
      |      ScDocListItem
      |        ScPsiDocToken(DOC_LIST_ITEM_HEAD)('1. ')
      |        ScDocParagraph
      |          ScPsiDocToken(DOC_COMMENT_DATA)('First item')
      |          ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |          ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |          ScPsiDocToken(DOC_WHITESPACE)('  ')
      |      ScDocListItem
      |        ScPsiDocToken(DOC_LIST_ITEM_HEAD)('1. ')
      |        ScDocParagraph
      |          ScPsiDocToken(DOC_COMMENT_DATA)('Second item')
      |          ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |          ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |          ScPsiDocToken(DOC_WHITESPACE)('  ')
      |      ScDocListItem
      |        ScPsiDocToken(DOC_LIST_ITEM_HEAD)('1. ')
      |        ScDocParagraph
      |          ScPsiDocToken(DOC_COMMENT_DATA)('Third item')
      |          ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_code_fence(): Unit = checkTree(
    """
      |/**
      | * Code block example:
      | * ```
      | * val x = 42
      | * println(x)
      | * ```
      | */
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  DocComment
      |    ScPsiDocToken(DOC_COMMENT_START)('/**')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('Code block example:')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |    InnerCodeElement
      |      ScPsiDocToken(DOC_INNER_CODE_TAG)('```')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_INNER_CODE)(' val x = 42')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_INNER_CODE)(' println(x)')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_INNER_CODE)(' ')
      |      ScPsiDocToken(DOC_INNER_CLOSE_CODE_TAG)('```')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  // Code blocks with {{{ and }}} fences
  def test_code_block_fences(): Unit = checkTree(
    """
      |/**
      | * Code block example:
      | * {{{
      | * val x = 42
      | * println(x)
      | * }}}
      | */
      |""".stripMargin,
    """ScalaFile
      |  PsiWhiteSpace('\n')
      |  DocComment
      |    ScPsiDocToken(DOC_COMMENT_START)('/**')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('Code block example:')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |    InnerCodeElement
      |      ScPsiDocToken(DOC_INNER_CODE_TAG)('{{{')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_INNER_CODE)(' val x = 42')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_INNER_CODE)(' println(x)')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_INNER_CODE)(' ')
      |      ScPsiDocToken(DOC_INNER_CLOSE_CODE_TAG)('}}}')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_no_code_fence_in_the_middle(): Unit = checkTree(
    """
      |/**
      | * No code block: {{{
      | * val x = 42
      | * println(x)
      | * }}}
      | */
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  DocComment
      |    ScPsiDocToken(DOC_COMMENT_START)('/**')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('No code block: {{{')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('val x = 42')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('println(x)')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('}}}')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  // Code references with [[Reference]]
  def test_code_references(): Unit = checkTree(
    """
      |/**
      | * See [[scala.collection.immutable.List]] for more information.
      | * Also check [[scala.Option]].
      | */
      |""".stripMargin,
    """ScalaFile
      |  PsiWhiteSpace('\n')
      |  DocComment
      |    ScPsiDocToken(DOC_COMMENT_START)('/**')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('See ')
      |      DocSyntaxElement 64
      |        ScPsiDocToken(DOC_LINK_TAG 64)('[[')
      |        ASTWrapperPsiElement(SCALA_DOC_REFERENCE_LINK)
      |          CodeReferenceElement (scalaDoc): scala.collection.immutable.List
      |            CodeReferenceElement (scalaDoc): scala.collection.immutable
      |              CodeReferenceElement (scalaDoc): scala.collection
      |                CodeReferenceElement (scalaDoc): scala
      |                  PsiElement(identifier)('scala')
      |                PsiElement(.)('.')
      |                PsiElement(identifier)('collection')
      |              PsiElement(.)('.')
      |              PsiElement(identifier)('immutable')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('List')
      |        ScPsiDocToken(DOC_LINK_CLOSE_TAG 0)(']]')
      |      ScPsiDocToken(DOC_COMMENT_DATA)(' for more information.')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('Also check ')
      |      DocSyntaxElement 64
      |        ScPsiDocToken(DOC_LINK_TAG 64)('[[')
      |        ASTWrapperPsiElement(SCALA_DOC_REFERENCE_LINK)
      |          CodeReferenceElement (scalaDoc): scala.Option
      |            CodeReferenceElement (scalaDoc): scala
      |              PsiElement(identifier)('scala')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('Option')
      |        ScPsiDocToken(DOC_LINK_CLOSE_TAG 0)(']]')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('.')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_quote(): Unit = checkTree(
    """
      |/**
      | * > A lone quote
      | */
      |
      |/**
      | * A text
      | * > and then a *quote*
      | * > with multiple lines
      | * > > and a double qoute
      | */
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  DocComment
      |    ScPsiDocToken(DOC_COMMENT_START)('/**')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ASTWrapperPsiElement(ScalaDocBlockquote)
      |      ScPsiDocToken(DOC_COMMENT_DATA)(' >')
      |      ScDocParagraph
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('A lone quote')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n\n')
      |  DocComment
      |    ScPsiDocToken(DOC_COMMENT_START)('/**')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('A text')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ASTWrapperPsiElement(ScalaDocBlockquote)
      |      ScPsiDocToken(DOC_COMMENT_DATA)(' >')
      |      ScDocParagraph
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('and then a ')
      |        DocSyntaxElement 2
      |          ScPsiDocToken(DOC_ITALIC_TAG 2)('*')
      |          ScPsiDocToken(DOC_COMMENT_DATA)('quote')
      |          ScPsiDocToken(DOC_ITALIC_TAG 2)('*')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |        ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |        ScPsiDocToken(DOC_COMMENT_DATA)(' >')
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('with multiple lines')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |        ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |        ScPsiDocToken(DOC_COMMENT_DATA)(' >')
      |        ScPsiDocToken(DOC_WHITESPACE)(' >')
      |      ASTWrapperPsiElement(ScalaDocBlockquote)
      |        ScDocParagraph
      |          ScPsiDocToken(DOC_WHITESPACE)(' ')
      |          ScPsiDocToken(DOC_COMMENT_DATA)('and a double qoute')
      |          ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  // ScalaDoc tags
  def test_scaladoc_param_tag(): Unit = checkBoth(
    """
      |/**
      | * A method description
      | * @param name The name parameter
      | * @param age The age parameter
      | */
      |""".stripMargin,
    """ScalaFile
      |  PsiWhiteSpace('\n')
      |  DocComment
      |    ScPsiDocToken(DOC_COMMENT_START)('/**')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('A method description')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    DocTag
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_TAG_NAME)('@param')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScalaDocTagValue: name
      |        ScPsiDocToken(DOC_TAG_VALUE_TOKEN)('name')
      |      ScDocParagraph
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('The name parameter')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |        ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    DocTag
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_TAG_NAME)('@param')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScalaDocTagValue: age
      |        ScPsiDocToken(DOC_TAG_VALUE_TOKEN)('age')
      |      ScDocParagraph
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('The age parameter')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_scaladoc_return_tag(): Unit = checkBoth(
    """
      |/**
      | * Calculates something
      | * @return The calculated value
      | */
      |""".stripMargin,
    """ScalaFile
      |  PsiWhiteSpace('\n')
      |  DocComment
      |    ScPsiDocToken(DOC_COMMENT_START)('/**')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('Calculates something')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    DocTag
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_TAG_NAME)('@return')
      |      ScDocParagraph
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('The calculated value')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_scaladoc_throws_tag(): Unit = checkTree(
    """
      |/**
      | * Does something risky
      | * @throws IllegalArgumentException if the argument is invalid
      | * @throws NullPointerException if the input is null
      | */
      |""".stripMargin,
    """ScalaFile
      |  PsiWhiteSpace('\n')
      |  DocComment
      |    ScPsiDocToken(DOC_COMMENT_START)('/**')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('Does something risky')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    DocTag
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_TAG_NAME)('@throws')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ASTWrapperPsiElement(SCALA_DOC_REFERENCE_LINK)
      |        CodeReferenceElement (scalaDoc): IllegalArgumentException
      |          PsiElement(identifier)('IllegalArgumentException')
      |      ScDocParagraph
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('if the argument is invalid')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |        ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    DocTag
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_TAG_NAME)('@throws')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ASTWrapperPsiElement(SCALA_DOC_REFERENCE_LINK)
      |        CodeReferenceElement (scalaDoc): NullPointerException
      |          PsiElement(identifier)('NullPointerException')
      |      ScDocParagraph
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('if the input is null')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  // Combined features (more complex tests)
  def test_combined_formatting_and_lists(): Unit = checkTree(
    """
      |/**
      | * # Heading 1
      | * 
      | * This paragraph has **bold** and *italic* text.
      | * 
      | * ## Heading 2
      | * 
      | * - List item with **bold**
      | * - List item with *italic*
      | * - List item with [[scala.Option]] reference
      | */
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  DocComment
      |    ScPsiDocToken(DOC_COMMENT_START)('/**')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)(' ')
      |    ScDocMarkdownHeader
      |      ScPsiDocToken(DOC_COMMENT_DATA)('# Heading 1')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)(' ')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('This paragraph has ')
      |      DocSyntaxElement 1
      |        ScPsiDocToken(DOC_BOLD_TAG 1)('**')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('bold')
      |        ScPsiDocToken(DOC_BOLD_TAG 1)('**')
      |      ScPsiDocToken(DOC_COMMENT_DATA)(' and ')
      |      DocSyntaxElement 2
      |        ScPsiDocToken(DOC_ITALIC_TAG 2)('*')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('italic')
      |        ScPsiDocToken(DOC_ITALIC_TAG 2)('*')
      |      ScPsiDocToken(DOC_COMMENT_DATA)(' text.')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |    ScDocMarkdownHeader
      |      ScPsiDocToken(DOC_COMMENT_DATA)('## Heading 2')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)(' ')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)(' ')
      |    ScDocList
      |      ScDocListItem
      |        ScPsiDocToken(DOC_LIST_ITEM_HEAD)('- ')
      |        ScDocParagraph
      |          ScPsiDocToken(DOC_COMMENT_DATA)('List item with ')
      |          DocSyntaxElement 1
      |            ScPsiDocToken(DOC_BOLD_TAG 1)('**')
      |            ScPsiDocToken(DOC_COMMENT_DATA)('bold')
      |            ScPsiDocToken(DOC_BOLD_TAG 1)('**')
      |          ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |          ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |          ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScDocListItem
      |        ScPsiDocToken(DOC_LIST_ITEM_HEAD)('- ')
      |        ScDocParagraph
      |          ScPsiDocToken(DOC_COMMENT_DATA)('List item with ')
      |          DocSyntaxElement 2
      |            ScPsiDocToken(DOC_ITALIC_TAG 2)('*')
      |            ScPsiDocToken(DOC_COMMENT_DATA)('italic')
      |            ScPsiDocToken(DOC_ITALIC_TAG 2)('*')
      |          ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |          ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |          ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScDocListItem
      |        ScPsiDocToken(DOC_LIST_ITEM_HEAD)('- ')
      |        ScDocParagraph
      |          ScPsiDocToken(DOC_COMMENT_DATA)('List item with ')
      |          DocSyntaxElement 64
      |            ScPsiDocToken(DOC_LINK_TAG 64)('[[')
      |            ASTWrapperPsiElement(SCALA_DOC_REFERENCE_LINK)
      |              CodeReferenceElement (scalaDoc): scala.Option
      |                CodeReferenceElement (scalaDoc): scala
      |                  PsiElement(identifier)('scala')
      |                PsiElement(.)('.')
      |                PsiElement(identifier)('Option')
      |            ScPsiDocToken(DOC_LINK_CLOSE_TAG 0)(']]')
      |          ScPsiDocToken(DOC_COMMENT_DATA)(' reference')
      |          ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_combined_code_and_tags(): Unit = checkTree(
    """
      |/**
      | * Complex example with code and tags
      | *
      | * {{{
      | * def example(name: String): Option[Int] = {
      | *   Some(name.length)
      | * }
      | * }}}
      | *
      | * @param name The input name
      | * @return [[scala.Option]] containing the length
      | * @throws NullPointerException if name is null
      | */
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  DocComment
      |    ScPsiDocToken(DOC_COMMENT_START)('/**')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('Complex example with code and tags')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |    InnerCodeElement
      |      ScPsiDocToken(DOC_INNER_CODE_TAG)('{{{')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_INNER_CODE)(' def example(name: String): Option[Int] = {')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_INNER_CODE)('   Some(name.length)')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_INNER_CODE)(' }')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_INNER_CODE)(' ')
      |      ScPsiDocToken(DOC_INNER_CLOSE_CODE_TAG)('}}}')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    DocTag
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_TAG_NAME)('@param')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScalaDocTagValue: name
      |        ScPsiDocToken(DOC_TAG_VALUE_TOKEN)('name')
      |      ScDocParagraph
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('The input name')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |        ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    DocTag
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_TAG_NAME)('@return')
      |      ScDocParagraph
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        DocSyntaxElement 64
      |          ScPsiDocToken(DOC_LINK_TAG 64)('[[')
      |          ASTWrapperPsiElement(SCALA_DOC_REFERENCE_LINK)
      |            CodeReferenceElement (scalaDoc): scala.Option
      |              CodeReferenceElement (scalaDoc): scala
      |                PsiElement(identifier)('scala')
      |              PsiElement(.)('.')
      |              PsiElement(identifier)('Option')
      |          ScPsiDocToken(DOC_LINK_CLOSE_TAG 0)(']]')
      |        ScPsiDocToken(DOC_COMMENT_DATA)(' containing the length')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |        ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    DocTag
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_TAG_NAME)('@throws')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ASTWrapperPsiElement(SCALA_DOC_REFERENCE_LINK)
      |        CodeReferenceElement (scalaDoc): NullPointerException
      |          PsiElement(identifier)('NullPointerException')
      |      ScDocParagraph
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('if name is null')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_java_style_inline_tags(): Unit = checkTree(
      """
        |/**
        | * Java-style inline tags:
        | * {@link scala.Option}
        | * {@literal literal text}
        | */
        |""".stripMargin,
      """ScalaFile
        |  PsiWhiteSpace('\n')
        |  DocComment
        |    ScPsiDocToken(DOC_COMMENT_START)('/**')
        |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
        |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
        |    ScDocParagraph
        |      ScPsiDocToken(DOC_WHITESPACE)(' ')
        |      ScPsiDocToken(DOC_COMMENT_DATA)('Java-style inline tags:')
        |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
        |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
        |      ScPsiDocToken(DOC_WHITESPACE)(' ')
        |      ScPsiDocToken(DOC_COMMENT_DATA)('{@link scala.Option}')
        |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
        |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
        |      ScPsiDocToken(DOC_WHITESPACE)(' ')
        |      ScPsiDocToken(DOC_COMMENT_DATA)('{@literal literal text}')
        |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
        |    ScPsiDocToken(DOC_COMMENT_END)('*/')
        |  PsiWhiteSpace('\n')
        |""".stripMargin
    )

  // Edge cases for tags within blocks
  def test_tags_blocks(): Unit = checkTree(
    """
      |/**
      | * 1. List item
      | *    @note a continuation of that list item
      | *
      | * ```
      | * @note tag in code block (not a tag)
      | * ```
      | *
      | * > Block quote
      | *   @note a note in that block quote
      | *
      | * 1. List item
      | *    > @note A note that's not a tag
      | */
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  DocComment
      |    ScPsiDocToken(DOC_COMMENT_START)('/**')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)(' ')
      |    ScDocList
      |      ScDocListItem
      |        ScPsiDocToken(DOC_LIST_ITEM_HEAD)('1. ')
      |        ScDocParagraph
      |          ScPsiDocToken(DOC_COMMENT_DATA)('List item')
      |          ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |          ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    DocTag
      |      ScPsiDocToken(DOC_WHITESPACE)('    ')
      |      ScPsiDocToken(DOC_TAG_NAME)('@note')
      |      ScDocParagraph
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('a continuation of that list item')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |        ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |        ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      InnerCodeElement
      |        ScPsiDocToken(DOC_INNER_CODE_TAG)('```')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |        ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |        ScPsiDocToken(DOC_INNER_CODE)(' @note tag in code block (not a tag)')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |        ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |        ScPsiDocToken(DOC_INNER_CODE)(' ')
      |        ScPsiDocToken(DOC_INNER_CLOSE_CODE_TAG)('```')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ASTWrapperPsiElement(ScalaDocBlockquote)
      |        ScPsiDocToken(DOC_COMMENT_DATA)(' >')
      |        ScDocParagraph
      |          ScPsiDocToken(DOC_WHITESPACE)(' ')
      |          ScPsiDocToken(DOC_COMMENT_DATA)('Block quote')
      |          ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |          ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    DocTag
      |      ScPsiDocToken(DOC_WHITESPACE)('   ')
      |      ScPsiDocToken(DOC_TAG_NAME)('@note')
      |      ScDocParagraph
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('a note in that block quote')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |        ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |        ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScDocList
      |        ScDocListItem
      |          ScPsiDocToken(DOC_LIST_ITEM_HEAD)('1. ')
      |          ScDocParagraph
      |            ScPsiDocToken(DOC_COMMENT_DATA)('List item')
      |            ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |            ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |            ScPsiDocToken(DOC_WHITESPACE)('    ')
      |          ASTWrapperPsiElement(ScalaDocBlockquote)
      |            ScPsiDocToken(DOC_COMMENT_DATA)('>')
      |            ScDocParagraph
      |              ScPsiDocToken(DOC_WHITESPACE)(' ')
      |              ScPsiDocToken(DOC_COMMENT_DATA)('@note A note that's not a tag')
      |              ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_all_features_combined(): Unit = checkTree(
    """
      |/**
      | * # Complete Documentation Example
      | * 
      | * This is a paragraph with **bold** and *italic* text.
      | * It also contains a code reference to [[scala.collection.immutable.List]].
      | * 
      | * ## Code Examples
      | * 
      | * Here's a simple code example:
      | * {{{
      | * val numbers = List(1, 2, 3, 4, 5)
      | * val doubled = numbers.map(_ * 2)
      | * }}}
      | * 
      | * ## Java-style Inline Tags
      | * 
      | * {@link scala.Option}
      | * {@literal literal text}
      | * 
      | * ## Parameters
      | * 
      | * @param input The input string to process
      | * @param count The number of times to process
      | * 
      | * ## Return Value
      | * 
      | * @return An [[scala.Option]] containing the result
      | * 
      | * ## Exceptions
      | * 
      | * @throws IllegalArgumentException if input is empty
      | * @throws NullPointerException if input is null
      | */
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  DocComment
      |    ScPsiDocToken(DOC_COMMENT_START)('/**')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)(' ')
      |    ScDocMarkdownHeader
      |      ScPsiDocToken(DOC_COMMENT_DATA)('# Complete Documentation Example')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)(' ')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('This is a paragraph with ')
      |      DocSyntaxElement 1
      |        ScPsiDocToken(DOC_BOLD_TAG 1)('**')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('bold')
      |        ScPsiDocToken(DOC_BOLD_TAG 1)('**')
      |      ScPsiDocToken(DOC_COMMENT_DATA)(' and ')
      |      DocSyntaxElement 2
      |        ScPsiDocToken(DOC_ITALIC_TAG 2)('*')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('italic')
      |        ScPsiDocToken(DOC_ITALIC_TAG 2)('*')
      |      ScPsiDocToken(DOC_COMMENT_DATA)(' text.')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('It also contains a code reference to ')
      |      DocSyntaxElement 64
      |        ScPsiDocToken(DOC_LINK_TAG 64)('[[')
      |        ASTWrapperPsiElement(SCALA_DOC_REFERENCE_LINK)
      |          CodeReferenceElement (scalaDoc): scala.collection.immutable.List
      |            CodeReferenceElement (scalaDoc): scala.collection.immutable
      |              CodeReferenceElement (scalaDoc): scala.collection
      |                CodeReferenceElement (scalaDoc): scala
      |                  PsiElement(identifier)('scala')
      |                PsiElement(.)('.')
      |                PsiElement(identifier)('collection')
      |              PsiElement(.)('.')
      |              PsiElement(identifier)('immutable')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('List')
      |        ScPsiDocToken(DOC_LINK_CLOSE_TAG 0)(']]')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('.')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |    ScDocMarkdownHeader
      |      ScPsiDocToken(DOC_COMMENT_DATA)('## Code Examples')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)(' ')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('Here's a simple code example:')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |    InnerCodeElement
      |      ScPsiDocToken(DOC_INNER_CODE_TAG)('{{{')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_INNER_CODE)(' val numbers = List(1, 2, 3, 4, 5)')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_INNER_CODE)(' val doubled = numbers.map(_ * 2)')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_INNER_CODE)(' ')
      |      ScPsiDocToken(DOC_INNER_CLOSE_CODE_TAG)('}}}')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)(' ')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)(' ')
      |    ScDocMarkdownHeader
      |      ScPsiDocToken(DOC_COMMENT_DATA)('## Java-style Inline Tags')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)(' ')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('{@link scala.Option}')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('{@literal literal text}')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |    ScDocMarkdownHeader
      |      ScPsiDocToken(DOC_COMMENT_DATA)('## Parameters')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)(' ')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    DocTag
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_TAG_NAME)('@param')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScalaDocTagValue: input
      |        ScPsiDocToken(DOC_TAG_VALUE_TOKEN)('input')
      |      ScDocParagraph
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('The input string to process')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |        ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    DocTag
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_TAG_NAME)('@param')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScalaDocTagValue: count
      |        ScPsiDocToken(DOC_TAG_VALUE_TOKEN)('count')
      |      ScDocParagraph
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('The number of times to process')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |        ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |        ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScDocMarkdownHeader
      |        ScPsiDocToken(DOC_COMMENT_DATA)('## Return Value')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)(' ')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    DocTag
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_TAG_NAME)('@return')
      |      ScDocParagraph
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('An ')
      |        DocSyntaxElement 64
      |          ScPsiDocToken(DOC_LINK_TAG 64)('[[')
      |          ASTWrapperPsiElement(SCALA_DOC_REFERENCE_LINK)
      |            CodeReferenceElement (scalaDoc): scala.Option
      |              CodeReferenceElement (scalaDoc): scala
      |                PsiElement(identifier)('scala')
      |              PsiElement(.)('.')
      |              PsiElement(identifier)('Option')
      |          ScPsiDocToken(DOC_LINK_CLOSE_TAG 0)(']]')
      |        ScPsiDocToken(DOC_COMMENT_DATA)(' containing the result')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |        ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |        ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScDocMarkdownHeader
      |        ScPsiDocToken(DOC_COMMENT_DATA)('## Exceptions')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)(' ')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    DocTag
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_TAG_NAME)('@throws')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ASTWrapperPsiElement(SCALA_DOC_REFERENCE_LINK)
      |        CodeReferenceElement (scalaDoc): IllegalArgumentException
      |          PsiElement(identifier)('IllegalArgumentException')
      |      ScDocParagraph
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('if input is empty')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |        ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    DocTag
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_TAG_NAME)('@throws')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ASTWrapperPsiElement(SCALA_DOC_REFERENCE_LINK)
      |        CodeReferenceElement (scalaDoc): NullPointerException
      |          PsiElement(identifier)('NullPointerException')
      |      ScDocParagraph
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('if input is null')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_incomplete(): Unit = checkTree(
    """
      |/**
      |object Blub
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  DocComment
      |    ScPsiDocToken(DOC_COMMENT_START)('/**')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_COMMENT_DATA)('object Blub')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n')
      |""".stripMargin
  )

  def test_incomplete_spans(): Unit = checkTree(
    """
      |/**
      | * **italic*
      | *
      | *  *italic**
      | *
      | * __italic_
      | *
      | *  _italic__
      | *
      | * ***bold**
      | *
      | *  **bold***
      | *
      | * ___bold__
      | *
      | *  __bold___
      | *
      | *   `code`
      | *
      | *  ``code`
      | *
      | *   `code``
      | *
      | *  ``code``
      | *
      | * ```code```
      | *
      | * _*italic_*
      | *
      | * *_italic*_
      | */
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  DocComment
      |    ScPsiDocToken(DOC_COMMENT_START)('/**')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('*')
      |      DocSyntaxElement 2
      |        ScPsiDocToken(DOC_ITALIC_TAG 2)('*')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('italic')
      |        ScPsiDocToken(DOC_ITALIC_TAG 2)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)('  ')
      |      DocSyntaxElement 2
      |        ScPsiDocToken(DOC_ITALIC_TAG 2)('*')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('italic')
      |        ScPsiDocToken(DOC_ITALIC_TAG 2)('*')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('_')
      |      DocSyntaxElement 2
      |        ScPsiDocToken(DOC_ITALIC_TAG 2)('_')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('italic')
      |        ScPsiDocToken(DOC_ITALIC_TAG 2)('_')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)('  ')
      |      DocSyntaxElement 2
      |        ScPsiDocToken(DOC_ITALIC_TAG 2)('_')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('italic')
      |        ScPsiDocToken(DOC_ITALIC_TAG 2)('_')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('_')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('*')
      |      DocSyntaxElement 1
      |        ScPsiDocToken(DOC_BOLD_TAG 1)('**')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('bold')
      |        ScPsiDocToken(DOC_BOLD_TAG 1)('**')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)('  ')
      |      DocSyntaxElement 1
      |        ScPsiDocToken(DOC_BOLD_TAG 1)('**')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('bold')
      |        ScPsiDocToken(DOC_BOLD_TAG 1)('**')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('_')
      |      DocSyntaxElement 1
      |        ScPsiDocToken(DOC_BOLD_TAG 1)('__')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('bold')
      |        ScPsiDocToken(DOC_BOLD_TAG 1)('__')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)('  ')
      |      DocSyntaxElement 1
      |        ScPsiDocToken(DOC_BOLD_TAG 1)('__')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('bold')
      |        ScPsiDocToken(DOC_BOLD_TAG 1)('__')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('_')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)('   ')
      |      DocSyntaxElement 8
      |        ScPsiDocToken(DOC_MONOSPACE_TAG 8)('`')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('code')
      |        ScPsiDocToken(DOC_MONOSPACE_TAG 8)('`')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)('  ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('``code`')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)('   ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('`code``')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)('  ')
      |      DocSyntaxElement 8
      |        ScPsiDocToken(DOC_MONOSPACE_TAG 8)('``')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('code')
      |        ScPsiDocToken(DOC_MONOSPACE_TAG 8)('``')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      DocSyntaxElement 8
      |        ScPsiDocToken(DOC_MONOSPACE_TAG 8)('```')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('code')
      |        ScPsiDocToken(DOC_MONOSPACE_TAG 8)('```')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      DocSyntaxElement 2
      |        ScPsiDocToken(DOC_ITALIC_TAG 2)('_')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('*italic')
      |        ScPsiDocToken(DOC_ITALIC_TAG 2)('_')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      DocSyntaxElement 2
      |        ScPsiDocToken(DOC_ITALIC_TAG 2)('*')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('_italic')
      |        ScPsiDocToken(DOC_ITALIC_TAG 2)('*')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('_')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_start_on_first_line(): Unit = checkTree(
    """
      |/** MainAnnotation provides the functionality for a compiler-generated main class.
      | *  It links a compiler-generated main method (call it compiler-main) to a user
      | *  written main method (user-main).
      | *  The protocol of calls from compiler-main is as follows:
      | *
      | *    - create a `command` with the command line arguments,
      | *    - for each parameter of user-main, a call to `command.argGetter`,
      | *      or `command.argsGetter` if is a final varargs parameter,
      | *    - a call to `command.run` with the closure of user-main applied to all arguments.
      | */
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  DocComment
      |    ScPsiDocToken(DOC_COMMENT_START)('/**')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('MainAnnotation provides the functionality for a compiler-generated main class.')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)('  ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('It links a compiler-generated main method (call it compiler-main) to a user')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)('  ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('written main method (user-main).')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)('  ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('The protocol of calls from compiler-main is as follows:')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)('    ')
      |    ScDocList
      |      ScDocListItem
      |        ScPsiDocToken(DOC_LIST_ITEM_HEAD)('- ')
      |        ScDocParagraph
      |          ScPsiDocToken(DOC_COMMENT_DATA)('create a ')
      |          DocSyntaxElement 8
      |            ScPsiDocToken(DOC_MONOSPACE_TAG 8)('`')
      |            ScPsiDocToken(DOC_COMMENT_DATA)('command')
      |            ScPsiDocToken(DOC_MONOSPACE_TAG 8)('`')
      |          ScPsiDocToken(DOC_COMMENT_DATA)(' with the command line arguments,')
      |          ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |          ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |          ScPsiDocToken(DOC_WHITESPACE)('    ')
      |      ScDocListItem
      |        ScPsiDocToken(DOC_LIST_ITEM_HEAD)('- ')
      |        ScDocParagraph
      |          ScPsiDocToken(DOC_COMMENT_DATA)('for each parameter of user-main, a call to ')
      |          DocSyntaxElement 8
      |            ScPsiDocToken(DOC_MONOSPACE_TAG 8)('`')
      |            ScPsiDocToken(DOC_COMMENT_DATA)('command.argGetter')
      |            ScPsiDocToken(DOC_MONOSPACE_TAG 8)('`')
      |          ScPsiDocToken(DOC_COMMENT_DATA)(',')
      |          ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |          ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |          ScPsiDocToken(DOC_WHITESPACE)('      ')
      |          ScPsiDocToken(DOC_COMMENT_DATA)('or ')
      |          DocSyntaxElement 8
      |            ScPsiDocToken(DOC_MONOSPACE_TAG 8)('`')
      |            ScPsiDocToken(DOC_COMMENT_DATA)('command.argsGetter')
      |            ScPsiDocToken(DOC_MONOSPACE_TAG 8)('`')
      |          ScPsiDocToken(DOC_COMMENT_DATA)(' if is a final varargs parameter,')
      |          ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |          ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |          ScPsiDocToken(DOC_WHITESPACE)('    ')
      |      ScDocListItem
      |        ScPsiDocToken(DOC_LIST_ITEM_HEAD)('- ')
      |        ScDocParagraph
      |          ScPsiDocToken(DOC_COMMENT_DATA)('a call to ')
      |          DocSyntaxElement 8
      |            ScPsiDocToken(DOC_MONOSPACE_TAG 8)('`')
      |            ScPsiDocToken(DOC_COMMENT_DATA)('command.run')
      |            ScPsiDocToken(DOC_MONOSPACE_TAG 8)('`')
      |          ScPsiDocToken(DOC_COMMENT_DATA)(' with the closure of user-main applied to all arguments.')
      |          ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def testIndentedTagValue(): Unit = checkTree(
    """
      |/**
      | * @param parameter1        description
      | * @param parameter2        description
      | * @param parameterFieldVal description
      | * @param parameterFieldVar description
      | */
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  DocComment
      |    ScPsiDocToken(DOC_COMMENT_START)('/**')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    DocTag
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_TAG_NAME)('@param')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScalaDocTagValue: parameter1
      |        ScPsiDocToken(DOC_TAG_VALUE_TOKEN)('parameter1')
      |      ScDocParagraph
      |        ScPsiDocToken(DOC_WHITESPACE)('        ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('description')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |        ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    DocTag
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_TAG_NAME)('@param')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScalaDocTagValue: parameter2
      |        ScPsiDocToken(DOC_TAG_VALUE_TOKEN)('parameter2')
      |      ScDocParagraph
      |        ScPsiDocToken(DOC_WHITESPACE)('        ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('description')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |        ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    DocTag
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_TAG_NAME)('@param')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScalaDocTagValue: parameterFieldVal
      |        ScPsiDocToken(DOC_TAG_VALUE_TOKEN)('parameterFieldVal')
      |      ScDocParagraph
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('description')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |        ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    DocTag
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_TAG_NAME)('@param')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScalaDocTagValue: parameterFieldVar
      |        ScPsiDocToken(DOC_TAG_VALUE_TOKEN)('parameterFieldVar')
      |      ScDocParagraph
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('description')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )
}
