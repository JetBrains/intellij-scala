package org.jetbrains.plugins.scala.highlighter

import com.intellij.codeHighlighting.RainbowHighlighter
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.{SyntaxHighlighter, SyntaxHighlighterFactory}
import com.intellij.openapi.options.colors.{AttributesDescriptor, ColorDescriptor, RainbowColorSettingsPage}
import org.jetbrains.plugins.scala.highlighter.DefaultHighlighter._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaLanguage}

import java.util
import javax.swing.Icon

object ScalaColorsAndFontsPage {

  private val ourDescriptors = Array(
    new AttributesDescriptor(DisplayNames.KEYWORD, KEYWORD),
    new AttributesDescriptor(DisplayNames.NUMBER, NUMBER),
    new AttributesDescriptor(DisplayNames.STRING, STRING),
    new AttributesDescriptor(DisplayNames.VALID_STRING_ESCAPE, VALID_STRING_ESCAPE),
    new AttributesDescriptor(DisplayNames.INVALID_STRING_ESCAPE, INVALID_STRING_ESCAPE),
    new AttributesDescriptor(DisplayNames.ASSIGN, ASSIGN),
    new AttributesDescriptor(DisplayNames.ARROW, ARROW),
    new AttributesDescriptor(DisplayNames.PARENTHESES, PARENTHESES),
    new AttributesDescriptor(DisplayNames.BRACES, BRACES),
    new AttributesDescriptor(DisplayNames.BRACKETS, BRACKETS),
    new AttributesDescriptor(DisplayNames.COLON, COLON),
    new AttributesDescriptor(DisplayNames.SEMICOLON, SEMICOLON),
    new AttributesDescriptor(DisplayNames.DOT, DOT),
    new AttributesDescriptor(DisplayNames.COMMA, COMMA),
    new AttributesDescriptor(DisplayNames.MUTABLE_COLLECTION, MUTABLE_COLLECTION),
    new AttributesDescriptor(DisplayNames.IMMUTABLE_COLLECTION, IMMUTABLE_COLLECTION),
    new AttributesDescriptor(DisplayNames.JAVA_COLLECTION, JAVA_COLLECTION),
    new AttributesDescriptor(DisplayNames.INTERPOLATED_STRING_INJECTION, INTERPOLATED_STRING_INJECTION),
    new AttributesDescriptor(DisplayNames.LINE_COMMENT, LINE_COMMENT),
    new AttributesDescriptor(DisplayNames.BLOCK_COMMENT, BLOCK_COMMENT),
    new AttributesDescriptor(DisplayNames.DOC_COMMENT, DOC_COMMENT),
    new AttributesDescriptor(DisplayNames.SCALA_DOC_TAG, SCALA_DOC_TAG),
    new AttributesDescriptor(DisplayNames.SCALA_DOC_HTML_TAG, SCALA_DOC_HTML_TAG),
    new AttributesDescriptor(DisplayNames.SCALA_DOC_WIKI_SYNTAX, SCALA_DOC_WIKI_SYNTAX),
    new AttributesDescriptor(DisplayNames.SCALA_DOC_HTML_ESCAPE, SCALA_DOC_HTML_ESCAPE),
    new AttributesDescriptor(DisplayNames.SCALA_DOC_TAG_PARAM_VALUE, SCALA_DOC_TAG_PARAM_VALUE),
    new AttributesDescriptor(DisplayNames.IMPLICIT_CONVERSIONS, IMPLICIT_CONVERSIONS),
    new AttributesDescriptor(DisplayNames.CLASS, CLASS),
    new AttributesDescriptor(DisplayNames.ABSTRACT_CLASS, ABSTRACT_CLASS),
    new AttributesDescriptor(DisplayNames.OBJECT, OBJECT),
    new AttributesDescriptor(DisplayNames.TRAIT, TRAIT),
    new AttributesDescriptor(DisplayNames.ENUM, ENUM),
    new AttributesDescriptor(DisplayNames.ENUM_SINGLETON_CASE, ENUM_SINGLETON_CASE),
    new AttributesDescriptor(DisplayNames.ENUM_CLASS_CASE, ENUM_CLASS_CASE),
    new AttributesDescriptor(DisplayNames.TYPEPARAM, TYPEPARAM),
    new AttributesDescriptor(DisplayNames.TYPE_ALIAS, TYPE_ALIAS),
    new AttributesDescriptor(DisplayNames.PREDEF, PREDEF),
    new AttributesDescriptor(DisplayNames.LOCAL_VALUES, LOCAL_VALUES),
    new AttributesDescriptor(DisplayNames.LOCAL_VARIABLES, LOCAL_VARIABLES),
    new AttributesDescriptor(DisplayNames.LOCAL_LAZY, LOCAL_LAZY),
    new AttributesDescriptor(DisplayNames.VALUES, VALUES),
    new AttributesDescriptor(DisplayNames.VARIABLES, VARIABLES),
    new AttributesDescriptor(DisplayNames.LAZY, LAZY),
    new AttributesDescriptor(DisplayNames.PARAMETER, PARAMETER),
    new AttributesDescriptor(DisplayNames.PARAMETER_OF_ANONIMOUS_FUNCTION, PARAMETER_OF_ANONIMOUS_FUNCTION),
    new AttributesDescriptor(DisplayNames.PATTERN, PATTERN),
    new AttributesDescriptor(DisplayNames.GENERATOR, GENERATOR),
    new AttributesDescriptor(DisplayNames.METHOD_CALL, METHOD_CALL),
    new AttributesDescriptor(DisplayNames.OBJECT_METHOD_CALL, OBJECT_METHOD_CALL),
    new AttributesDescriptor(DisplayNames.LOCAL_METHOD_CALL, LOCAL_METHOD_CALL),
    new AttributesDescriptor(DisplayNames.METHOD_DECLARATION, METHOD_DECLARATION),
    new AttributesDescriptor(DisplayNames.BAD_CHARACTER, BAD_CHARACTER),
    new AttributesDescriptor(DisplayNames.ANNOTATION, ANNOTATION),
    new AttributesDescriptor(DisplayNames.ANNOTATION_ATTRIBUTE, ANNOTATION_ATTRIBUTE),
    new AttributesDescriptor(DisplayNames.XML_TAG, XML_TAG),
    new AttributesDescriptor(DisplayNames.XML_TAG_NAME, XML_TAG_NAME),
    new AttributesDescriptor(DisplayNames.XML_TAG_DATA, XML_TAG_DATA),
    new AttributesDescriptor(DisplayNames.XML_ATTRIBUTE_NAME, XML_ATTRIBUTE_NAME),
    new AttributesDescriptor(DisplayNames.XML_ATTRIBUTE_VALUE, XML_ATTRIBUTE_VALUE),
    new AttributesDescriptor(DisplayNames.XML_COMMENT, XML_COMMENT),
    new AttributesDescriptor(DisplayNames.SCALATEST_KEYWORD, SCALATEST_KEYWORD),

    new AttributesDescriptor(DisplayNames.SCALA_DIRECTIVE_PREFIX, SCALA_DIRECTIVE_PREFIX),
    new AttributesDescriptor(DisplayNames.SCALA_DIRECTIVE_COMMAND, SCALA_DIRECTIVE_COMMAND),
    new AttributesDescriptor(DisplayNames.SCALA_DIRECTIVE_KEY, SCALA_DIRECTIVE_KEY),
    new AttributesDescriptor(DisplayNames.SCALA_DIRECTIVE_VALUE, SCALA_DIRECTIVE_VALUE)
  )

