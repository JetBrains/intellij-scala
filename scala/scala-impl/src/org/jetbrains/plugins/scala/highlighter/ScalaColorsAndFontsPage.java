package org.jetbrains.plugins.scala.highlighter;

import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.RainbowColorSettingsPage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaLanguage;
import org.jetbrains.plugins.scala.icons.Icons;

import javax.swing.*;
import java.util.Map;

import static org.jetbrains.plugins.scala.highlighter.DefaultHighlighter.*;

/**
 * User: Alexander Podkhalyuzin
 * Date: 15.07.2008
 */
public class ScalaColorsAndFontsPage implements RainbowColorSettingsPage {
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
            new AttributesDescriptor(KEYWORD_NAME, KEYWORD),
            new AttributesDescriptor(NUMBER_NAME, NUMBER),
            new AttributesDescriptor(STRING_NAME, STRING),
            new AttributesDescriptor(VALID_STRING_ESCAPE_NAME, VALID_STRING_ESCAPE),
            new AttributesDescriptor(INVALID_STRING_ESCAPE_NAME, INVALID_STRING_ESCAPE),
            new AttributesDescriptor(ASSIGN_NAME, ASSIGN),
            new AttributesDescriptor(PARENTHESES_NAME, PARENTHESES),
            new AttributesDescriptor(BRACES_NAME, BRACES),
            new AttributesDescriptor(BRACKETS_NAME, BRACKETS),
            new AttributesDescriptor(COLON_NAME, COLON),
            new AttributesDescriptor(SEMICOLON_NAME, SEMICOLON),
            new AttributesDescriptor(DOT_NAME, DOT),
            new AttributesDescriptor(COMMA_NAME, COMMA),
            new AttributesDescriptor(MUTABLE_COLLECTION_NAME, MUTABLE_COLLECTION),
            new AttributesDescriptor(IMMUTABLE_COLLECTION_NAME, IMMUTABLE_COLLECTION),
            new AttributesDescriptor(JAVA_COLLECTION_NAME, JAVA_COLLECTION),
            new AttributesDescriptor(INTERPOLATED_STRING_INJECTION_NAME, INTERPOLATED_STRING_INJECTION),
            new AttributesDescriptor(LINE_COMMENT_NAME, LINE_COMMENT),
            new AttributesDescriptor(BLOCK_COMMENT_NAME, BLOCK_COMMENT),
            new AttributesDescriptor(DOC_COMMENT_NAME, DOC_COMMENT),
            new AttributesDescriptor(SCALA_DOC_TAG_NAME, SCALA_DOC_TAG),
            new AttributesDescriptor(SCALA_DOC_HTML_TAG_NAME, SCALA_DOC_HTML_TAG),
            new AttributesDescriptor(SCALA_DOC_WIKI_SYNTAX_NAME, SCALA_DOC_WIKI_SYNTAX),
            new AttributesDescriptor(SCALA_DOC_HTML_ESCAPE_NAME, SCALA_DOC_HTML_ESCAPE),
            new AttributesDescriptor(SCALA_DOC_TAG_PARAM_VALUE_NAME, SCALA_DOC_TAG_PARAM_VALUE),
            new AttributesDescriptor(IMPLICIT_CONVERSIONS_NAME, IMPLICIT_CONVERSIONS),
            new AttributesDescriptor(CLASS_NAME, CLASS),
            new AttributesDescriptor(ABSTRACT_CLASS_NAME, ABSTRACT_CLASS),
            new AttributesDescriptor(OBJECT_NAME, OBJECT),
            new AttributesDescriptor(TYPEPARAM_NAME, TYPEPARAM),
            new AttributesDescriptor(TYPE_ALIAS_NAME, TYPE_ALIAS),
            new AttributesDescriptor(PREDEF_NAME, PREDEF),
            new AttributesDescriptor(TRAIT_NAME, TRAIT),
            new AttributesDescriptor(LOCAL_VALUES_NAME, LOCAL_VALUES),
            new AttributesDescriptor(LOCAL_VARIABLES_NAME, LOCAL_VARIABLES),
            new AttributesDescriptor(LOCAL_LAZY_NAME, LOCAL_LAZY),
            new AttributesDescriptor(VALUES_NAME, VALUES),
            new AttributesDescriptor(VARIABLES_NAME, VARIABLES),
            new AttributesDescriptor(LAZY_NAME, LAZY),
            new AttributesDescriptor(PARAMETER_NAME, PARAMETER),
            new AttributesDescriptor(ANONYMOUS_PARAMETER_NAME, ANONYMOUS_PARAMETER),
            new AttributesDescriptor(PATTERN_NAME, PATTERN),
            new AttributesDescriptor(GENERATOR_NAME, GENERATOR),
            new AttributesDescriptor(METHOD_CALL_NAME, METHOD_CALL),
            new AttributesDescriptor(OBJECT_METHOD_CALL_NAME, OBJECT_METHOD_CALL),
            new AttributesDescriptor(LOCAL_METHOD_CALL_NAME, LOCAL_METHOD_CALL),
            new AttributesDescriptor(METHOD_DECLARATION_NAME, METHOD_DECLARATION),
            new AttributesDescriptor(BAD_CHARACTER_NAME, BAD_CHARACTER),
            new AttributesDescriptor(ANNOTATION_NAME, ANNOTATION),
            new AttributesDescriptor(ANNOTATION_ATTRIBUTE_NAME, ANNOTATION_ATTRIBUTE),
            new AttributesDescriptor(XML_TAG_ID, XML_TAG),
            new AttributesDescriptor(XML_TAG_NAME_ID, XML_TAG_NAME),
            new AttributesDescriptor(XML_TAG_DATA_ID, XML_TAG_DATA),
            new AttributesDescriptor(XML_ATTRIBUTE_NAME_ID, XML_ATTRIBUTE_NAME),
            new AttributesDescriptor(XML_ATTRIBUTE_VALUE_ID, XML_ATTRIBUTE_VALUE),
            new AttributesDescriptor(XML_COMMENT_ID, XML_COMMENT),
            new AttributesDescriptor(SCALATEST_KEYWORD_ID, SCALATEST_KEYWORD)
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

