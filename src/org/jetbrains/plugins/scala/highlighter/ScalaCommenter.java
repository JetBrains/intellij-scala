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

import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.psi.PsiComment;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes;

/**
 * @author ilyas
 * Date: 29.09.2006
 * Time: 20:48:30
 */
public class ScalaCommenter implements CodeDocumentationAwareCommenter, ScalaTokenTypes, ScalaDocElementTypes {
  public String getLineCommentPrefix() {
    return "//";
  }

  public boolean isLineCommentPrefixOnZeroColumn() {
    return false;
  }

  public String getBlockCommentPrefix() {
    return "/*";
  }

  public String getCommentedBlockCommentPrefix() {
    return "/*";
  }

  public String getCommentedBlockCommentSuffix() {
    return "*/";
  }

  public String getBlockCommentSuffix() {
    return "*/";
  }

  @Nullable
  public IElementType getLineCommentTokenType() {
    return tLINE_COMMENT;
  }

  @Nullable
  public IElementType getBlockCommentTokenType() {
    return tBLOCK_COMMENT;
  }

  @Nullable
  public IElementType getDocumentationCommentTokenType() {
    return SCALA_DOC_COMMENT;
  }

  @Nullable
  public String getDocumentationCommentPrefix() {
    return "/**";
  }

  @Nullable
  public String getDocumentationCommentLinePrefix() {
    return "*";
  }

  @Nullable
  public String getDocumentationCommentSuffix() {
    return "*/";
  }

  public boolean isDocumentationComment(PsiComment element) {
    return element.getText().startsWith(getDocumentationCommentPrefix());
  }
}