  //noinspection TypeAnnotation
  private[highlighter] object DisplayNames {
    val LINE_COMMENT = ScalaOptionsBundle.message("options.scala.attribute.descriptor.line.comment")
    val BLOCK_COMMENT = ScalaOptionsBundle.message("options.scala.attribute.descriptor.block.comment")
    val KEYWORD = ScalaOptionsBundle.message("options.scala.attribute.descriptor.keyword")
    val NUMBER = ScalaOptionsBundle.message("options.scala.attribute.descriptor.number")
    val STRING = ScalaOptionsBundle.message("options.scala.attribute.descriptor.string")
    val VALID_STRING_ESCAPE = ScalaOptionsBundle.message("options.scala.attribute.descriptor.valid.escape.in.string")
    val INVALID_STRING_ESCAPE = ScalaOptionsBundle.message("options.scala.attribute.descriptor.invalid.escape.in.string")
    val BRACKETS = ScalaOptionsBundle.message("options.scala.attribute.descriptor.brackets")
    val BRACES = ScalaOptionsBundle.message("options.scala.attribute.descriptor.braces")
    val COLON = ScalaOptionsBundle.message("options.scala.attribute.descriptor.colon")
    val PARENTHESES = ScalaOptionsBundle.message("options.scala.attribute.descriptor.parentheses")
    val ASSIGN = ScalaOptionsBundle.message("options.scala.attribute.descriptor.assign")
    val ARROW = ScalaOptionsBundle.message("options.scala.attribute.descriptor.arrow")
    val SEMICOLON = ScalaOptionsBundle.message("options.scala.attribute.descriptor.semicolon")
    val DOT = ScalaOptionsBundle.message("options.scala.attribute.descriptor.dot")
    val COMMA = ScalaOptionsBundle.message("options.scala.attribute.descriptor.comma")
    val INTERPOLATED_STRING_INJECTION = ScalaOptionsBundle.message("options.scala.attribute.descriptor.interpolated.string.injection")
    val MUTABLE_COLLECTION = ScalaOptionsBundle.message("options.scala.attribute.descriptor.mutable.collection")
    val IMMUTABLE_COLLECTION = ScalaOptionsBundle.message("options.scala.attribute.descriptor.immutable.collection")
    val JAVA_COLLECTION = ScalaOptionsBundle.message("options.scala.attribute.descriptor.standard.java.collection")
    val TYPEPARAM = ScalaOptionsBundle.message("options.scala.attribute.descriptor.type.parameter")
    val PREDEF = ScalaOptionsBundle.message("options.scala.attribute.descriptor.predefined.types")
    val OBJECT = ScalaOptionsBundle.message("options.scala.attribute.descriptor.object")
    val CLASS = ScalaOptionsBundle.message("options.scala.attribute.descriptor.class")
    val ABSTRACT_CLASS = ScalaOptionsBundle.message("options.scala.attribute.descriptor.abstract.class")
    val TRAIT = ScalaOptionsBundle.message("options.scala.attribute.descriptor.trait")
    val ENUM = ScalaOptionsBundle.message("options.scala.attribute.descriptor.enum")
    val ENUM_SINGLETON_CASE = ScalaOptionsBundle.message("options.scala.attribute.descriptor.enum.case.singleton")
    val ENUM_CLASS_CASE = ScalaOptionsBundle.message("options.scala.attribute.descriptor.enum.case.class")
    val BAD_CHARACTER = ScalaOptionsBundle.message("options.scala.attribute.descriptor.bad.character")
    val DOC_COMMENT = ScalaOptionsBundle.message("options.scala.attribute.descriptor.scaladoc.comment")
    val SCALA_DOC_TAG = ScalaOptionsBundle.message("options.scala.attribute.descriptor.scaladoc.comment.tag")
    val SCALA_DOC_HTML_TAG = ScalaOptionsBundle.message("options.scala.attribute.descriptor.scaladoc.html.tag")
    val SCALA_DOC_WIKI_SYNTAX = ScalaOptionsBundle.message("options.scala.attribute.descriptor.scaladoc.wiki.syntax.elements")
    val SCALA_DOC_HTML_ESCAPE = ScalaOptionsBundle.message("options.scala.attribute.descriptor.scaladoc.html.escape.sequences")
    val SCALA_DOC_TAG_PARAM_VALUE = ScalaOptionsBundle.message("options.scala.attribute.descriptor.scaladoc.param.value")
    val IMPLICIT_CONVERSIONS = ScalaOptionsBundle.message("options.scala.attribute.descriptor.implicit.conversion")
    val LOCAL_VALUES = ScalaOptionsBundle.message("options.scala.attribute.descriptor.local.value")
    val LOCAL_VARIABLES = ScalaOptionsBundle.message("options.scala.attribute.descriptor.local.variable")
    val LOCAL_LAZY = ScalaOptionsBundle.message("options.scala.attribute.descriptor.local.lazy.val.var")
    val VALUES = ScalaOptionsBundle.message("options.scala.attribute.descriptor.template.val")
    val VARIABLES = ScalaOptionsBundle.message("options.scala.attribute.descriptor.template.var")
    val LAZY = ScalaOptionsBundle.message("options.scala.attribute.descriptor.template.lazy.val.var")
    val PARAMETER = ScalaOptionsBundle.message("options.scala.attribute.descriptor.parameter")
    //TODO: rename ANONIMOUS -> ANONYMOUS
    val PARAMETER_OF_ANONIMOUS_FUNCTION = ScalaOptionsBundle.message("options.scala.attribute.descriptor.parameter.of.anonimous.function")
    val METHOD_CALL = ScalaOptionsBundle.message("options.scala.attribute.descriptor.class.method.call")
    val OBJECT_METHOD_CALL = ScalaOptionsBundle.message("options.scala.attribute.descriptor.object.method.call")
    val LOCAL_METHOD_CALL = ScalaOptionsBundle.message("options.scala.attribute.descriptor.local.method.call")
    val METHOD_DECLARATION = ScalaOptionsBundle.message("options.scala.attribute.descriptor.method.declaration")
    val ANNOTATION = ScalaOptionsBundle.message("options.scala.attribute.descriptor.annotation.name")
    val ANNOTATION_ATTRIBUTE = ScalaOptionsBundle.message("options.scala.attribute.descriptor.annotation.attribute.name")
    val PATTERN = ScalaOptionsBundle.message("options.scala.attribute.descriptor.pattern.value")
    val GENERATOR = ScalaOptionsBundle.message("options.scala.attribute.descriptor.for.statement.value")
    val TYPE_ALIAS = ScalaOptionsBundle.message("options.scala.attribute.descriptor.type.alias")
    val XML_TAG = ScalaOptionsBundle.message("options.scala.attribute.descriptor.scala.xml.tag")
    val XML_TAG_NAME = ScalaOptionsBundle.message("options.scala.attribute.descriptor.scala.xml.tag.name")
    val XML_TAG_DATA = ScalaOptionsBundle.message("options.scala.attribute.descriptor.scala.xml.tag.data")
    val XML_ATTRIBUTE_NAME = ScalaOptionsBundle.message("options.scala.attribute.descriptor.scala.xml.attribute.name")
    val XML_ATTRIBUTE_VALUE = ScalaOptionsBundle.message("options.scala.attribute.descriptor.scala.xml.attribute.value")
    val XML_COMMENT = ScalaOptionsBundle.message("options.scala.attribute.descriptor.scala.xml.comment")
    val SCALATEST_KEYWORD = ScalaOptionsBundle.message("options.scala.attribute.descriptor.scalatest.keyword")

    val SCALA_DIRECTIVE_PREFIX = ScalaOptionsBundle.message("options.scala.attribute.descriptor.scala.directive.prefix")
    val SCALA_DIRECTIVE_COMMAND = ScalaOptionsBundle.message("options.scala.attribute.descriptor.scala.directive.command")
    val SCALA_DIRECTIVE_KEY = ScalaOptionsBundle.message("options.scala.attribute.descriptor.scala.directive.key")
    val SCALA_DIRECTIVE_VALUE = ScalaOptionsBundle.message("options.scala.attribute.descriptor.scala.directive.value")
  }
}
class ScalaColorsAndFontsPage extends RainbowColorSettingsPage {
  override def getDisplayName: String = ScalaBundle.message("options.scala.display.name")

