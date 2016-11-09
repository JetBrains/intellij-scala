package org.jetbrains.plugins.scala.highlighter;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaLanguage;
import org.jetbrains.plugins.scala.icons.Icons;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

/**
 * User: Alexander Podkhalyuzin
 * Date: 15.07.2008
 */
public class ScalaColorsAndFontsPage implements ColorSettingsPage {
  @NotNull
  public String getDisplayName() {
    return "Scala";
  }

  @Nullable
  public Icon getIcon() {
    return Icons.SCALA_SMALL_LOGO;
  }

  @NotNull
  public AttributesDescriptor[] getAttributeDescriptors() {
    return ATTRS;
  }

  private static final AttributesDescriptor[] ATTRS;

  static {
    ATTRS = new AttributesDescriptor[]{
        new AttributesDescriptor(DefaultHighlighter.KEYWORD_NAME, DefaultHighlighter.KEYWORD),
        new AttributesDescriptor(DefaultHighlighter.NUMBER_NAME, DefaultHighlighter.NUMBER),
        new AttributesDescriptor(DefaultHighlighter.STRING_NAME, DefaultHighlighter.STRING),
        new AttributesDescriptor(DefaultHighlighter.VALID_STRING_ESCAPE_NAME, DefaultHighlighter.VALID_STRING_ESCAPE),
        new AttributesDescriptor(DefaultHighlighter.INVALID_STRING_ESCAPE_NAME, DefaultHighlighter.INVALID_STRING_ESCAPE),
        new AttributesDescriptor(DefaultHighlighter.ASSIGN_NAME, DefaultHighlighter.ASSIGN),
        new AttributesDescriptor(DefaultHighlighter.PARENTHESES_NAME, DefaultHighlighter.PARENTHESES),
        new AttributesDescriptor(DefaultHighlighter.BRACES_NAME, DefaultHighlighter.BRACES),
        new AttributesDescriptor(DefaultHighlighter.BRACKETS_NAME, DefaultHighlighter.BRACKETS),
        new AttributesDescriptor(DefaultHighlighter.COLON_NAME, DefaultHighlighter.COLON),
        new AttributesDescriptor(DefaultHighlighter.SEMICOLON_NAME, DefaultHighlighter.SEMICOLON),
        new AttributesDescriptor(DefaultHighlighter.DOT_NAME, DefaultHighlighter.DOT),
        new AttributesDescriptor(DefaultHighlighter.COMMA_NAME, DefaultHighlighter.COMMA),
        new AttributesDescriptor(DefaultHighlighter.MUTABLE_COLLECTION_NAME, DefaultHighlighter.MUTABLE_COLLECTION),
        new AttributesDescriptor(DefaultHighlighter.IMMUTABLE_COLLECTION_NAME, DefaultHighlighter.IMMUTABLE_COLLECTION),
        new AttributesDescriptor(DefaultHighlighter.JAVA_COLLECTION_NAME, DefaultHighlighter.JAVA_COLLECTION),
        new AttributesDescriptor(DefaultHighlighter.INTERPOLATED_STRING_INJECTION_NAME, DefaultHighlighter.INTERPOLATED_STRING_INJECTION),
        new AttributesDescriptor(DefaultHighlighter.LINE_COMMENT_NAME, DefaultHighlighter.LINE_COMMENT),
        new AttributesDescriptor(DefaultHighlighter.BLOCK_COMMENT_NAME, DefaultHighlighter.BLOCK_COMMENT),
        new AttributesDescriptor(DefaultHighlighter.DOC_COMMENT_NAME, DefaultHighlighter.DOC_COMMENT),
        new AttributesDescriptor(DefaultHighlighter.SCALA_DOC_TAG_NAME, DefaultHighlighter.SCALA_DOC_TAG),
        new AttributesDescriptor(DefaultHighlighter.SCALA_DOC_HTML_TAG_NAME, DefaultHighlighter.SCALA_DOC_HTML_TAG),
        new AttributesDescriptor(DefaultHighlighter.SCALA_DOC_WIKI_SYNTAX_NAME, DefaultHighlighter.SCALA_DOC_WIKI_SYNTAX),
        new AttributesDescriptor(DefaultHighlighter.SCALA_DOC_HTML_ESCAPE_NAME, DefaultHighlighter.SCALA_DOC_HTML_ESCAPE),
        new AttributesDescriptor(DefaultHighlighter.SCALA_DOC_TAG_PARAM_VALUE_NAME, DefaultHighlighter.SCALA_DOC_TAG_PARAM_VALUE),
        new AttributesDescriptor(DefaultHighlighter.IMPLICIT_CONVERSIONS_NAME, DefaultHighlighter.IMPLICIT_CONVERSIONS),
        new AttributesDescriptor(DefaultHighlighter.CLASS_NAME, DefaultHighlighter.CLASS),
        new AttributesDescriptor(DefaultHighlighter.ABSTRACT_CLASS_NAME, DefaultHighlighter.ABSTRACT_CLASS),
        new AttributesDescriptor(DefaultHighlighter.OBJECT_NAME, DefaultHighlighter.OBJECT),
        new AttributesDescriptor(DefaultHighlighter.TYPEPARAM_NAME, DefaultHighlighter.TYPEPARAM),
        new AttributesDescriptor(DefaultHighlighter.TYPE_ALIAS_NAME, DefaultHighlighter.TYPE_ALIAS),
        new AttributesDescriptor(DefaultHighlighter.PREDEF_NAME, DefaultHighlighter.PREDEF),
        new AttributesDescriptor(DefaultHighlighter.TRAIT_NAME, DefaultHighlighter.TRAIT),
        new AttributesDescriptor(DefaultHighlighter.LOCAL_VALUES_NAME, DefaultHighlighter.LOCAL_VALUES),
        new AttributesDescriptor(DefaultHighlighter.LOCAL_VARIABLES_NAME, DefaultHighlighter.LOCAL_VARIABLES),
        new AttributesDescriptor(DefaultHighlighter.LOCAL_LAZY_NAME, DefaultHighlighter.LOCAL_LAZY),
        new AttributesDescriptor(DefaultHighlighter.VALUES_NAME, DefaultHighlighter.VALUES),
        new AttributesDescriptor(DefaultHighlighter.VARIABLES_NAME, DefaultHighlighter.VARIABLES),
        new AttributesDescriptor(DefaultHighlighter.LAZY_NAME, DefaultHighlighter.LAZY),
        new AttributesDescriptor(DefaultHighlighter.PARAMETER_NAME, DefaultHighlighter.PARAMETER),
        new AttributesDescriptor(DefaultHighlighter.ANONYMOUS_PARAMETER_NAME, DefaultHighlighter.ANONYMOUS_PARAMETER),
        new AttributesDescriptor(DefaultHighlighter.PATTERN_NAME, DefaultHighlighter.PATTERN),
        new AttributesDescriptor(DefaultHighlighter.GENERATOR_NAME, DefaultHighlighter.GENERATOR),
        new AttributesDescriptor(DefaultHighlighter.METHOD_CALL_NAME, DefaultHighlighter.METHOD_CALL),
        new AttributesDescriptor(DefaultHighlighter.OBJECT_METHOD_CALL_NAME, DefaultHighlighter.OBJECT_METHOD_CALL),
        new AttributesDescriptor(DefaultHighlighter.LOCAL_METHOD_CALL_NAME, DefaultHighlighter.LOCAL_METHOD_CALL),
        new AttributesDescriptor(DefaultHighlighter.METHOD_DECLARATION_NAME, DefaultHighlighter.METHOD_DECLARATION),
        new AttributesDescriptor(DefaultHighlighter.BAD_CHARACTER_NAME, DefaultHighlighter.BAD_CHARACTER),
        new AttributesDescriptor(DefaultHighlighter.ANNOTATION_NAME, DefaultHighlighter.ANNOTATION),
        new AttributesDescriptor(DefaultHighlighter.ANNOTATION_ATTRIBUTE_NAME, DefaultHighlighter.ANNOTATION_ATTRIBUTE),
        new AttributesDescriptor(DefaultHighlighter.XML_TAG_ID, DefaultHighlighter.XML_TAG),
        new AttributesDescriptor(DefaultHighlighter.XML_TAG_NAME_ID, DefaultHighlighter.XML_TAG_NAME),
        new AttributesDescriptor(DefaultHighlighter.XML_TAG_DATA_ID, DefaultHighlighter.XML_TAG_DATA),
        new AttributesDescriptor(DefaultHighlighter.XML_ATTRIBUTE_NAME_ID, DefaultHighlighter.XML_ATTRIBUTE_NAME),
        new AttributesDescriptor(DefaultHighlighter.XML_ATTRIBUTE_VALUE_ID, DefaultHighlighter.XML_ATTRIBUTE_VALUE),
        new AttributesDescriptor(DefaultHighlighter.XML_COMMENT_ID, DefaultHighlighter.XML_COMMENT),
        new AttributesDescriptor(DefaultHighlighter.IMPLICIT_FIRST_PART_NAME, DefaultHighlighter.IMPLICIT_FIRST_PART),
        new AttributesDescriptor(DefaultHighlighter.IMPLICIT_SECOND_PART_NAME, DefaultHighlighter.IMPLICIT_SECOND_PART),
        new AttributesDescriptor(DefaultHighlighter.SCALATEST_KEYWORD_ID, DefaultHighlighter.SCALATEST_KEYWORD)
    };
  }

