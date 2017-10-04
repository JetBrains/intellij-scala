package org.jetbrains.plugins.scala.lang.parser;

import com.intellij.lang.WhitespacesAndCommentsBinder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes;

import java.util.List;

public class ScalaTokenBinders {
  public static final WhitespacesAndCommentsBinder DEFAULT_LEFT_EDGE_BINDER = new WhitespacesAndCommentsBinder() {
    @Override
    public int getEdgePosition(final List<IElementType> tokens, final boolean atStreamEdge, final TokenTextGetter getter) {
      return tokens.size();
    }
  };

  public static final WhitespacesAndCommentsBinder PRECEEDING_COMMENTS_TOKEN = new WhitespacesAndCommentsBinder() {
    @Override
    public int getEdgePosition(final List<IElementType> tokens, final boolean atStreamEdge, final TokenTextGetter
        getter) {
      if (tokens.size() == 0) return 0;

      // bind doc comment
      for (int idx = tokens.size() - 1; idx >= 0; idx--) {
        if (tokens.get(idx) == ScalaDocElementTypes.SCALA_DOC_COMMENT) return idx;
      }

      // bind othercomments
      int i = tokens.size();

      for (int idx = tokens.size() - 1; idx >= 0; idx--) {
        IElementType type = tokens.get(idx);
        if (ScalaTokenTypes.COMMENTS_TOKEN_SET.contains(type)) {
          i = idx;
          continue;
        }
        if (type == ScalaTokenTypes.tWHITE_SPACE_IN_LINE && StringUtil.countChars(getter.get(idx), '\n') <= 1) {
          continue;
        }
        break;
      }

      return i;
    }
  };
}
