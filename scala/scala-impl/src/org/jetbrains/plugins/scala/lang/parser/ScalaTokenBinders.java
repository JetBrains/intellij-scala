package org.jetbrains.plugins.scala.lang.parser;

import com.intellij.lang.WhitespacesAndCommentsBinder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes;

import java.util.List;

public class ScalaTokenBinders {

  public static final WhitespacesAndCommentsBinder PRECEDING_COMMENTS_TOKEN = new WhitespacesAndCommentsBinder() {
    @Override
    public int getEdgePosition(final List<IElementType> tokens, final boolean atStreamEdge, final TokenTextGetter getter) {
      if (tokens.isEmpty()) return 0;

      int tokensSize = tokens.size();

      /* Bind doc comment */
      for (int idx = tokensSize - 1; idx >= 0; idx--) {
        if (tokens.get(idx) == ScalaDocElementTypes.SCALA_DOC_COMMENT)
          return idx;
      }

      /* Bind other comments (mainly Line comments).
       * A comment is bound if:
       * 1) it has at most 1 line break between comment and definition/declaration
       * (note that it can have 0 line breaks for badly-formatted block comment before declaration)
       * 2) one-line comment starts from a new line, i.e are is not semantically-"attached" to the same line
       * (see testdata/parser/data/comments/binding)
       */
      int resultEdgeIdx = tokensSize;

      int lastCommentIdx = tokensSize;
      boolean lastCommentIsLine = false;

      for (int idx = tokensSize - 1; idx >= 0; idx--) {
        IElementType type = tokens.get(idx);

        if (type == ScalaTokenTypes.tWHITE_SPACE_IN_LINE) {
          // to the right can only have a comment or original expression which we are binding to
          int lineBreaks = StringUtil.countChars(getter.get(idx), '\n');

          if (lineBreaks == 0) {
            if (lastCommentIsLine) {
              break;
            } else {
              resultEdgeIdx = lastCommentIdx;
            }
          } else {
            resultEdgeIdx = lastCommentIdx;
            if (lineBreaks > 1) {
              break;
            }
          }

          continue;
        }

        if (ScalaTokenTypes.COMMENTS_TOKEN_SET.contains(type)) {
          lastCommentIdx = idx;
          lastCommentIsLine = type == ScalaTokenTypes.tLINE_COMMENT;
          continue;
        }

        break;
      }

      if (lastCommentIdx == 0) {
        resultEdgeIdx = lastCommentIdx;
      }

      return resultEdgeIdx;
    }
  };

  public static final WhitespacesAndCommentsBinder PRECEDING_WS_AND_COMMENT_TOKENS = new WhitespacesAndCommentsBinder() {
    @Override
    public int getEdgePosition(final List<IElementType> tokens, final boolean atStreamEdge, final TokenTextGetter getter) {
      if (tokens.isEmpty()) return 0;

      int tokensSize = tokens.size();
      int edgeIdx = tokensSize;

      for (int idx = tokensSize - 1; idx >= 0; idx--) {
        IElementType type = tokens.get(idx);
        if (!ScalaTokenTypes.WHITES_SPACES_AND_COMMENTS_TOKEN_SET.contains(type)) {
          break;
        }
        edgeIdx = idx;
      }

      return edgeIdx;
    }
  };
}
