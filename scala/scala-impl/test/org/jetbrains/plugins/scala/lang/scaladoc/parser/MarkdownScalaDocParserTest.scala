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
      |    ScPsiDocToken(DOC_COMMENT_DATA)('/**')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_COMMENT_DATA)('A basic textual comment')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n ')
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
      |    ScPsiDocToken(DOC_COMMENT_DATA)('/**')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_COMMENT_DATA)('This is ')
      |      DocSyntaxElement 1
      |        ScPsiDocToken(DOC_COMMENT_DATA)('**bold**')
      |      ScPsiDocToken(DOC_COMMENT_DATA)(' text')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n ')
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
      |    ScPsiDocToken(DOC_COMMENT_DATA)('/**')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_COMMENT_DATA)('This is ')
      |      DocSyntaxElement 2
      |        ScPsiDocToken(DOC_COMMENT_DATA)('*italic*')
      |      ScPsiDocToken(DOC_COMMENT_DATA)(' text')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_unordered_list(): Unit = checkTree(
    """
      |/**
      | * Unordered list:
      | * - Item 1
      | * - Item 2
      | * - Item 3
      | */
      |""".stripMargin,
    """ScalaFile
      |  PsiWhiteSpace('\n')
      |  DocComment
      |    ScPsiDocToken(DOC_COMMENT_DATA)('/**')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_COMMENT_DATA)('Unordered list:')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('- ')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_COMMENT_DATA)('Item 1')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('- ')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_COMMENT_DATA)('Item 2')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('- ')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_COMMENT_DATA)('Item 3')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_ordered_list(): Unit = checkTree(
    """
      |/**
      | * Ordered list:
      | * 1. First item
      | * 2. Second item
      | * 3. Third item
      | */
      |""".stripMargin,
    """ScalaFile
      |  PsiWhiteSpace('\n')
      |  DocComment
      |    ScPsiDocToken(DOC_COMMENT_DATA)('/**')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_COMMENT_DATA)('Ordered list:')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('1. ')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_COMMENT_DATA)('First item')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('2. ')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_COMMENT_DATA)('Second item')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('3. ')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_COMMENT_DATA)('Third item')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n ')
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
      |    ScPsiDocToken(DOC_COMMENT_DATA)('/**')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_COMMENT_DATA)('Code block example:')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    ASTWrapperPsiElement(ScalaDocCodeBlock)
      |      ScPsiDocToken(DOC_COMMENT_DATA)('{{{\n * val x = 42\n * println(x)\n * }}}')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n ')
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
      |    ScPsiDocToken(DOC_COMMENT_DATA)('/**')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_COMMENT_DATA)('See [')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('[scala.collection.immutable.List]')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('] for more information.\n * Also check [')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('[scala.Option]')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('].')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n ')
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
      |    ScPsiDocToken(DOC_COMMENT_DATA)('/**')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_COMMENT_DATA)('A method description')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    DocTag
      |      ScPsiDocToken(DOC_COMMENT_DATA)('@param name The name parameter')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    DocTag
      |      ScPsiDocToken(DOC_COMMENT_DATA)('@param age The age parameter\n ')
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
      |    ScPsiDocToken(DOC_COMMENT_DATA)('/**')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_COMMENT_DATA)('Calculates something')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    DocTag
      |      ScPsiDocToken(DOC_COMMENT_DATA)('@return The calculated value\n ')
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
      |    ScPsiDocToken(DOC_COMMENT_DATA)('/**')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_COMMENT_DATA)('Does something risky')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    DocTag
      |      ScPsiDocToken(DOC_COMMENT_DATA)('@throws IllegalArgumentException if the argument is invalid')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    DocTag
      |      ScPsiDocToken(DOC_COMMENT_DATA)('@throws NullPointerException if the input is null\n ')
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
      |    ScPsiDocToken(DOC_COMMENT_DATA)('/**')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    DocSyntaxElement 256
      |      ScPsiDocToken(DOC_COMMENT_DATA)('#')
      |      ScPsiDocToken(DOC_COMMENT_DATA)(' Heading 1')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * \n * ')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_COMMENT_DATA)('This paragraph has ')
      |      DocSyntaxElement 1
      |        ScPsiDocToken(DOC_COMMENT_DATA)('**bold**')
      |      ScPsiDocToken(DOC_COMMENT_DATA)(' and ')
      |      DocSyntaxElement 2
      |        ScPsiDocToken(DOC_COMMENT_DATA)('*italic*')
      |      ScPsiDocToken(DOC_COMMENT_DATA)(' text.')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * \n * ')
      |    DocSyntaxElement 256
      |      ScPsiDocToken(DOC_COMMENT_DATA)('##')
      |      ScPsiDocToken(DOC_COMMENT_DATA)(' Heading 2')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * \n * ')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('- ')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_COMMENT_DATA)('List item with ')
      |      DocSyntaxElement 1
      |        ScPsiDocToken(DOC_COMMENT_DATA)('**bold**')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('- ')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_COMMENT_DATA)('List item with ')
      |      DocSyntaxElement 2
      |        ScPsiDocToken(DOC_COMMENT_DATA)('*italic*')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('- ')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_COMMENT_DATA)('List item with [')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('[scala.Option]')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('] reference')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n ')
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
      |    ScPsiDocToken(DOC_COMMENT_DATA)('/**')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_COMMENT_DATA)('Complex example with code and tags')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * \n * ')
      |    ASTWrapperPsiElement(ScalaDocCodeBlock)
      |      ScPsiDocToken(DOC_COMMENT_DATA)('{{{\n * def example(name: String): Option[Int] = {\n *   Some(name.length)\n * }\n * }}}')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * \n * ')
      |    DocTag
      |      ScPsiDocToken(DOC_COMMENT_DATA)('@param name The input name')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    DocTag
      |      ScPsiDocToken(DOC_COMMENT_DATA)('@return [[scala.Option]] containing the length')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    DocTag
      |      ScPsiDocToken(DOC_COMMENT_DATA)('@throws NullPointerException if name is null\n ')
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
        |    ScPsiDocToken(DOC_COMMENT_DATA)('/**')
        |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
        |    ScDocParagraph
        |      ScPsiDocToken(DOC_COMMENT_DATA)('Java-style inline tags:\n * {@link scala.Option}\n * {@literal literal text}')
        |    ScPsiDocToken(DOC_COMMENT_DATA)('\n ')
        |    ScPsiDocToken(DOC_COMMENT_END)('*/')
        |  PsiWhiteSpace('\n')""".stripMargin
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
      |    ScPsiDocToken(DOC_COMMENT_DATA)('/**')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    DocSyntaxElement 256
      |      ScPsiDocToken(DOC_COMMENT_DATA)('#')
      |      ScPsiDocToken(DOC_COMMENT_DATA)(' Complete Documentation Example')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * \n * ')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_COMMENT_DATA)('This is a paragraph with ')
      |      DocSyntaxElement 1
      |        ScPsiDocToken(DOC_COMMENT_DATA)('**bold**')
      |      ScPsiDocToken(DOC_COMMENT_DATA)(' and ')
      |      DocSyntaxElement 2
      |        ScPsiDocToken(DOC_COMMENT_DATA)('*italic*')
      |      ScPsiDocToken(DOC_COMMENT_DATA)(' text.\n * It also contains a code reference to [')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('[scala.collection.immutable.List]')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('].')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * \n * ')
      |    DocSyntaxElement 256
      |      ScPsiDocToken(DOC_COMMENT_DATA)('##')
      |      ScPsiDocToken(DOC_COMMENT_DATA)(' Code Examples')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * \n * ')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_COMMENT_DATA)('Here's a simple code example:')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    ASTWrapperPsiElement(ScalaDocCodeBlock)
      |      ScPsiDocToken(DOC_COMMENT_DATA)('{{{\n * val numbers = List(1, 2, 3, 4, 5)\n * val doubled = numbers.map(_ * 2)\n * }}}')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * \n * ')
      |    DocSyntaxElement 256
      |      ScPsiDocToken(DOC_COMMENT_DATA)('##')
      |      ScPsiDocToken(DOC_COMMENT_DATA)(' Java-style Inline Tags')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * \n * ')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_COMMENT_DATA)('{@link scala.Option}\n * {@literal literal text}')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * \n * ')
      |    DocSyntaxElement 256
      |      ScPsiDocToken(DOC_COMMENT_DATA)('##')
      |      ScPsiDocToken(DOC_COMMENT_DATA)(' Parameters')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * \n * ')
      |    DocTag
      |      ScPsiDocToken(DOC_COMMENT_DATA)('@param input The input string to process')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    DocTag
      |      ScPsiDocToken(DOC_COMMENT_DATA)('@param count The number of times to process\n * \n * ')
      |      DocSyntaxElement 256
      |        ScPsiDocToken(DOC_COMMENT_DATA)('##')
      |        ScPsiDocToken(DOC_COMMENT_DATA)(' Return Value')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    DocTag
      |      ScPsiDocToken(DOC_COMMENT_DATA)('@return An [[scala.Option]] containing the result\n * \n * ')
      |      DocSyntaxElement 256
      |        ScPsiDocToken(DOC_COMMENT_DATA)('##')
      |        ScPsiDocToken(DOC_COMMENT_DATA)(' Exceptions')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    DocTag
      |      ScPsiDocToken(DOC_COMMENT_DATA)('@throws IllegalArgumentException if input is empty')
      |    ScPsiDocToken(DOC_COMMENT_DATA)('\n * ')
      |    DocTag
      |      ScPsiDocToken(DOC_COMMENT_DATA)('@throws NullPointerException if input is null\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')""".stripMargin
  )
}