  @Override
  public boolean isRainbowType(TextAttributesKey key) {
    return LOCAL_VALUES.equals(key)
            || LOCAL_VARIABLES.equals(key)
            || PARAMETER.equals(key)
            || ANONYMOUS_PARAMETER.equals(key)
            || SCALA_DOC_TAG_PARAM_VALUE.equals(key);
  }

  @NotNull
  @Override
  public Language getLanguage() {
    return ScalaLanguage.INSTANCE;
  }

  @Nullable
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    Map<String, TextAttributesKey> map = RainbowHighlighter.createRainbowHLM();
    map.put("keyword", KEYWORD);
    map.put("par", PARENTHESES);
    map.put("brace", BRACES);
    map.put("colon", COLON);
    map.put("scaladoc", DOC_COMMENT);
    map.put("string", STRING);
    map.put("validescape", VALID_STRING_ESCAPE);
    map.put("invalidescape", INVALID_STRING_ESCAPE);
    map.put("typeparam", TYPEPARAM);
    map.put("assign", ASSIGN);
    map.put("bracket", BRACKETS);
    map.put("dot", DOT);
    map.put("semicolon", SEMICOLON);
    map.put("comma", COMMA);
    map.put("mutablec", MUTABLE_COLLECTION);
    map.put("immutablec", IMMUTABLE_COLLECTION);
    map.put("javac", JAVA_COLLECTION);
    map.put("number", NUMBER);
    map.put("linecomment", LINE_COMMENT);
    map.put("blockcomment", BLOCK_COMMENT);
    map.put("class", CLASS);
    map.put("predef", PREDEF);
    map.put("object", OBJECT);
    map.put("trait", TRAIT);
    map.put("annotation", ANNOTATION);
    map.put("attribute", ANNOTATION_ATTRIBUTE);
    map.put("markup", SCALA_DOC_MARKUP);
    map.put("tag", SCALA_DOC_TAG);
    map.put("abstract", ABSTRACT_CLASS);
    map.put("local", LOCAL_VALUES);
    map.put("val", VALUES);
    map.put("param", PARAMETER);
    map.put("anon_param", ANONYMOUS_PARAMETER);
    map.put("method", METHOD_CALL);
    map.put("objectmethod", OBJECT_METHOD_CALL);
    map.put("localmethod", LOCAL_METHOD_CALL);
    map.put("methoddecl", METHOD_DECLARATION);
    map.put("pattern", PATTERN);
    map.put("generator", GENERATOR);
    map.put("typeAlias", TYPE_ALIAS);
    map.put("wikiElement", SCALA_DOC_WIKI_SYNTAX);
    map.put("scaladocHtml", SCALA_DOC_HTML_TAG);
    map.put("htmlDocEscape", SCALA_DOC_HTML_ESCAPE);
    map.put("paramtagval", SCALA_DOC_TAG_PARAM_VALUE);
    map.put("xmltag", XML_TAG);
    map.put("xmltagname", XML_TAG_NAME);
    map.put("xmltagdata", XML_TAG_DATA);
    map.put("xmlattributename", XML_ATTRIBUTE_NAME);
    map.put("xmlattributevalue", XML_ATTRIBUTE_VALUE);
    map.put("xmlcomment", XML_COMMENT);
    map.put("implicit", IMPLICIT_CONVERSIONS);
    return map;
  }
}
