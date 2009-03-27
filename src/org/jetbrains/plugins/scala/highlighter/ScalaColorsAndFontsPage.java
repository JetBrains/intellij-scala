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
import org.jetbrains.plugins.scala.icons.Icons;
import org.jetbrains.plugins.scala.ScalaFileType;

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
    return Icons.FILE_TYPE_LOGO;
  }

  @NotNull
  public AttributesDescriptor[] getAttributeDescriptors() {
    return ATTRS;
  }

  private static final AttributesDescriptor[] ATTRS;

  static {
    ATTRS = new AttributesDescriptor[]{
        new AttributesDescriptor(DefaultHighlighter.KEYWORD_ID, DefaultHighlighter.KEYWORD),
        new AttributesDescriptor(DefaultHighlighter.NUMBER_ID, DefaultHighlighter.NUMBER),
        new AttributesDescriptor(DefaultHighlighter.STRING_ID, DefaultHighlighter.STRING),
        new AttributesDescriptor(DefaultHighlighter.ASSIGN_ID, DefaultHighlighter.ASSIGN),
        new AttributesDescriptor(DefaultHighlighter.PARENTHESES_ID, DefaultHighlighter.PARENTHESES),
        new AttributesDescriptor(DefaultHighlighter.BRACES_ID, DefaultHighlighter.BRACES),
        new AttributesDescriptor(DefaultHighlighter.BRACKETS_ID, DefaultHighlighter.BRACKETS),
        new AttributesDescriptor(DefaultHighlighter.COLON_ID, DefaultHighlighter.COLON),
        new AttributesDescriptor(DefaultHighlighter.SEMICOLON_ID, DefaultHighlighter.SEMICOLON),
        new AttributesDescriptor(DefaultHighlighter.DOT_ID, DefaultHighlighter.DOT),
        new AttributesDescriptor(DefaultHighlighter.COMMA_ID, DefaultHighlighter.COMMA),
        new AttributesDescriptor(DefaultHighlighter.LINE_COMMENT_ID, DefaultHighlighter.LINE_COMMENT),
        new AttributesDescriptor(DefaultHighlighter.BLOCK_COMMENT_ID, DefaultHighlighter.BLOCK_COMMENT),
        new AttributesDescriptor(DefaultHighlighter.DOC_COMMENT_ID, DefaultHighlighter.DOC_COMMENT),
        new AttributesDescriptor(DefaultHighlighter.SCALA_DOC_TAG_ID, DefaultHighlighter.SCALA_DOC_TAG),
        new AttributesDescriptor(DefaultHighlighter.CLASS_ID, DefaultHighlighter.CLASS),
        new AttributesDescriptor(DefaultHighlighter.ABSTRACT_CLASS_ID, DefaultHighlighter.ABSTRACT_CLASS),
        new AttributesDescriptor(DefaultHighlighter.OBJECT_ID, DefaultHighlighter.OBJECT),
        new AttributesDescriptor(DefaultHighlighter.TYPEPARAM_ID, DefaultHighlighter.TYPEPARAM),
        new AttributesDescriptor(DefaultHighlighter.TYPE_ALIAS_ID, DefaultHighlighter.TYPE_ALIAS),
        new AttributesDescriptor(DefaultHighlighter.PREDEF_ID, DefaultHighlighter.PREDEF),
        new AttributesDescriptor(DefaultHighlighter.TRAIT_ID, DefaultHighlighter.TRAIT),
        new AttributesDescriptor(DefaultHighlighter.LOCAL_VALUES_ID, DefaultHighlighter.LOCAL_VALUES),
        new AttributesDescriptor(DefaultHighlighter.LOCAL_VARIABLES_ID, DefaultHighlighter.LOCAL_VARIABLES),
        new AttributesDescriptor(DefaultHighlighter.LOCAL_LAZY_ID, DefaultHighlighter.LOCAL_LAZY),
        new AttributesDescriptor(DefaultHighlighter.VALUES_ID, DefaultHighlighter.VALUES),
        new AttributesDescriptor(DefaultHighlighter.VARIABLES_ID, DefaultHighlighter.VARIABLES),
        new AttributesDescriptor(DefaultHighlighter.LAZY_ID, DefaultHighlighter.LAZY),
        new AttributesDescriptor(DefaultHighlighter.PARAMETER_ID, DefaultHighlighter.PARAMETER),
        new AttributesDescriptor(DefaultHighlighter.PATTERN_ID, DefaultHighlighter.PATTERN),
        new AttributesDescriptor(DefaultHighlighter.METHOD_CALL_ID, DefaultHighlighter.METHOD_CALL),
        new AttributesDescriptor(DefaultHighlighter.OBJECT_METHOD_CALL_ID, DefaultHighlighter.OBJECT_METHOD_CALL),
        new AttributesDescriptor(DefaultHighlighter.LOCAL_METHOD_CALL_ID, DefaultHighlighter.LOCAL_METHOD_CALL),
        new AttributesDescriptor(DefaultHighlighter.METHOD_DECLARATION_ID, DefaultHighlighter.METHOD_DECLARATION),
        new AttributesDescriptor(DefaultHighlighter.BAD_CHARACTER_ID, DefaultHighlighter.BAD_CHARACTER),
        new AttributesDescriptor(DefaultHighlighter.ANNOTATION_ID, DefaultHighlighter.ANNOTATION),
        new AttributesDescriptor(DefaultHighlighter.ANNOTATION_ATTRIBUTE_ID, DefaultHighlighter.ANNOTATION_ATTRIBUTE),
    };
  }

  @NotNull
  public ColorDescriptor[] getColorDescriptors() {
    return new ColorDescriptor[0];
  }

  @NotNull
  public SyntaxHighlighter getHighlighter() {
    return SyntaxHighlighterFactory.getSyntaxHighlighter(ScalaFileType.SCALA_LANGUAGE, null, null);
  }

  @NonNls
  @NotNull
  public String getDemoText() {
    return "<keyword>import</keyword> scala<dot>.</dot>collection<dot>.</dot>mutable<dot>.</dot>_\n\n" +
        "<scaladoc>/**\n" +
        " * ScalaDoc comment: <code>Some code</code>\n" +
        " * <tag>@author</tag> IntelliJ\n" +
        " */</scaladoc>\n" +
        "<keyword>class</keyword> <class>ScalaClass</class><par>(</par><param>x</param><colon>:</colon> <predef>Int</predef><par>)</par>" +
        " <keyword>extends</keyword>" +
        " <class>ScalaObject</class> <brace>{</brace>\n" +
        "  <keyword>val</keyword> <val>field</val> <assign>=</assign> <string>\"String\"</string>\n" +
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
        "<brace>}</brace>\n" +
        "\n" +
        "<blockcomment>/*\n" +
        "  And now ScalaObject\n" +
        " */</blockcomment>\n" +
        "<keyword>object</keyword> <object>Object</object> <brace>{</brace>\n" +
        "  <keyword>val</keyword> <val>layer</val> <assign>=</assign> <number>-5.0</number>\n" +
        "  <keyword>def</keyword> <methoddecl>foo</methoddecl><colon>:</colon> <class>ScalaClass</class> <assign>=</assign> " +
        "<keyword>new</keyword> <class>ScalaClass</class><par>(</par><number>23</number>, " +
        "<number>9</number><par>)</par>\n" +
        "<brace>}</brace>\n\n" +
        "<annotation>@Annotation</annotation><par>(</par><number>2</number><par>)</par> " +
        "<brace>{</brace><keyword>val</keyword> <attribute>name</attribute> <assign>=</assign> value<brace>}</brace>\n" +
        "<keyword>trait</keyword> <trait>Trait</trait> <brace>{</brace>\n" +
        "<brace>}</brace>\n\n" +
        "<keyword>abstract</keyword> <keyword>class</keyword> <abstract>SomeAbstract</abstract>\n\n";
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
    map.put("typeparam", DefaultHighlighter.TYPEPARAM);
    map.put("assign", DefaultHighlighter.ASSIGN);
    map.put("bracket", DefaultHighlighter.BRACKETS);
    map.put("dot", DefaultHighlighter.DOT);
    map.put("semicolon", DefaultHighlighter.SEMICOLON);
    map.put("comma", DefaultHighlighter.COMMA);
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
    map.put("method", DefaultHighlighter.METHOD_CALL);
    map.put("objectmethod", DefaultHighlighter.OBJECT_METHOD_CALL);
    map.put("localmethod", DefaultHighlighter.LOCAL_METHOD_CALL);
    map.put("methoddecl", DefaultHighlighter.METHOD_DECLARATION);
    map.put("pattern", DefaultHighlighter.PATTERN);
    map.put("typeAlias", DefaultHighlighter.TYPE_ALIAS);
    return map;
  }
}
