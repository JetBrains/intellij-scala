package org.jetbrains.plugins.hocon.highlight

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.{HighlighterColors, DefaultLanguageHighlighterColors => DLHC}

object HoconHighlighterColors {
  final val BadCharacter = key("HOCON_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)
  final val HashComment = key("HOCON_HASH_COMMENT", DLHC.LINE_COMMENT)
  final val DoubleSlashComment = key("HOCON_DOUBLE_SLASH_COMMENT", DLHC.LINE_COMMENT)
  final val Null = key("HOCON_NULL", DLHC.KEYWORD)
  final val Boolean = key("HOCON_BOOLEAN", DLHC.KEYWORD)
  final val Number = key("HOCON_NUMBER", DLHC.NUMBER)
  final val QuotedString = key("HOCON_QUOTED_STRING", DLHC.STRING)
  final val MultilineString = key("HOCON_MULTILINE_STRING", DLHC.STRING)
  final val ValidStringEscape = key("HOCON_VALID_STRING_ESCAPE", DLHC.VALID_STRING_ESCAPE)
  final val InvalidStringEscape = key("HOCON_INVALID_STRING_ESCAPE", DLHC.INVALID_STRING_ESCAPE)
  final val Brackets = key("HOCON_BRACKETS", DLHC.BRACKETS)
  final val Braces = key("HOCON_OBJECT_BRACES", DLHC.BRACES)
  final val IncludeModifierParens = key("HOCON_INCLUDE_MODIFIER_PARENS", DLHC.PARENTHESES)
  final val SubBraces = key("HOCON_SUBSTITUTION_BRACES", DLHC.BRACES)
  final val KeyValueSeparator = key("HOCON_KEY_VALUE_SEPARATOR", DLHC.OPERATION_SIGN)
  final val Comma = key("HOCON_COMMA", DLHC.COMMA)
  final val Include = key("HOCON_INCLUDE", DLHC.KEYWORD)
  // TODO create a language-specific highlight info type
  final val IncludeModifier = key("HOCON_INCLUDE_MODIFIER", HighlightInfoType.STATIC_METHOD.getAttributesKey)
  final val SubstitutionSign = key("HOCON_SUBSTITUTION_SIGN", DLHC.OPERATION_SIGN)
  final val OptionalSubstitutionSign = key("HOCON_OPTIONAL_SUBSTITUTION_SIGN", DLHC.OPERATION_SIGN)
  final val UnquotedString = key("HOCON_UNQUOTED_STRING", DLHC.IDENTIFIER)
  final val PathSeparator = key("PATH_SEPARATOR", DLHC.DOT)
  final val EntryKey = key("ENTRY_KEY", DLHC.INSTANCE_METHOD)
  final val SubstitutionKey = key("SUBSTITUTION_KEY", DLHC.INSTANCE_FIELD)

  private def key(name: String, prototype: TextAttributesKey) =
    TextAttributesKey.createTextAttributesKey(name, prototype)

}
