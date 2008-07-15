/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.highlighter;

import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.SyntaxHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import org.jetbrains.annotations.NonNls;

/**
 * @author ilyas
 *         Date: 24.09.2006
 */
public class DefaultHighlighter {
  // Comments
  @NonNls
  static final String LINE_COMMENT_ID = "Line comment";
  @NonNls
  static final String BLOCK_COMMENT_ID = "Block comment";
  @NonNls
  static final String KEYWORD_ID = "Keyword";
  @NonNls
  static final String NUMBER_ID = "Number";
  @NonNls
  static final String STRING_ID = "String";
  @NonNls
  static final String BRACKETS_ID = "Brackets";
  @NonNls
  static final String BRACES_ID = "Braces";
  @NonNls
  static final String COLON_ID = "Colon";
  @NonNls
  static final String PARENTHESES_ID = "Parentheses";
  @NonNls
  static final String OPERATION_SIGN_ID = "Operator sign";

  @NonNls
  static final String BAD_CHARACTER_ID = "Bad character";

  @NonNls
  static final String DOC_COMMENT_ID = "Doc comment";

  // Registering TextAttributes
  static {
    TextAttributesKey.createTextAttributesKey(LINE_COMMENT_ID, SyntaxHighlighterColors.LINE_COMMENT.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(BLOCK_COMMENT_ID, SyntaxHighlighterColors.JAVA_BLOCK_COMMENT.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(KEYWORD_ID, SyntaxHighlighterColors.KEYWORD.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(NUMBER_ID, SyntaxHighlighterColors.NUMBER.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(STRING_ID, SyntaxHighlighterColors.STRING.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(BRACKETS_ID, SyntaxHighlighterColors.BRACKETS.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(BRACKETS_ID, SyntaxHighlighterColors.BRACKETS.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(BRACES_ID, SyntaxHighlighterColors.BRACES.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(PARENTHESES_ID, SyntaxHighlighterColors.PARENTHS.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(COLON_ID, SyntaxHighlighterColors.COMMA.getDefaultAttributes());


    TextAttributesKey.createTextAttributesKey(OPERATION_SIGN_ID, SyntaxHighlighterColors.OPERATION_SIGN.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(BAD_CHARACTER_ID, HighlighterColors.BAD_CHARACTER.getDefaultAttributes());
  }

  public static TextAttributesKey LINE_COMMENT = TextAttributesKey.createTextAttributesKey(LINE_COMMENT_ID);
  public static TextAttributesKey BLOCK_COMMENT = TextAttributesKey.createTextAttributesKey(BLOCK_COMMENT_ID);
  public static TextAttributesKey KEYWORD = TextAttributesKey.createTextAttributesKey(KEYWORD_ID);
  public static TextAttributesKey NUMBER = TextAttributesKey.createTextAttributesKey(NUMBER_ID);
  public static TextAttributesKey STRING = TextAttributesKey.createTextAttributesKey(STRING_ID);
  public static TextAttributesKey BRACKETS = TextAttributesKey.createTextAttributesKey(BRACKETS_ID);
  public static TextAttributesKey BRACES = TextAttributesKey.createTextAttributesKey(BRACES_ID);
  public static TextAttributesKey PARENTHESES = TextAttributesKey.createTextAttributesKey(PARENTHESES_ID);
  public static TextAttributesKey COLON = TextAttributesKey.createTextAttributesKey(COLON_ID);


  public static TextAttributesKey OPERATION_SIGN = TextAttributesKey.createTextAttributesKey(OPERATION_SIGN_ID);
  public static TextAttributesKey BAD_CHARACTER = TextAttributesKey.createTextAttributesKey(BAD_CHARACTER_ID);

  public static TextAttributesKey DOC_COMMENT = TextAttributesKey.createTextAttributesKey(DOC_COMMENT_ID,
          SyntaxHighlighterColors.DOC_COMMENT.getDefaultAttributes());

}


