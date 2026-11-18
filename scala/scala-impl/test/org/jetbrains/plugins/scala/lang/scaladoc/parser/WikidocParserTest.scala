package org.jetbrains.plugins.scala.lang.scaladoc.parser

import com.intellij.psi.impl.DebugUtil.psiToString
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.parser.SimpleScalaParserTestBase
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocComment, ScDocInnerCodeElement}
import org.jetbrains.plugins.scala.project.ScalaFeatures
import org.junit.Assert.assertEquals

class WikidocParserTest extends SimpleScalaParserTestBase {
  override def checkTree(text: String, expectedTree: String): Unit = {
    checkWhitespaceTokensOnlyContainWhitespacs(expectedTree)
    super.checkTree(text, expectedTree)
  }

  def test_tag_after_brace_code_fence_in_description(): Unit =
    checkContentAfterBraceCodeFence(inParamTag = false, withFollowingProse = false)

  def test_prose_and_tag_after_brace_code_fence_in_description(): Unit =
    checkContentAfterBraceCodeFence(inParamTag = false, withFollowingProse = true)

  def test_tag_after_brace_code_fence_in_param_tag(): Unit =
    checkContentAfterBraceCodeFence(inParamTag = true, withFollowingProse = false)

  def test_prose_and_tag_after_brace_code_fence_in_param_tag(): Unit =
    checkContentAfterBraceCodeFence(inParamTag = true, withFollowingProse = true)

  private def checkContentAfterBraceCodeFence(
    inParamTag: Boolean,
    withFollowingProse: Boolean
  ): Unit = {
    val closingIndents = Seq("", "          ", "\t")
    for (closingIndent <- closingIndents) {
      checkContentAfterBraceCodeFence(inParamTag, withFollowingProse, closingIndent)
    }
  }

  private def checkContentAfterBraceCodeFence(
    inParamTag: Boolean,
    withFollowingProse: Boolean,
    closingIndent: String
  ): Unit = {
    val commentText = braceCodeFenceComment(inParamTag, withFollowingProse, closingIndent)
    val comment = parseDocComment(commentText)
    val clue = psiToString(comment, true)
    val expectedTags = if (inParamTag) Seq("param", "note") else Seq("note")
    assertEquals(clue, expectedTags, comment.tags.map(_.getName))

    val codeBlock = checkClosedCodeBlock(comment)
    assert(!codeBlock.getText.contains("@note"), clue)
    assert(comment.tags.last.getText.contains("Following note."), clue)
    if (withFollowingProse) {
      assert(!codeBlock.getText.contains("After the code."), clue)
      val description = if (inParamTag) comment.tags.head.getText else comment.descriptionParts.map(_.getText).mkString
      assert(description.contains("After the code."), clue)
    }
  }

  private def braceCodeFenceComment(
    inParamTag: Boolean,
    withFollowingProse: Boolean,
    closingIndent: String
  ): String = {
    val firstTag = if (inParamTag) "@param value " else ""
    val followingProse = if (withFollowingProse) "After the code.\n * " else ""
    s"""/**
       | * $firstTag{{{
       | *   val x = 42
       | * ${closingIndent}}}}
       | * $followingProse@note Following note.
       | */""".stripMargin
  }

  private def parseDocComment(
    text: String
  ): ScDocComment = {
    val file = parseScalaFile(
      s"class Example {\n$text\n  def method(value: Int): Int = value\n}",
      ScalaFeatures.defaultScala2
    )
    file.depthFirst().findByType[ScDocComment].get
  }