  @NotNull
  public ColorDescriptor[] getColorDescriptors() {
    return new ColorDescriptor[0];
  }

  @NotNull
  public SyntaxHighlighter getHighlighter() {
      return SyntaxHighlighterFactory.getSyntaxHighlighter(ScalaLanguage.INSTANCE, null, null);
  }

  @NonNls
  @NotNull
  public String getDemoText() {
    return "<keyword>import</keyword> scala<dot>.</dot>collection<dot>.</dot>mutable<dot>.</dot>_\n" +
        "<keyword>import</keyword> java<dot>.</dot>util<dot>.</dot>TreeMap\n\n" +
        "<scaladoc>/**\n" +
        " * ScalaDoc comment: <scaladocHtml><code></scaladocHtml>Some code<scaladocHtml></code></scaladocHtml>\n" +
        " * Html escape sequence <htmlDocEscape>&#94;</htmlDocEscape>\n" +
        " * <wikiElement>''</wikiElement>Text<wikiElement>''</wikiElement>  \n" +
        " * <tag>@param</tag> <paramtagval>x</paramtagval> Int param \n" +
        " * <tag>@author</tag> IntelliJ\n" +
        " */</scaladoc>\n" +
        "<keyword>class</keyword> <class>ScalaClass</class><par>(</par><param>x</param><colon>:</colon> <predef>Int</predef><par>)</par>" +
        " <keyword>extends</keyword>" +
        " <class>ScalaObject</class> <brace>{</brace>\n" +
        "  <number>1</number> <implicit>to</implicit> <number>5</number>\n" +
        "  <par>(</par><anon_param>x</anon_param><colon>:</colon> <predef>Int</predef><par>)</par> => <anon_param>x</anon_param>\n" +
        "  <keyword>val</keyword> <val>field</val> <assign>=</assign> <string>\"Some<validescape>\\n</validescape>Strin<invalidescape>\\g</invalidescape>\"</string>\n" +
        "  <keyword>def</keyword> <methoddecl>foo</methoddecl><par>(</par><param>x</param><colon>:</colon> <predef>Float</predef><comma>," +
        "</comma> <param>y</param><colon>:</colon> <predef>Float</predef><par>)</par> <assign>=</assign> <brace>{</brace>\n" +
        "    <keyword>def</keyword> <methoddecl>empty</methoddecl> <assign>=</assign> <number>2</number>\n" +
        "    <keyword>val</keyword> <local>local</local> <assign>=</assign> <number>1000</number> - <localmethod>empty</localmethod>\n" +
        "    <object>Math</object><dot>.</dot><objectmethod>sqrt</objectmethod><par>(" +
        "</par><param>x</param> + <param>y</param> + <local>local</local><par>)</par><semicolon>;</semicolon> <linecomment>//this can crash</linecomment>\n" +
        "  <brace>}</brace>\n" +
        "  <keyword>def</keyword> <methoddecl>t</methoddecl><bracket>[</bracket><typeparam>T</typeparam><bracket>]</bracket><colon>:</colon> " +
        "<typeparam>T</typeparam> <assign>=</assign> <keyword>null</keyword>\n" +
        "  <method>foo</method><par>(</par><number>0</number><comma>,</comma> <number>-1</number><par>)</par> " +
        "<keyword>match</keyword> <brace>{</brace>\n" +
        "    <keyword>case</keyword> <pattern>x</pattern> => <pattern>x</pattern>\n" +
        "  <brace>}<brace>\n" +
        "  <keyword>type</keyword> <typeAlias>G</typeAlias> <assign>=</assign> <predef>Int</predef>\n" +
        "  val xml = <xmltag><<xmltagname>element</xmltagname> <xmlattributename>attibute=</xmlattributename><xmlattributevalue>\"value\"</xmlattributevalue>></xmltag><xmltagdata>data</xmltagdata><xmltag></element></xmltag>\n" +
        "<brace>}</brace>\n" +
        "\n" +
        "<blockcomment>/*\n" +
        "  And now ScalaObject\n" +
        " */</blockcomment>\n" +
        "<keyword>object</keyword> <object>Object</object> <brace>{</brace>\n" +
        "  <keyword>val</keyword> <val>layer</val> <assign>=</assign> <number>-5.0</number>\n" +
        "  <keyword>val</keyword> <val>mutableCollection</val> <assign>=</assign> <mutablec>HashMap</mutablec>[<predef>Int</predef>,  <predef>Int</predef>]()\n" +
        "  <keyword>val</keyword> <val>immutableCollection</val> <assign>=</assign> <immutablec>List</immutablec>(<number>1</number><comma>,</comma> <number>2</number>)\n" +
        "  <keyword>val</keyword> <val>javaCollection</val> <assign>=</assign> <keyword>new</keyword> <javac>TreeMap</javac>[<predef>Int</predef>,  <predef>Int</predef>]()\n\n" +
        "  <keyword>def</keyword> <methoddecl>foo</methoddecl><colon>:</colon> <class>ScalaClass</class> <assign>=</assign> " +
        "<keyword>new</keyword> <class>ScalaClass</class><par>(</par><number>23</number>, " +
        "<number>9</number><par>)</par>\n" +
        "<brace>}</brace>\n\n" +
        "<annotation>@Annotation</annotation><par>(</par><number>2</number><par>)</par> " +
        "<brace>{</brace><keyword>val</keyword> <attribute>name</attribute> <assign>=</assign> value<brace>}</brace>\n" +
        "<keyword>trait</keyword> <trait>Trait</trait> <brace>{</brace>\n" +
        "<brace>}</brace>\n\n" +
        "<keyword>abstract</keyword> <keyword>class</keyword> <abstract>SomeAbstract</abstract> <brace>{</brace>\n" +
        "  <keyword>for</keyword> <par>(</par><generator>x</generator> <- list<par>)</par> <brace>{</brace><generator>x</generator><brace>}</brace>\n" +
        "<brace>}</brace>\n\n";
  }