  override def getLanguage: ScalaLanguage = ScalaLanguage.INSTANCE

  override def getIcon: Icon = Icons.SCALA_SMALL_LOGO

  override def getAttributeDescriptors: Array[AttributesDescriptor] = ScalaColorsAndFontsPage.ourDescriptors

  override def getColorDescriptors: Array[ColorDescriptor] = new Array[ColorDescriptor](0)

  override def getHighlighter: SyntaxHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(ScalaLanguage.INSTANCE, null, null)

  override def isRainbowType(key: TextAttributesKey): Boolean =
    key match {
      case `LOCAL_VALUES` |
           `LOCAL_VARIABLES` |
           `PARAMETER` |
           PARAMETER_OF_ANONIMOUS_FUNCTION |
           `PATTERN` |
           `SCALA_DOC_TAG_PARAM_VALUE` =>
        true
      case _ => false
    }

  private val scalaDirectiveDemoText: String = {
    val pt = "ScalaDirectivePrefix"
    val ct = "ScalaDirectiveCommand"
    val kt = "ScalaDirectiveKey"
    val vt = "ScalaDirectiveValue"

    s"<$pt>//></$pt> <$ct>using</$ct> <$kt>dep</$kt> <$vt>org.jetbrains::annotation:4.2.42</$vt>"
  }