  private def checkClosedCodeBlock(
    comment: ScDocComment
  ): ScDocInnerCodeElement = {
    val clue = psiToString(comment, true)
    val codeBlocks = comment.depthFirst().collect { case code: ScDocInnerCodeElement => code }.toSeq
    assertEquals(clue, 1, codeBlocks.size)
    val codeBlock = codeBlocks.head
    assertEquals(clue, "}}}", codeBlock.getLastChild.getText)
    codeBlock
  }


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
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)('\n')
      |      ScPsiDocToken(DOC_COMMENT_DATA)('object Blub')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n')
      |""".stripMargin
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
      |      ScDocThrowTagValueImpl(DOC_TAG_VALUE_TOKEN)
      |        ScDocReferenceLink
      |          ScDocRefQuerySegment('IllegalArgumentException')
      |            PsiElement(identifier)('IllegalArgumentException')
      |      ScDocParagraph
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('if the argument is invalid')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |        ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    DocTag
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_TAG_NAME)('@throws')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScDocThrowTagValueImpl(DOC_TAG_VALUE_TOKEN)
      |        ScDocReferenceLink
      |          ScDocRefQuerySegment('NullPointerException')
      |            PsiElement(identifier)('NullPointerException')
      |      ScDocParagraph
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('if the input is null')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_more_complicated_throws(): Unit = checkTree(
    s"""
      |/**
      | * @throws
      | * @throws${" "}
      | * @throws Target!.func()
      | * @throws Target!.func()${" "}
      | * @throws Target!.func() some exception
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
      |      ScPsiDocToken(DOC_TAG_NAME)('@throws')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScDocThrowTagValueImpl(DOC_TAG_VALUE_TOKEN)
      |        ScDocReferenceLink
      |          ScDocRefQuerySegment(<error>)
      |            PsiErrorElement:Identifier, 'this', or 'package' expected
      |              <empty list>
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    DocTag
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_TAG_NAME)('@throws')
      |      ScPsiDocToken(DOC_WHITESPACE)(' \n ')
      |      ScDocThrowTagValueImpl(DOC_TAG_VALUE_TOKEN)
      |        ScDocReferenceLink
      |          ScDocRefQuerySegment(<error>)
      |            PsiErrorElement:Identifier, 'this', or 'package' expected
      |              <empty list>
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    DocTag
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_TAG_NAME)('@throws')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScDocThrowTagValueImpl(DOC_TAG_VALUE_TOKEN)
      |        ScDocReferenceLink
      |          ScDocRefQuerySegment('func')
      |            ScDocRefQuerySegment('Target!')
      |              PsiElement(identifier)('Target!')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('func')
      |          PsiElement(()('(')
      |          PsiElement())(')')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    DocTag
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_TAG_NAME)('@throws')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScDocThrowTagValueImpl(DOC_TAG_VALUE_TOKEN)
      |        ScDocReferenceLink
      |          ScDocRefQuerySegment('func')
      |            ScDocRefQuerySegment('Target!')
      |              PsiElement(identifier)('Target!')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('func')
      |          PsiElement(()('(')
      |          PsiElement())(')')
      |      ScPsiDocToken(DOC_WHITESPACE)(' \n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    DocTag
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScPsiDocToken(DOC_TAG_NAME)('@throws')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      ScDocThrowTagValueImpl(DOC_TAG_VALUE_TOKEN)
      |        ScDocReferenceLink
      |          ScDocRefQuerySegment('func')
      |            ScDocRefQuerySegment('Target!')
      |              PsiElement(identifier)('Target!')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('func')
      |          PsiElement(()('(')
      |          PsiElement())(')')
      |      ScDocParagraph
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('some exception')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
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

  def test_ref_link(): Unit = checkTree(
    """
      |/**
      | * [[]]
      | * [[ref.ref]]
      | * [[ref.ref Some alt text]]
      | * [[ref.ref Some
      | *           text in next line]]
      | * [[ ref?]]
      | * [[ref  ]]
      | * [[
      | * [[ref
      | * [[ref Text
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
      |      DocSyntaxElement 64
      |        ScPsiDocToken(DOC_LINK_TAG 64)('[[')
      |        ScDocReferenceLink
      |          ScDocRefQuerySegment(<error>)
      |            PsiErrorElement:Identifier, 'this', or 'package' expected
      |              <empty list>
      |        ScPsiDocToken(DOC_LINK_CLOSE_TAG 0)(']]')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      DocSyntaxElement 64
      |        ScPsiDocToken(DOC_LINK_TAG 64)('[[')
      |        ScDocReferenceLink
      |          ScDocRefQuerySegment('ref')
      |            ScDocRefQuerySegment('ref')
      |              PsiElement(identifier)('ref')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('ref')
      |        ScPsiDocToken(DOC_LINK_CLOSE_TAG 0)(']]')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      DocSyntaxElement 64
      |        ScPsiDocToken(DOC_LINK_TAG 64)('[[')
      |        ScDocReferenceLink
      |          ScDocRefQuerySegment('ref')
      |            ScDocRefQuerySegment('ref')
      |              PsiElement(identifier)('ref')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('ref')
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('Some alt text')
      |        ScPsiDocToken(DOC_LINK_CLOSE_TAG 0)(']]')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      DocSyntaxElement 64
      |        ScPsiDocToken(DOC_LINK_TAG 64)('[[')
      |        ScDocReferenceLink
      |          ScDocRefQuerySegment('ref')
      |            ScDocRefQuerySegment('ref')
      |              PsiElement(identifier)('ref')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('ref')
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('Some')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |        ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |        ScPsiDocToken(DOC_WHITESPACE)('           ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('text in next line')
      |        ScPsiDocToken(DOC_LINK_CLOSE_TAG 0)(']]')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      DocSyntaxElement 64
      |        ScPsiDocToken(DOC_LINK_TAG 64)('[[')
      |        ScDocReferenceLink
      |          ScDocRefQuerySegment(<error>)
      |            PsiErrorElement:Identifier, 'this', or 'package' expected
      |              <empty list>
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('ref?')
      |        ScPsiDocToken(DOC_LINK_CLOSE_TAG 0)(']]')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      DocSyntaxElement 64
      |        ScPsiDocToken(DOC_LINK_TAG 64)('[[')
      |        ScDocReferenceLink
      |          ScDocRefQuerySegment('ref')
      |            PsiElement(identifier)('ref')
      |        ScPsiDocToken(DOC_WHITESPACE)('  ')
      |        ScPsiDocToken(DOC_LINK_CLOSE_TAG 0)(']]')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      DocSyntaxElement 64
      |        ScPsiDocToken(DOC_LINK_TAG 64)('[[')
      |        ScDocReferenceLink
      |          ScDocRefQuerySegment(<error>)
      |            PsiErrorElement:Identifier, 'this', or 'package' expected
      |              <empty list>
      |        PsiErrorElement:Expected description or closing link tag
      |          <empty list>
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      DocSyntaxElement 64
      |        ScPsiDocToken(DOC_LINK_TAG 64)('[[')
      |        ScDocReferenceLink
      |          ScDocRefQuerySegment('ref')
      |            PsiElement(identifier)('ref')
      |        PsiErrorElement:Expected description or closing link tag
      |          <empty list>
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      DocSyntaxElement 64
      |        ScPsiDocToken(DOC_LINK_TAG 64)('[[')
      |        ScDocReferenceLink
      |          ScDocRefQuerySegment('ref')
      |            PsiElement(identifier)('ref')
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('Text')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |        PsiErrorElement:No closing element
      |          <empty list>
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_http_link(): Unit = checkTree(
    """
      |/**
      | * [[http://google.com]]
      | * [[http://google.com Some alt text]]
      | * [[https://google.com]]
      | * [[https://google.com Some alt text]]
      | *
      | * [[http://google.com
      | * [[http://google.com Some alt text
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
      |      DocSyntaxElement 128
      |        ScPsiDocToken(DOC_HTTP_LINK_TAG 128)('[[')
      |        ScPsiDocToken(DOC_HTTP_LINK_VALUE)('http://google.com')
      |        ScPsiDocToken(DOC_LINK_CLOSE_TAG 0)(']]')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      DocSyntaxElement 128
      |        ScPsiDocToken(DOC_HTTP_LINK_TAG 128)('[[')
      |        ScPsiDocToken(DOC_HTTP_LINK_VALUE)('http://google.com')
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('Some alt text')
      |        ScPsiDocToken(DOC_LINK_CLOSE_TAG 0)(']]')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      DocSyntaxElement 128
      |        ScPsiDocToken(DOC_HTTP_LINK_TAG 128)('[[')
      |        ScPsiDocToken(DOC_HTTP_LINK_VALUE)('https://google.com')
      |        ScPsiDocToken(DOC_LINK_CLOSE_TAG 0)(']]')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      DocSyntaxElement 128
      |        ScPsiDocToken(DOC_HTTP_LINK_TAG 128)('[[')
      |        ScPsiDocToken(DOC_HTTP_LINK_VALUE)('https://google.com')
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('Some alt text')
      |        ScPsiDocToken(DOC_LINK_CLOSE_TAG 0)(']]')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |    ScDocParagraph
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      DocSyntaxElement 128
      |        ScPsiDocToken(DOC_HTTP_LINK_TAG 128)('[[')
      |        ScPsiDocToken(DOC_HTTP_LINK_VALUE)('http://google.com')
      |        PsiErrorElement:Expected description or closing link tag
      |          <empty list>
      |      ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |      ScPsiDocToken(DOC_COMMENT_LEADING_ASTERISKS)('*')
      |      ScPsiDocToken(DOC_WHITESPACE)(' ')
      |      DocSyntaxElement 128
      |        ScPsiDocToken(DOC_HTTP_LINK_TAG 128)('[[')
      |        ScPsiDocToken(DOC_HTTP_LINK_VALUE)('http://google.com')
      |        ScPsiDocToken(DOC_WHITESPACE)(' ')
      |        ScPsiDocToken(DOC_COMMENT_DATA)('Some alt text')
      |        ScPsiDocToken(DOC_WHITESPACE)('\n ')
      |        PsiErrorElement:No closing element
      |          <empty list>
      |    ScPsiDocToken(DOC_COMMENT_END)('*/')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )
}
