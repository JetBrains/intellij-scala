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
            new AttributesDescriptor(DefaultHighlighter.OPERATION_SIGN_ID, DefaultHighlighter.OPERATION_SIGN),
            new AttributesDescriptor(DefaultHighlighter.PARENTHESES_ID, DefaultHighlighter.PARENTHESES),
            new AttributesDescriptor(DefaultHighlighter.BRACES_ID, DefaultHighlighter.BRACES),
            new AttributesDescriptor(DefaultHighlighter.BRACKETS_ID, DefaultHighlighter.BRACKETS),
            new AttributesDescriptor(DefaultHighlighter.COLON_ID, DefaultHighlighter.COLON),
            new AttributesDescriptor(DefaultHighlighter.LINE_COMMENT_ID, DefaultHighlighter.LINE_COMMENT),
            new AttributesDescriptor(DefaultHighlighter.BLOCK_COMMENT_ID, DefaultHighlighter.BLOCK_COMMENT),
            new AttributesDescriptor(DefaultHighlighter.DOC_COMMENT_ID, DefaultHighlighter.DOC_COMMENT),
            new AttributesDescriptor(DefaultHighlighter.BAD_CHARACTER_ID, DefaultHighlighter.BAD_CHARACTER),
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
    return "<keyword>import</keyword> scala.collection.mutable._\n\n" +
            "<scaladoc>/**\n" +
            " * ScalaDoc comment\n" +
            " */</scaladoc>\n" +
            "<keyword>class</keyword> ScalaClass<par>(</par>x<colon>:</colon> Int<par>)</par> <keyword>extends</keyword>" +
            " ScalaObject <brace>{</brace>\n" +
            "<brace>}</brace>";
  }

  @Nullable
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    Map<String, TextAttributesKey> map = new HashMap<String, TextAttributesKey>();
    map.put("keyword", DefaultHighlighter.KEYWORD);
    map.put("par", DefaultHighlighter.PARENTHESES);
    map.put("brace", DefaultHighlighter.BRACES);
    map.put("colon", DefaultHighlighter.COLON);
    map.put("scaladoc", DefaultHighlighter.DOC_COMMENT);
    return map;
  }
}