  @Nullable
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    Map<String, TextAttributesKey> map = new HashMap<String, TextAttributesKey>();
    map.put("keyword", DefaultHighlighter.KEYWORD);
    map.put("par", DefaultHighlighter.PARENTHESES);
    map.put("brace", DefaultHighlighter.BRACES);
    map.put("colon", DefaultHighlighter.COLON);
    map.put("scaladoc", DefaultHighlighter.DOC_COMMENT);
    map.put("string", DefaultHighlighter.STRING);
    map.put("validescape", DefaultHighlighter.VALID_STRING_ESCAPE);
    map.put("invalidescape", DefaultHighlighter.INVALID_STRING_ESCAPE);
    map.put("typeparam", DefaultHighlighter.TYPEPARAM);
    map.put("assign", DefaultHighlighter.ASSIGN);
    map.put("bracket", DefaultHighlighter.BRACKETS);
    map.put("dot", DefaultHighlighter.DOT);
    map.put("semicolon", DefaultHighlighter.SEMICOLON);
    map.put("comma", DefaultHighlighter.COMMA);
    map.put("mutablec", DefaultHighlighter.MUTABLE_COLLECTION);
    map.put("immutablec", DefaultHighlighter.IMMUTABLE_COLLECTION);
    map.put("javac", DefaultHighlighter.JAVA_COLLECTION);
    map.put("number", DefaultHighlighter.NUMBER);
    map.put("linecomment", DefaultHighlighter.LINE_COMMENT);
    map.put("blockcomment", DefaultHighlighter.BLOCK_COMMENT);
    map.put("class", DefaultHighlighter.CLASS);
    map.put("predef", DefaultHighlighter.PREDEF);
    map.put("object", DefaultHighlighter.OBJECT);
    map.put("trait", DefaultHighlighter.TRAIT);
    map.put("annotation", DefaultHighlighter.ANNOTATION);
    map.put("attribute", DefaultHighlighter.ANNOTATION_ATTRIBUTE);
    map.put("markup", DefaultHighlighter.SCALA_DOC_MARKUP);
    map.put("tag", DefaultHighlighter.SCALA_DOC_TAG);
    map.put("abstract", DefaultHighlighter.ABSTRACT_CLASS);
    map.put("local", DefaultHighlighter.LOCAL_VALUES);
    map.put("val", DefaultHighlighter.VALUES);
    map.put("param", DefaultHighlighter.PARAMETER);
    map.put("anon_param", DefaultHighlighter.ANONYMOUS_PARAMETER);
    map.put("method", DefaultHighlighter.METHOD_CALL);
    map.put("objectmethod", DefaultHighlighter.OBJECT_METHOD_CALL);
    map.put("localmethod", DefaultHighlighter.LOCAL_METHOD_CALL);
    map.put("methoddecl", DefaultHighlighter.METHOD_DECLARATION);
    map.put("pattern", DefaultHighlighter.PATTERN);
    map.put("generator", DefaultHighlighter.GENERATOR);
    map.put("typeAlias", DefaultHighlighter.TYPE_ALIAS);
    map.put("wikiElement", DefaultHighlighter.SCALA_DOC_WIKI_SYNTAX);
    map.put("scaladocHtml", DefaultHighlighter.SCALA_DOC_HTML_TAG);
    map.put("htmlDocEscape", DefaultHighlighter.SCALA_DOC_HTML_ESCAPE);
    map.put("paramtagval", DefaultHighlighter.SCALA_DOC_TAG_PARAM_VALUE);
    map.put("xmltag", DefaultHighlighter.XML_TAG);
    map.put("xmltagname", DefaultHighlighter.XML_TAG_NAME);
    map.put("xmltagdata", DefaultHighlighter.XML_TAG_DATA);
    map.put("xmlattributename", DefaultHighlighter.XML_ATTRIBUTE_NAME);
    map.put("xmlattributevalue", DefaultHighlighter.XML_ATTRIBUTE_VALUE);
    map.put("xmlcomment", DefaultHighlighter.XML_COMMENT);
    map.put("implicit", DefaultHighlighter.IMPLICIT_CONVERSIONS);
    map.put("implicitFirstPart", DefaultHighlighter.IMPLICIT_FIRST_PART);
    map.put("implicitSecondPart", DefaultHighlighter.IMPLICIT_SECOND_PART);
    return map;
  }
}
