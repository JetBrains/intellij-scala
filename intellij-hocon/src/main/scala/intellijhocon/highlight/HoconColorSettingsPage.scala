package intellijhocon
package highlight

import com.intellij.openapi.options.colors.{AttributesDescriptor, ColorSettingsPage}
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import scala.collection.JavaConverters._
import intellijhocon.lang.HoconLanguage
import intellijhocon.highlight.{HoconHighlighterColors => HHC}

class HoconColorSettingsPage extends ColorSettingsPage {
  def getIcon =
    AllIcons.FileTypes.Config

  def getDemoText =
    s"""<hashcomment># hash comment</hashcomment>
       |<doubleslashcomment>// double slash comment<doubleslashcomment>
       |
       |<include>include</include> <inclmod>classpath</inclmod><imparens>(</imparens><quotedstring>"included.conf"</quotedstring><imparens>)</imparens>
       |
       |<key>object</key><dot>.</dot><key>subobject</key> <braces>{</braces>
       |  <key>someList</key> <pathvalueseparator>=</pathvalueseparator> <brackets>[</brackets>
       |    <null>null</null><comma>,</comma>
       |    <boolean>true</boolean><comma>,</comma>
       |    <number>123.4e5</number><comma>,</comma>
       |    <unquotedstring>unquoted string </unquotedstring><badchar>*</badchar><comma>,</comma>
       |    <quotedstring>"quo</quotedstring><validstringescape>\\n</validstringescape><quotedstring>ted</quotedstring><invalidstringescape>\\d</invalidstringescape><quotedstring> string"</quotedstring><comma>,</comma>
       |    <substsign>$$</substsign><substbraces>{</substbraces><optsubstsign>?</optsubstsign><substkey>substitution</substkey><dot>.</dot><substkey>inner</substkey><substbraces>}</substbraces><comma>,</comma>
       |    <multilinestring>${"\"\"\""}multiline\n    multiline${"\"\"\""}</multilinestring>
       |  <brackets>]</brackets>
       |<braces>}</braces>
       |""".stripMargin.trim

  def getAdditionalHighlightingTagToDescriptorMap = Map(
    "badchar" -> HHC.BadCharacter,
    "hashcomment" -> HHC.HashComment,
    "doubleslashcomment" -> HHC.DoubleSlashComment,
    "null" -> HHC.Null,
    "boolean" -> HHC.Boolean,
    "number" -> HHC.Number,
    "quotedstring" -> HHC.QuotedString,
    "multilinestring" -> HHC.MultilineString,
    "validstringescape" -> HHC.ValidStringEscape,
    "invalidstringescape" -> HHC.InvalidStringEscape,
    "brackets" -> HHC.Brackets,
    "braces" -> HHC.Braces,
    "imparens" -> HHC.IncludeModifierParens,
    "substbraces" -> HHC.SubBraces,
    "pathvalueseparator" -> HHC.PathValueSeparator,
    "comma" -> HHC.Comma,
    "include" -> HHC.Include,
    "inclmod" -> HHC.IncludeModifier,
    "substsign" -> HHC.SubstitutionSign,
    "optsubstsign" -> HHC.OptionalSubstitutionSign,
    "unquotedstring" -> HHC.UnquotedString,
    "dot" -> HHC.PathSeparator,
    "key" -> HHC.FieldKey,
    "substkey" -> HHC.SubstitutionKey
  ).asJava

  def getHighlighter =
    SyntaxHighlighterFactory.getSyntaxHighlighter(HoconLanguage, null, null)

  def getDisplayName =
    "HOCON"

  def getColorDescriptors =
    Array.empty

  def getAttributeDescriptors =
    HoconColorSettingsPage.Attrs
}

object HoconColorSettingsPage {
  final val Attrs = Array(
    "Bad character" -> HHC.BadCharacter,
    "Hash comment" -> HHC.HashComment,
    "Double slash comment" -> HHC.DoubleSlashComment,
    "Null" -> HHC.Null,
    "Boolean" -> HHC.Boolean,
    "Number" -> HHC.Number,
    "Quoted string" -> HHC.QuotedString,
    "Multiline string" -> HHC.MultilineString,
    "Valid string escape" -> HHC.ValidStringEscape,
    "Invalid string escape" -> HHC.InvalidStringEscape,
    "Brackets" -> HHC.Brackets,
    "Braces" -> HHC.Braces,
    "Include modifier parens" -> HHC.IncludeModifierParens,
    "Substitution braces" -> HHC.SubBraces,
    "Path-value separator ('=', ':', '+=')" -> HHC.PathValueSeparator,
    "Comma" -> HHC.Comma,
    "Include keyword" -> HHC.Include,
    "Include modifier" -> HHC.IncludeModifier,
    "Substitution sign" -> HHC.SubstitutionSign,
    "Optional substitution sign" -> HHC.OptionalSubstitutionSign,
    "Unquoted string" -> HHC.UnquotedString,
    "Path separator" -> HHC.PathSeparator,
    "Key" -> HHC.FieldKey,
    "Key in substitution" -> HHC.SubstitutionKey
  ).map({
    case (displayName, key) => new AttributesDescriptor(displayName, key)
  })
}