  override def getDemoText: String =
    s"""$scalaDirectiveDemoText
       |
       |<keyword>import</keyword> scala<dot>.</dot>collection<dot>.</dot>mutable<dot>.</dot>_
       |<keyword>import</keyword> java<dot>.</dot>util<dot>.</dot>TreeMap
       |
       |<scaladoc>/**
       | * ScalaDoc comment: <scaladocHtml><code></scaladocHtml>Some code<scaladocHtml></code></scaladocHtml>
       | * Html escape sequence <htmlDocEscape>&#94;</htmlDocEscape>
       | * <wikiElement>''</wikiElement>Text<wikiElement>''</wikiElement>
       | * <tag>@param</tag> <paramtagval>x</paramtagval> Int param
       | * <tag>@author</tag> IntelliJ
       | */</scaladoc>
       |<keyword>class</keyword> <class>ScalaClass</class><par>(</par><param>x</param><colon>:</colon> <predef>Int</predef><par>)</par> <keyword>extends</keyword> <class>ScalaObject</class> <brace>{</brace>
       |  <number>1</number> <implicit>to</implicit> <number>5</number>
       |  <par>(</par><anon_param>x</anon_param><colon>:</colon> <predef>Int</predef><par>)</par> <arrow>=></arrow> <anon_param>x</anon_param>
       |  <keyword>val</keyword> <val>field</val> <assign>=</assign> <string>"Some<validescape>\\n</validescape>Strin<invalidescape>\\g</invalidescape>"</string>
       |  <keyword>def</keyword> <methoddecl>foo</methoddecl><par>(</par><param>x</param><colon>:</colon> <predef>Float</predef><comma>,</comma> <param>y</param><colon>:</colon> <predef>Float</predef><par>)</par> <assign>=</assign> <brace>{</brace>
       |    <keyword>def</keyword> <methoddecl>empty</methoddecl> <assign>=</assign> <number>2</number>
       |    <keyword>val</keyword> <local>local</local> <assign>=</assign> <number>1000</number> - <localmethod>empty</localmethod>
       |    <object>Math</object><dot>.</dot><objectmethod>sqrt</objectmethod><par>(</par><param>x</param> + <param>y</param> + <local>local</local><par>)</par><semicolon>;</semicolon> <linecomment>//this can crash</linecomment>
       |  <brace>}</brace>
       |  <keyword>def</keyword> <methoddecl>t</methoddecl><bracket>[</bracket><typeparam>T</typeparam><bracket>]</bracket><colon>:</colon> <typeparam>T</typeparam> <assign>=</assign> <keyword>null</keyword>
       |  <method>foo</method><par>(</par><number>0</number><comma>,</comma> <number>-1</number><par>)</par> <keyword>match</keyword> <brace>{</brace>
       |    <keyword>case</keyword> <pattern>x</pattern> <arrow>=></arrow> <pattern>x</pattern>
       |  <brace>}<brace>
       |  <keyword>type</keyword> <typeAlias>G</typeAlias> <assign>=</assign> <predef>Int</predef>
       |  val xml = <xmltag><<xmltagname>element</xmltagname> <xmlattributename>attibute=</xmlattributename><xmlattributevalue>"value"</xmlattributevalue>></xmltag><xmltagdata>data</xmltagdata><xmltag></element></xmltag>
       |<brace>}</brace>
       |
       |<keyword>object</keyword> <object>Object</object> <brace>{</brace>
       |  <keyword>val</keyword> <val>layer</val> <assign>=</assign> <number>-5.0</number>
       |  <keyword>val</keyword> <val>mutableCollection</val> <assign>=</assign> <mutablec>HashMap</mutablec>[<predef>Int</predef>,  <predef>Int</predef>]()
       |  <keyword>val</keyword> <val>immutableCollection</val> <assign>=</assign> <immutablec>List</immutablec>(<number>1</number><comma>,</comma> <number>2</number>)
       |  <keyword>val</keyword> <val>javaCollection</val> <assign>=</assign> <keyword>new</keyword> <javac>TreeMap</javac>[<predef>Int</predef>,  <predef>Int</predef>]()
       |
       |  <keyword>def</keyword> <methoddecl>foo</methoddecl><colon>:</colon> <class>ScalaClass</class> <assign>=</assign> <keyword>new</keyword> <class>ScalaClass</class><par>(</par><number>23</number>, <number>9</number><par>)</par>
       |<brace>}</brace>
       |
       |<annotation>@Annotation</annotation><par>(</par><number>2</number><par>)</par>
       |<keyword>trait</keyword> <trait>Trait</trait> <brace>{</brace>
       |<brace>}</brace>
       |
       |<keyword>abstract</keyword> <keyword>class</keyword> <abstract>SomeAbstract</abstract> <brace>{</brace>
       |  <keyword>for</keyword> <par>(</par><generator>x</generator> <- list<par>)</par> <brace>{</brace><generator>x</generator><brace>}</brace>
       |<brace>}</brace>
       |
       |<keyword>enum</keyword> <enum>MyEnum</enum>:
       |  <keyword>case</keyword> <enum_singleton_case>MySingletonCase</enum_singleton_case>
       |  <keyword>case</keyword> <enum_class_case>MyClassCase</enum_class_case>(<param>x</param>: <predef>Int</predef>)
       |""".stripMargin.replace("\r", "")

