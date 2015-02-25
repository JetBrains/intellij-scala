package org.jetbrains.plugins.hocon.formatting

import com.intellij.formatting.{Alignment, Indent, Spacing, Wrap}
import com.intellij.lang.ASTNode
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.hocon.codestyle.HoconCustomCodeStyleSettings
import org.jetbrains.plugins.hocon.lang.HoconLanguage

class HoconFormatter(settings: CodeStyleSettings) {

  import org.jetbrains.plugins.hocon.CommonUtil._
  import org.jetbrains.plugins.hocon.lexer.HoconTokenSets._
  import org.jetbrains.plugins.hocon.lexer.HoconTokenType._
  import org.jetbrains.plugins.hocon.parser.HoconElementSets._
  import org.jetbrains.plugins.hocon.parser.HoconElementType._

  val commonSettings = settings.getCommonSettings(HoconLanguage)
  val customSettings = settings.getCustomSettings(classOf[HoconCustomCodeStyleSettings])

  private def beforeCommentOnNewLineSpacing(parent: ASTNode, comment: ASTNode) = {
    val maxBlankLines = getMaxBlankLines(parent.getElementType, comment.getElementType)
    comment.getElementType match {
      case HashComment if customSettings.HASH_COMMENTS_AT_FIRST_COLUMN =>
        Spacing.createKeepingFirstColumnSpacing(0, 0, true, maxBlankLines)
      case DoubleSlashComment if customSettings.DOUBLE_SLASH_COMMENTS_AT_FIRST_COLUMN =>
        Spacing.createKeepingFirstColumnSpacing(0, 0, true, maxBlankLines)
      case _ =>
        Spacing.createSpacing(0, 0, 0, true, maxBlankLines)
    }
  }

  private def getMaxBlankLines(parentType: IElementType, rightChildType: IElementType) =
    (parentType, rightChildType) match {
      case (_, RBrace) => customSettings.KEEP_BLANK_LINES_BEFORE_RBRACE
      case (_, RBracket) => customSettings.KEEP_BLANK_LINES_BEFORE_RBRACKET
      case (Array, _) => customSettings.KEEP_BLANK_LINES_IN_LISTS
      case _ => customSettings.KEEP_BLANK_LINES_IN_OBJECTS
    }

  def getFirstSpacing(parent: ASTNode, firstChild: ASTNode) =
    if (Comment.contains(firstChild.getElementType))
      beforeCommentOnNewLineSpacing(parent, firstChild)
    else
      Spacing.createSpacing(0, 0, 0, true, getMaxBlankLines(parent.getElementType, firstChild.getElementType))

