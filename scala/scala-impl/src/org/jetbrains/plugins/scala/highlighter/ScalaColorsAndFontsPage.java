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
import org.jetbrains.plugins.scala.ScalaBundle;
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
  @Override
  public String getDisplayName() {
    return ScalaBundle.message("options.scala.display.name");
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return Icons.SCALA_SMALL_LOGO;
  }

  @NotNull
  @Override
  public AttributesDescriptor[] getAttributeDescriptors() {
    return ourDescriptors;
  }

  private static final AttributesDescriptor[] ourDescriptors = {
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
          new AttributesDescriptor(DisplayNames.TYPEPARAM, TYPEPARAM),
          new AttributesDescriptor(DisplayNames.TYPE_ALIAS, TYPE_ALIAS),
          new AttributesDescriptor(DisplayNames.PREDEF, PREDEF),
          new AttributesDescriptor(DisplayNames.TRAIT, TRAIT),
          new AttributesDescriptor(DisplayNames.LOCAL_VALUES, LOCAL_VALUES),
          new AttributesDescriptor(DisplayNames.LOCAL_VARIABLES, LOCAL_VARIABLES),
          new AttributesDescriptor(DisplayNames.LOCAL_LAZY, LOCAL_LAZY),
          new AttributesDescriptor(DisplayNames.VALUES, VALUES),
          new AttributesDescriptor(DisplayNames.VARIABLES, VARIABLES),
          new AttributesDescriptor(DisplayNames.LAZY, LAZY),
          new AttributesDescriptor(DisplayNames.PARAMETER, PARAMETER),
          new AttributesDescriptor(DisplayNames.ANONYMOUS_PARAMETER, ANONYMOUS_PARAMETER),
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
          new AttributesDescriptor(DisplayNames.SCALATEST_KEYWORD, SCALATEST_KEYWORD)
  };

  @NotNull
  @Override
  public ColorDescriptor[] getColorDescriptors() {
    return new ColorDescriptor[0];
  }

  @NotNull
  @Override
  public SyntaxHighlighter getHighlighter() {
    return SyntaxHighlighterFactory.getSyntaxHighlighter(ScalaLanguage.INSTANCE, null, null);
  }

  @NonNls
  @NotNull
  @Override
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
            "  <par>(</par><anon_param>x</anon_param><colon>:</colon> <predef>Int</predef><par>)</par> <arrow>=></arrow> <anon_param>x</anon_param>\n" +
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
            "    <keyword>case</keyword> <pattern>x</pattern> <arrow>=></arrow> <pattern>x</pattern>\n" +
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
            || PATTERN.equals(key)
            || SCALA_DOC_TAG_PARAM_VALUE.equals(key);
  }

  @NotNull
  @Override
  public Language getLanguage() {
    return ScalaLanguage.INSTANCE;
  }

  @Nullable
  @Override
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    Map<String, TextAttributesKey> map = RainbowHighlighter.createRainbowHLM();
    map.put("keyword", KEYWORD);
    map.put("par", PARENTHESES);
    map.put("brace", BRACES);
    map.put("colon", COLON);
    map.put("string", STRING);
    map.put("validescape", VALID_STRING_ESCAPE);
    map.put("invalidescape", INVALID_STRING_ESCAPE);
    map.put("typeparam", TYPEPARAM);
    map.put("assign", ASSIGN);
    map.put("arrow", ARROW);
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
    map.put("xmltag", XML_TAG);
    map.put("xmltagname", XML_TAG_NAME);
    map.put("xmltagdata", XML_TAG_DATA);
    map.put("xmlattributename", XML_ATTRIBUTE_NAME);
    map.put("xmlattributevalue", XML_ATTRIBUTE_VALUE);
    map.put("xmlcomment", XML_COMMENT);
    map.put("implicit", IMPLICIT_CONVERSIONS);

    map.put("scaladoc", DOC_COMMENT);
    map.put("markup", SCALA_DOC_MARKUP);
    map.put("tag", SCALA_DOC_TAG);
    map.put("wikiElement", SCALA_DOC_WIKI_SYNTAX);
    map.put("scaladocHtml", SCALA_DOC_HTML_TAG);
    map.put("htmlDocEscape", SCALA_DOC_HTML_ESCAPE);
    map.put("paramtagval", SCALA_DOC_TAG_PARAM_VALUE);

    return map;
  }

  public static final class DisplayNames {
    private DisplayNames() {
    }

    static final String LINE_COMMENT = ScalaOptionsBundle.message("options.scala.attribute.descriptor.line.comment");
    static final String BLOCK_COMMENT = ScalaOptionsBundle.message("options.scala.attribute.descriptor.block.comment");
    static final String KEYWORD = ScalaOptionsBundle.message("options.scala.attribute.descriptor.keyword");
    static final String NUMBER = ScalaOptionsBundle.message("options.scala.attribute.descriptor.number");
    static final String STRING = ScalaOptionsBundle.message("options.scala.attribute.descriptor.string");
    static final String VALID_STRING_ESCAPE = ScalaOptionsBundle.message("options.scala.attribute.descriptor.valid.escape.in.string");
    static final String INVALID_STRING_ESCAPE = ScalaOptionsBundle.message("options.scala.attribute.descriptor.invalid.escape.in.string");
    static final String BRACKETS = ScalaOptionsBundle.message("options.scala.attribute.descriptor.brackets");
    static final String BRACES = ScalaOptionsBundle.message("options.scala.attribute.descriptor.braces");
    static final String COLON = ScalaOptionsBundle.message("options.scala.attribute.descriptor.colon");
    static final String PARENTHESES = ScalaOptionsBundle.message("options.scala.attribute.descriptor.parentheses");
    static final String ASSIGN = ScalaOptionsBundle.message("options.scala.attribute.descriptor.assign");
    static final String ARROW = ScalaOptionsBundle.message("options.scala.attribute.descriptor.arrow");
    static final String SEMICOLON = ScalaOptionsBundle.message("options.scala.attribute.descriptor.semicolon");
    static final String DOT = ScalaOptionsBundle.message("options.scala.attribute.descriptor.dot");
    static final String COMMA = ScalaOptionsBundle.message("options.scala.attribute.descriptor.comma");
    static final String INTERPOLATED_STRING_INJECTION = ScalaOptionsBundle.message("options.scala.attribute.descriptor.interpolated.string.injection");
    static final String MUTABLE_COLLECTION = ScalaOptionsBundle.message("options.scala.attribute.descriptor.mutable.collection");
    static final String IMMUTABLE_COLLECTION = ScalaOptionsBundle.message("options.scala.attribute.descriptor.immutable.collection");
    static final String JAVA_COLLECTION = ScalaOptionsBundle.message("options.scala.attribute.descriptor.standard.java.collection");
    static final String TYPEPARAM = ScalaOptionsBundle.message("options.scala.attribute.descriptor.type.parameter");
    static final String PREDEF = ScalaOptionsBundle.message("options.scala.attribute.descriptor.predefined.types");
    static final String OBJECT = ScalaOptionsBundle.message("options.scala.attribute.descriptor.object");
    static final String CLASS = ScalaOptionsBundle.message("options.scala.attribute.descriptor.class");
    static final String BAD_CHARACTER = ScalaOptionsBundle.message("options.scala.attribute.descriptor.bad.character");
    static final String DOC_COMMENT = ScalaOptionsBundle.message("options.scala.attribute.descriptor.scaladoc.comment");
    static final String SCALA_DOC_TAG = ScalaOptionsBundle.message("options.scala.attribute.descriptor.scaladoc.comment.tag");
    static final String SCALA_DOC_HTML_TAG = ScalaOptionsBundle.message("options.scala.attribute.descriptor.scaladoc.html.tag");
    static final String SCALA_DOC_WIKI_SYNTAX = ScalaOptionsBundle.message("options.scala.attribute.descriptor.scaladoc.wiki.syntax.elements");
    static final String SCALA_DOC_HTML_ESCAPE = ScalaOptionsBundle.message("options.scala.attribute.descriptor.scaladoc.html.escape.sequences");
    static final String SCALA_DOC_TAG_PARAM_VALUE = ScalaOptionsBundle.message("options.scala.attribute.descriptor.scaladoc.param.value");
    static final String IMPLICIT_CONVERSIONS = ScalaOptionsBundle.message("options.scala.attribute.descriptor.implicit.conversion");
    static final String ABSTRACT_CLASS = ScalaOptionsBundle.message("options.scala.attribute.descriptor.abstract.class");
    static final String TRAIT = ScalaOptionsBundle.message("options.scala.attribute.descriptor.trait");
    static final String LOCAL_VALUES = ScalaOptionsBundle.message("options.scala.attribute.descriptor.local.value");
    static final String LOCAL_VARIABLES = ScalaOptionsBundle.message("options.scala.attribute.descriptor.local.variable");
    static final String LOCAL_LAZY = ScalaOptionsBundle.message("options.scala.attribute.descriptor.local.lazy.val.var");
    static final String VALUES = ScalaOptionsBundle.message("options.scala.attribute.descriptor.template.val");
    static final String VARIABLES = ScalaOptionsBundle.message("options.scala.attribute.descriptor.template.var");
    static final String LAZY = ScalaOptionsBundle.message("options.scala.attribute.descriptor.template.lazy.val.var");
    static final String PARAMETER = ScalaOptionsBundle.message("options.scala.attribute.descriptor.parameter");
    static final String ANONYMOUS_PARAMETER = ScalaOptionsBundle.message("options.scala.attribute.descriptor.anonymous.parameter");
    static final String METHOD_CALL = ScalaOptionsBundle.message("options.scala.attribute.descriptor.class.method.call");
    static final String OBJECT_METHOD_CALL = ScalaOptionsBundle.message("options.scala.attribute.descriptor.object.method.call");
    static final String LOCAL_METHOD_CALL = ScalaOptionsBundle.message("options.scala.attribute.descriptor.local.method.call");
    static final String METHOD_DECLARATION = ScalaOptionsBundle.message("options.scala.attribute.descriptor.method.declaration");
    static final String ANNOTATION = ScalaOptionsBundle.message("options.scala.attribute.descriptor.annotation.name");
    static final String ANNOTATION_ATTRIBUTE = ScalaOptionsBundle.message("options.scala.attribute.descriptor.annotation.attribute.name");
    static final String PATTERN = ScalaOptionsBundle.message("options.scala.attribute.descriptor.pattern.value");
    static final String GENERATOR = ScalaOptionsBundle.message("options.scala.attribute.descriptor.for.statement.value");
    static final String TYPE_ALIAS = ScalaOptionsBundle.message("options.scala.attribute.descriptor.type.alias");

    static final String XML_TAG = ScalaOptionsBundle.message("options.scala.attribute.descriptor.scala.xml.tag");
    static final String XML_TAG_NAME = ScalaOptionsBundle.message("options.scala.attribute.descriptor.scala.xml.tag.name");
    static final String XML_TAG_DATA = ScalaOptionsBundle.message("options.scala.attribute.descriptor.scala.xml.tag.data");
    static final String XML_ATTRIBUTE_NAME = ScalaOptionsBundle.message("options.scala.attribute.descriptor.scala.xml.attribute.name");
    static final String XML_ATTRIBUTE_VALUE = ScalaOptionsBundle.message("options.scala.attribute.descriptor.scala.xml.attribute.value");
    static final String XML_COMMENT = ScalaOptionsBundle.message("options.scala.attribute.descriptor.scala.xml.comment");
    static final String SCALATEST_KEYWORD = ScalaOptionsBundle.message("options.scala.attribute.descriptor.scalatest.keyword");
  }
}