  override def getAdditionalHighlightingTagToDescriptorMap: util.Map[String, TextAttributesKey] = {
    val map = RainbowHighlighter.createRainbowHLM
    map.put("keyword", KEYWORD)
    map.put("par", PARENTHESES)
    map.put("brace", BRACES)
    map.put("colon", COLON)
    map.put("string", STRING)
    map.put("validescape", VALID_STRING_ESCAPE)
    map.put("invalidescape", INVALID_STRING_ESCAPE)
    map.put("typeparam", TYPEPARAM)
    map.put("assign", ASSIGN)
    map.put("arrow", ARROW)
    map.put("bracket", BRACKETS)
    map.put("dot", DOT)
    map.put("semicolon", SEMICOLON)
    map.put("comma", COMMA)
    map.put("mutablec", MUTABLE_COLLECTION)
    map.put("immutablec", IMMUTABLE_COLLECTION)
    map.put("javac", JAVA_COLLECTION)
    map.put("number", NUMBER)
    map.put("linecomment", LINE_COMMENT)
    map.put("blockcomment", BLOCK_COMMENT)
    map.put("predef", PREDEF)
    map.put("class", CLASS)
    map.put("object", OBJECT)
    map.put("trait", TRAIT)
    map.put("enum", ENUM)
    map.put("enum_singleton_case", ENUM_SINGLETON_CASE)
    map.put("enum_class_case", ENUM_CLASS_CASE)
    map.put("annotation", ANNOTATION)
    map.put("attribute", ANNOTATION_ATTRIBUTE)
    map.put("abstract", ABSTRACT_CLASS)
    map.put("local", LOCAL_VALUES)
    map.put("val", VALUES)
    map.put("param", PARAMETER)
    map.put("anon_param", PARAMETER_OF_ANONIMOUS_FUNCTION)
    map.put("method", METHOD_CALL)
    map.put("objectmethod", OBJECT_METHOD_CALL)
    map.put("localmethod", LOCAL_METHOD_CALL)
    map.put("methoddecl", METHOD_DECLARATION)
    map.put("pattern", PATTERN)
    map.put("generator", GENERATOR)
    map.put("typeAlias", TYPE_ALIAS)
    map.put("xmltag", XML_TAG)
    map.put("xmltagname", XML_TAG_NAME)
    map.put("xmltagdata", XML_TAG_DATA)
    map.put("xmlattributename", XML_ATTRIBUTE_NAME)
    map.put("xmlattributevalue", XML_ATTRIBUTE_VALUE)
    map.put("xmlcomment", XML_COMMENT)
    map.put("implicit", IMPLICIT_CONVERSIONS)
    map.put("scaladoc", DOC_COMMENT)
    map.put("markup", SCALA_DOC_MARKUP)
    map.put("tag", SCALA_DOC_TAG)
    map.put("wikiElement", SCALA_DOC_WIKI_SYNTAX)
    map.put("scaladocHtml", SCALA_DOC_HTML_TAG)
    map.put("htmlDocEscape", SCALA_DOC_HTML_ESCAPE)
    map.put("paramtagval", SCALA_DOC_TAG_PARAM_VALUE)

    map.put("ScalaDirectivePrefix", SCALA_DIRECTIVE_PREFIX)
    map.put("ScalaDirectiveCommand", SCALA_DIRECTIVE_COMMAND)
    map.put("ScalaDirectiveKey", SCALA_DIRECTIVE_KEY)
    map.put("ScalaDirectiveValue", SCALA_DIRECTIVE_VALUE)
    map
  }
}