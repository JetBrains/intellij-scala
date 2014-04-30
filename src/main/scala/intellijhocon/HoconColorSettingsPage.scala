package intellijhocon

import com.intellij.openapi.options.colors.{AttributesDescriptor, ColorSettingsPage}
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import intellijhocon.{HoconHighlighterColors => HHC}
import scala.collection.JavaConverters._

class HoconColorSettingsPage extends ColorSettingsPage {
  def getIcon =
    AllIcons.FileTypes.Config

  def getDemoText =
    s"""<hashcomment># hash comment</hashcomment>
       |<doubleslashcomment>// double slash comment<doubleslashcomment>
       |
       |<include>include</include> <inclmod>classpath</inclmod><imparens>(</imparens><quotedstring>"included.conf"</quotedstring><imparens>)</imparens>
       |
       |<pathel>object</pathel><dot>.</dot><pathel>subobject</pathel> <braces>{</braces>
       |    <pathel>someList</pathel> <pathvalueseparator>=</pathvalueseparator> <brackets>[</brackets>
       |        <null>null</null><comma>,</comma>
       |        <boolean>true</boolean><comma>,</comma>
       |        <number>123.4e5</number><comma>,</comma>
       |        <unquotedstring>unquoted string </unquotedstring><badchar>*</badchar><comma>,</comma>
       |        <quotedstring>"quo</quotedstring><validstringescape>\\n</validstringescape><quotedstring>ted</quotedstring><invalidstringescape>\\d</invalidstringescape><quotedstring> string"</quotedstring><comma>,</comma>
       |        <refsign>$$</refsign><refbraces>{</refbraces><optrefsign>?</optrefsign><refpathel>reference</refpathel><dot>.</dot><refpathel>inner</refpathel><refbraces>}</refbraces><comma>,</comma>
       |        <multilinestring>${"\"\"\""}multiline\n        multiline${"\"\"\""}</multilinestring>
       |    <brackets>]</brackets>
       |<braces>}</braces>
       |
    """.stripMargin

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
    "refbraces" -> HHC.RefBraces,
    "pathvalueseparator" -> HHC.PathValueSeparator,
    "comma" -> HHC.Comma,
    "include" -> HHC.Include,
    "inclmod" -> HHC.IncludeModifier,
    "refsign" -> HHC.ReferenceSign,
    "optrefsign" -> HHC.OptionalReferenceSign,
    "unquotedstring" -> HHC.UnquotedString,
    "dot" -> HHC.PathSeparator,
    "pathel" -> HHC.PathElement,
    "refpathel" -> HHC.ReferencePathElement
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
    "Reference braces" -> HHC.RefBraces,
    "Path-value separator ('=', ':', '+=')" -> HHC.PathValueSeparator,
    "Comma" -> HHC.Comma,
    "Include keyword" -> HHC.Include,
    "Include modifier" -> HHC.IncludeModifier,
    "Reference sign" -> HHC.ReferenceSign,
    "Optional reference sign" -> HHC.OptionalReferenceSign,
    "Unquoted string" -> HHC.UnquotedString,
    "Path separator" -> HHC.PathSeparator,
    "Path element" -> HHC.PathElement,
    "Path element in reference" -> HHC.ReferencePathElement
  ).map({ case (displayName, key) => new AttributesDescriptor(displayName, key)})
}