  def getSpacing(parent: ASTNode, leftChild: ASTNode, rightChild: ASTNode) = {

    val keepLineBreaks = commonSettings.KEEP_LINE_BREAKS
    val maxBlankLines = getMaxBlankLines(parent.getElementType, rightChild.getElementType)

    def dependentLFSpacing(shouldBeSpace: Boolean) = {
      val spaces = if (shouldBeSpace) 1 else 0
      Spacing.createDependentLFSpacing(spaces, spaces, parent.getTextRange,
        keepLineBreaks, maxBlankLines)
    }

    def normalSpacing(shouldBeSpace: Boolean) = {
      val spaces = if (shouldBeSpace) 1 else 0
      Spacing.createSpacing(spaces, spaces, 0, keepLineBreaks, maxBlankLines)
    }

    val lineBreakEnsuringSpacing =
      Spacing.createSpacing(0, 0, 1, keepLineBreaks, maxBlankLines)

    val isLineBreakBetween = parent.getText.subSequence(
      leftChild.getTextRange.getEndOffset - parent.getTextRange.getStartOffset,
      rightChild.getTextRange.getStartOffset - parent.getTextRange.getStartOffset)
            .charIterator.contains('\n')

    def standardSpacing = (leftChild.getElementType, rightChild.getElementType) match {
      case (LBrace, RBrace) =>
        normalSpacing(commonSettings.SPACE_WITHIN_BRACES)

      case (LBrace, Include | BareObjectField) =>
        if (customSettings.OBJECTS_NEW_LINE_AFTER_LBRACE)
          dependentLFSpacing(commonSettings.SPACE_WITHIN_BRACES)
        else
          normalSpacing(commonSettings.SPACE_WITHIN_BRACES)

      case (Include | BareObjectField, Include | BareObjectField) =>
        lineBreakEnsuringSpacing

      case (Include | BareObjectField, Comma) =>
        normalSpacing(commonSettings.SPACE_BEFORE_COMMA)

      case (Comma, BareObjectField | Include) =>
        normalSpacing(commonSettings.SPACE_AFTER_COMMA)

      case (BareObjectField | Include | Comma, RBrace) =>
        if (customSettings.OBJECTS_RBRACE_ON_NEXT_LINE)
          dependentLFSpacing(commonSettings.SPACE_WITHIN_BRACES)
        else
          normalSpacing(commonSettings.SPACE_WITHIN_BRACES)

      case (LBracket, RBracket) =>
        normalSpacing(commonSettings.SPACE_WITHIN_BRACKETS)

      case (LBracket, Value.extractor()) =>
        if (customSettings.LISTS_NEW_LINE_AFTER_LBRACKET)
          dependentLFSpacing(commonSettings.SPACE_WITHIN_BRACKETS)
        else
          normalSpacing(commonSettings.SPACE_WITHIN_BRACKETS)

      case (Value.extractor(), Value.extractor()) =>
        lineBreakEnsuringSpacing

      case (Value.extractor(), Comma) =>
        normalSpacing(commonSettings.SPACE_BEFORE_COMMA)

      case (Comma, Value.extractor()) =>
        normalSpacing(commonSettings.SPACE_AFTER_COMMA)

      case (Value.extractor() | Comma, RBracket) =>
        if (customSettings.LISTS_RBRACKET_ON_NEXT_LINE)
          dependentLFSpacing(commonSettings.SPACE_WITHIN_BRACKETS)
        else
          normalSpacing(commonSettings.SPACE_WITHIN_BRACKETS)

      case (UnquotedChars, Included) =>
        normalSpacing(shouldBeSpace = true)

      case (FieldPath, Object) =>
        normalSpacing(customSettings.SPACE_BEFORE_LBRACE_AFTER_PATH)

      case (FieldPath, Colon) =>
        normalSpacing(customSettings.SPACE_BEFORE_COLON)

      case (FieldPath, Equals | PlusEquals) =>
        normalSpacing(customSettings.SPACE_BEFORE_ASSIGNMENT)

      case (Colon, Value.extractor()) =>
        normalSpacing(customSettings.SPACE_AFTER_COLON)

      case (Equals | PlusEquals, Value.extractor()) =>
        normalSpacing(customSettings.SPACE_AFTER_ASSIGNMENT)

      case (Dollar, SubLBrace) | (SubLBrace, QMark) =>
        Spacing.getReadOnlySpacing

      case (SubLBrace, SubstitutionPath | SubRBrace) =>
        normalSpacing(customSettings.SPACE_WITHIN_SUBSTITUTION_BRACES)

      case (QMark, SubstitutionPath) =>
        normalSpacing(customSettings.SPACE_AFTER_QMARK)

      case (SubstitutionPath, SubRBrace) =>
        normalSpacing(customSettings.SPACE_WITHIN_SUBSTITUTION_BRACES)

      case (UnquotedChars, String) | (String, UnquotedChars) if parent.getElementType == Included =>
        normalSpacing(commonSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES)

      case _ =>
        null

    }

    if (Comment.contains(rightChild.getElementType)) {
      if (isLineBreakBetween)
        beforeCommentOnNewLineSpacing(parent, rightChild)
      else
        null
    } else if (Comment.contains(leftChild.getElementType))
      Spacing.createSafeSpacing(true, maxBlankLines)
    else if (parent.getElementType == Concatenation)
      Spacing.getReadOnlySpacing
    else
      standardSpacing
  }

  // Formatter must be able to return exactly the same instance of Wrap and Alignment objects
  // for children of the same parent and these two classes are one way to make it possible.

  class WrapCache(pathValueSeparator: Option[IElementType]) {
    val objectEntryWrap =
      Wrap.createWrap(customSettings.OBJECTS_WRAP, false)

