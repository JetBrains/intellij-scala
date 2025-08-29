package org.jetbrains.plugins.scala.lang.scaladoc.parser

import com.intellij.psi.impl.DebugUtil.psiToString
import org.jetbrains.plugins.scala.lang.parser.scala3.SimpleScala3ParserTestBase

class MarkdownScalaDocParserTest extends SimpleScala3ParserTestBase {
  def test_basic_markdown(): Unit = checkTree(
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
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  // Basic formatting tests
  def test_bold_text(): Unit = checkTree(
      """
        |/**
        | * This is **bold** text
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
        |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
        |    ScPsiDocToken(DOC_COMMENT_END)('*/')
        |  PsiWhiteSpace('\n')""".stripMargin
    )

  def test_italic_text(): Unit = checkTree(
    """
      |/**
      | * This is *italic* text
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
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
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
    """ScalaFile
      |  PsiWhiteSpace('\n')
      |  DocComment
      |    ScPsiDocToken(DOC_COMMENT_START)('/**')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('Unordered list:')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocList
      |      ScDocListItem
      |        ScPsiDocToken(DOC_LIST_ITEM_HEAD)('  - ')
      |        ScDocParagraph
      |          ScPsiDocToken(DOC_COMMENT_DATA)('Item 1')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScDocListItem
      |        ScPsiDocToken(DOC_LIST_ITEM_HEAD)('  - ')
      |        ScDocParagraph
      |          ScPsiDocToken(DOC_COMMENT_DATA)('Item 2')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScDocListItem
      |        ScPsiDocToken(DOC_LIST_ITEM_HEAD)('  - ')
      |        ScDocParagraph
      |          ScPsiDocToken(DOC_COMMENT_DATA)('Item 3')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')""".stripMargin
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
    """ScalaFile
      |  PsiWhiteSpace('\n')
      |  DocComment
      |    ScPsiDocToken(DOC_COMMENT_START)('/**')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('Ordered list:')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocList
      |      ScDocListItem
      |        ScPsiDocToken(DOC_LIST_ITEM_HEAD)('  1. ')
      |        ScDocParagraph
      |          ScPsiDocToken(DOC_COMMENT_DATA)('First item')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScDocListItem
      |        ScPsiDocToken(DOC_LIST_ITEM_HEAD)('  1. ')
      |        ScDocParagraph
      |          ScPsiDocToken(DOC_COMMENT_DATA)('Second item')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScDocListItem
      |        ScPsiDocToken(DOC_LIST_ITEM_HEAD)('  1. ')
      |        ScDocParagraph
      |          ScPsiDocToken(DOC_COMMENT_DATA)('Third item')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')""".stripMargin
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
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    InnerCodeElement
      |      ScPsiDocToken(DOC_COMMENT_DATA)(' {{{')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_COMMENT_DATA)(' val x = 42')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_COMMENT_DATA)(' println(x)')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_COMMENT_DATA)(' }}}')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')""".stripMargin
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
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  // ScalaDoc tags
  def test_scaladoc_param_tag(): Unit = checkTree(
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
      |        ScPsiDocToken(DOC_COMMENT_DATA)('The name parameter')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    DocTag
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_TAG_NAME)('@param')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScalaDocTagValue: age
      |        ScPsiDocToken(DOC_TAG_VALUE_TOKEN)('age')
      |      ScDocParagraph
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('The age parameter')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_scaladoc_return_tag(): Unit = checkTree(
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
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    DocTag
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_TAG_NAME)('@return')
      |      ScDocParagraph
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('The calculated value')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
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
      |        ScPsiDocToken(DOC_COMMENT_DATA)('if the argument is invalid')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
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
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
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
    """ScalaFile
      |  PsiWhiteSpace('\n')
      |  DocComment
      |    ScPsiDocToken(DOC_COMMENT_START)('/**')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ASTWrapperPsiElement(DOC_MARKDOWN_HEADER)
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
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
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)(' ')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ASTWrapperPsiElement(DOC_MARKDOWN_HEADER)
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('## Heading 2')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)(' ')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocList
      |      ScDocListItem
      |        ScPsiDocToken(DOC_LIST_ITEM_HEAD)(' - ')
      |        ScDocParagraph
      |          ScPsiDocToken(DOC_COMMENT_DATA)('List item with ')
      |          DocSyntaxElement 1
      |            ScPsiDocToken(DOC_BOLD_TAG 1)('**')
      |            ScPsiDocToken(DOC_COMMENT_DATA)('bold')
      |            ScPsiDocToken(DOC_BOLD_TAG 1)('**')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScDocListItem
      |        ScPsiDocToken(DOC_LIST_ITEM_HEAD)(' - ')
      |        ScDocParagraph
      |          ScPsiDocToken(DOC_COMMENT_DATA)('List item with ')
      |          DocSyntaxElement 2
      |            ScPsiDocToken(DOC_ITALIC_TAG 2)('*')
      |            ScPsiDocToken(DOC_COMMENT_DATA)('italic')
      |            ScPsiDocToken(DOC_ITALIC_TAG 2)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScDocListItem
      |        ScPsiDocToken(DOC_LIST_ITEM_HEAD)(' - ')
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
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')""".stripMargin
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
    """ScalaFile
      |  PsiWhiteSpace('\n')
      |  DocComment
      |    ScPsiDocToken(DOC_COMMENT_START)('/**')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('Complex example with code and tags')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)(' ')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    InnerCodeElement
      |      ScPsiDocToken(DOC_COMMENT_DATA)(' {{{')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_COMMENT_DATA)(' def example(name: String): Option[Int] = {')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('   Some(name.length)')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_COMMENT_DATA)(' }')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_COMMENT_DATA)(' }}}')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)(' ')
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
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
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
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
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
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')""".stripMargin
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
        |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
        |    ScPsiDocToken(DOC_COMMENT_END)('*/')
        |  PsiWhiteSpace('\n')""".stripMargin
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
      | */""".stripMargin,
    """ScalaFile
      |  PsiWhiteSpace('\n')
      |  DocComment
      |    ScPsiDocToken(DOC_COMMENT_START)('/**')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocList
      |      ScDocListItem
      |        ScPsiDocToken(DOC_LIST_ITEM_HEAD)(' 1. ')
      |        ScDocParagraph
      |          ScPsiDocToken(DOC_COMMENT_DATA)('List item')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    DocTag
      |      ScPsiDocToken(DOC_WHITESPACE)('    ')
      |      ScPsiDocToken(DOC_TAG_NAME)('@note')
      |      ScDocParagraph
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('a continuation of that list item')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      InnerCodeElement
      |        ScPsiDocToken(DOC_COMMENT_DATA)(' ```')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |        ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |        ScPsiDocToken(DOC_COMMENT_DATA)(' @note tag in code block (not a tag)')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |        ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |        ScPsiDocToken(DOC_COMMENT_DATA)(' ```')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ASTWrapperPsiElement(ScalaDocBlockquote)
      |        ScPsiDocToken(DOC_COMMENT_DATA)(' > ')
      |        ScDocParagraph
      |          ScPsiDocToken(DOC_COMMENT_DATA)('Block quote')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    DocTag
      |      ScPsiDocToken(DOC_WHITESPACE)('   ')
      |      ScPsiDocToken(DOC_TAG_NAME)('@note')
      |      ScDocParagraph
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('a note in that block quote')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScDocList
      |        ScDocListItem
      |          ScPsiDocToken(DOC_LIST_ITEM_HEAD)(' 1. ')
      |          ScDocParagraph
      |            ScPsiDocToken(DOC_COMMENT_DATA)('List item')
      |          ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |          ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |          ScPsiDocToken(DOC_WHITESPACE)('    ')
      |          ASTWrapperPsiElement(ScalaDocBlockquote)
      |            ScPsiDocToken(DOC_COMMENT_DATA)('> ')
      |            ScDocParagraph
      |              ScPsiDocToken(DOC_COMMENT_DATA)('@note A note that's not a tag')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')""".stripMargin
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
    """ScalaFile
      |  PsiWhiteSpace('\n')
      |  DocComment
      |    ScPsiDocToken(DOC_COMMENT_START)('/**')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ASTWrapperPsiElement(DOC_MARKDOWN_HEADER)
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
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
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)(' ')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ASTWrapperPsiElement(DOC_MARKDOWN_HEADER)
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('## Code Examples')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)(' ')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('Here's a simple code example:')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    InnerCodeElement
      |      ScPsiDocToken(DOC_COMMENT_DATA)(' {{{')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_COMMENT_DATA)(' val numbers = List(1, 2, 3, 4, 5)')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_COMMENT_DATA)(' val doubled = numbers.map(_ * 2)')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_COMMENT_DATA)(' }}}')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)(' ')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ASTWrapperPsiElement(DOC_MARKDOWN_HEADER)
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
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
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)(' ')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ASTWrapperPsiElement(DOC_MARKDOWN_HEADER)
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
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
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    DocTag
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_TAG_NAME)('@param')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScalaDocTagValue: count
      |        ScPsiDocToken(DOC_TAG_VALUE_TOKEN)('count')
      |      ScDocParagraph
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('The number of times to process')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ASTWrapperPsiElement(DOC_MARKDOWN_HEADER)
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('## Return Value')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
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
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ASTWrapperPsiElement(DOC_MARKDOWN_HEADER)
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('## Exceptions')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
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
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
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
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')""".stripMargin
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
      |    ScPsiDocToken(DOC_WHITESPACE)('\n')
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
    """ScalaFile
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
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)('  ')
      |      DocSyntaxElement 2
      |        ScPsiDocToken(DOC_ITALIC_TAG 2)('*')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('italic')
      |        ScPsiDocToken(DOC_ITALIC_TAG 2)('*')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('_')
      |      DocSyntaxElement 2
      |        ScPsiDocToken(DOC_ITALIC_TAG 2)('_')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('italic')
      |        ScPsiDocToken(DOC_ITALIC_TAG 2)('_')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)('  ')
      |      DocSyntaxElement 2
      |        ScPsiDocToken(DOC_ITALIC_TAG 2)('_')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('italic')
      |        ScPsiDocToken(DOC_ITALIC_TAG 2)('_')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('_')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('*')
      |      DocSyntaxElement 1
      |        ScPsiDocToken(DOC_BOLD_TAG 1)('**')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('bold')
      |        ScPsiDocToken(DOC_BOLD_TAG 1)('**')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)('  ')
      |      DocSyntaxElement 1
      |        ScPsiDocToken(DOC_BOLD_TAG 1)('**')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('bold')
      |        ScPsiDocToken(DOC_BOLD_TAG 1)('**')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('_')
      |      DocSyntaxElement 1
      |        ScPsiDocToken(DOC_BOLD_TAG 1)('__')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('bold')
      |        ScPsiDocToken(DOC_BOLD_TAG 1)('__')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)('  ')
      |      DocSyntaxElement 1
      |        ScPsiDocToken(DOC_BOLD_TAG 1)('__')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('bold')
      |        ScPsiDocToken(DOC_BOLD_TAG 1)('__')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('_')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)('   ')
      |      DocSyntaxElement 8
      |        ScPsiDocToken(DOC_MONOSPACE_TAG 8)('`')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('code')
      |        ScPsiDocToken(DOC_MONOSPACE_TAG 8)('`')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)('  ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('``code`')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)('   ')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('`code``')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)('  ')
      |      DocSyntaxElement 8
      |        ScPsiDocToken(DOC_MONOSPACE_TAG 8)('``')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('code')
      |        ScPsiDocToken(DOC_MONOSPACE_TAG 8)('``')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      DocSyntaxElement 8
      |        ScPsiDocToken(DOC_MONOSPACE_TAG 8)('```')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('code')
      |        ScPsiDocToken(DOC_MONOSPACE_TAG 8)('```')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      DocSyntaxElement 2
      |        ScPsiDocToken(DOC_ITALIC_TAG 2)('_')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('*italic')
      |        ScPsiDocToken(DOC_ITALIC_TAG 2)('_')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      DocSyntaxElement 2
      |        ScPsiDocToken(DOC_ITALIC_TAG 2)('*')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('_italic')
      |        ScPsiDocToken(DOC_ITALIC_TAG 2)('*')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('_')
      |    ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_that_fails_safety(): Unit = assertNothing {}
}