    val arrayValueWrap =
      Wrap.createWrap(customSettings.LISTS_WRAP, false)

    val fieldPathWrap = pathValueSeparator match {
      case Some(Colon) =>
        Wrap.createWrap(customSettings.OBJECT_FIELDS_WITH_COLON_WRAP, true)
      case Some(Equals | PlusEquals) =>
        Wrap.createWrap(customSettings.OBJECT_FIELDS_WITH_ASSIGNMENT_WRAP, true)
      case _ => null
    }

    val pathValueSeparatorWrap = pathValueSeparator match {
      case Some(Colon) if customSettings.OBJECT_FIELDS_COLON_ON_NEXT_LINE =>
        fieldPathWrap
      case Some(Equals | PlusEquals) if customSettings.OBJECT_FIELDS_ASSIGNMENT_ON_NEXT_LINE =>
        fieldPathWrap
      case _ => null
    }

    val fieldValueWrap =
      if (pathValueSeparatorWrap == null) fieldPathWrap else null

    val includeInnerWrap =
      Wrap.createWrap(customSettings.INCLUDED_RESOURCE_WRAP, true)

  }

  class AlignmentCache {
    val objectEntryAlignment =
      if (customSettings.OBJECTS_ALIGN_WHEN_MULTILINE) Alignment.createAlignment else null

    val arrayValueAlignment =
      if (customSettings.LISTS_ALIGN_WHEN_MULTILINE) Alignment.createAlignment else null
  }


  def getWrap(wrapCache: WrapCache, parent: ASTNode, child: ASTNode) =
    (parent.getElementType, child.getElementType) match {
      case (Object, Include | BareObjectField) =>
        wrapCache.objectEntryWrap

      case (Array, Value.extractor()) =>
        wrapCache.arrayValueWrap

      case (BareObjectField, FieldPath) =>
        wrapCache.fieldPathWrap

      case (BareObjectField, PathValueSeparator.extractor()) =>
        wrapCache.pathValueSeparatorWrap

      case (BareObjectField, Value.extractor()) =>
        wrapCache.fieldValueWrap

      case (Include, _) =>
        wrapCache.includeInnerWrap

      case _ => null
    }

  def getAlignment(alignmentCache: AlignmentCache, parent: ASTNode, child: ASTNode) =
    (parent.getElementType, child.getElementType) match {
      case (Object, Include | BareObjectField | Comment.extractor()) =>
        alignmentCache.objectEntryAlignment

      case (Array, Value.extractor() | Comment.extractor()) =>
        alignmentCache.arrayValueAlignment

      case _ => null
    }

  def getIndent(parent: ASTNode, child: ASTNode) =
    (parent.getElementType, child.getElementType) match {
      case (Object, Include | BareObjectField | Comma | Comment.extractor()) |
           (Array, Value.extractor() | Comma | Comment.extractor()) =>
        Indent.getNormalIndent
      case (Include, Included) |
           (BareObjectField, PathValueSeparator.extractor() | Value.extractor()) =>
        Indent.getContinuationIndent
      case _ =>
        Indent.getNoneIndent

    }

  def getChildIndent(parent: ASTNode) = parent.getElementType match {
    case Object | Array => Indent.getNormalIndent
    case Include | BareObjectField => Indent.getContinuationIndent
    case _ => Indent.getNoneIndent
  }

  def getChildAlignment(alignmentCache: AlignmentCache, parent: ASTNode) = parent.getElementType match {
    case Object => alignmentCache.objectEntryAlignment
    case Array => alignmentCache.arrayValueAlignment
    case _ => null
  }

  def getChildren(node: ASTNode): Iterator[ASTNode] = node.getElementType match {
    case ForcedLeafBlock.extractor() =>
      Iterator.empty
    case HoconFileElementType | Object =>
      node.childrenIterator.flatMap(child => child.getElementType match {
        case ObjectEntries => getChildren(child)
        case _ => Iterator(child)
      })
    case ObjectEntries =>
      node.childrenIterator.flatMap(child => child.getElementType match {
        case ObjectField => getChildren(child)
        case _ => Iterator(child)
      })
    case _ => node.childrenIterator
  }
}
