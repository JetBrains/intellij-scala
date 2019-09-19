package org.jetbrains.plugins.scala.lang.scaladoc.lexer;

import com.intellij.lexer.LexerBase;
import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import static org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType.*;

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.07.2008
 */
public final class ScalaDocLexer extends MergingLexerAdapter {
  
  private static final TokenSet TOKENS_TO_MERGE = TokenSet.create(
          DOC_COMMENT_DATA,
          DOC_WHITESPACE,
          DOC_INNER_CODE
  );

  public ScalaDocLexer() {
    super(new AsteriskStripperLexer(new _ScalaDocLexer()), TOKENS_TO_MERGE);
  }

  private static class AsteriskStripperLexer extends LexerBase {
    private _ScalaDocLexer myFlex;
    private CharSequence myBuffer;
    private int myBufferIndex;
    private int myBufferEndOffset;
    private int myTokenEndOffset;
    private int myState;
    private IElementType myTokenType;
    private boolean myAfterLineBreak;
    private boolean myInLeadingSpace;

    private boolean needItalic;
    private boolean needCorrectAfterItalic;
    private boolean hasPreviousBold;

    AsteriskStripperLexer(final _ScalaDocLexer flex) {
      myFlex = flex;
    }

    public final void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
      myBuffer = buffer;
      myBufferIndex =  startOffset;
      myBufferEndOffset = endOffset;
      myTokenType = null;
      myTokenEndOffset = startOffset;
      myFlex.reset(myBuffer, startOffset, endOffset, initialState);
    }

    public int getState() {
      return myState;
    }

    public char[] getBuffer() {
      return CharArrayUtil.fromSequence(myBuffer);
    }

    @NotNull
    public CharSequence getBufferSequence() {
      return myBuffer;
    }

    public int getBufferEnd() {
      return myBufferEndOffset;
    }

    public final IElementType getTokenType() {
      locateToken();
      return myTokenType;
    }

    public final int getTokenStart() {
      locateToken();
      return myBufferIndex;
    }

    public final int getTokenEnd() {
      locateToken();
      return myTokenEndOffset;
    }

    public final void advance() {
      locateToken();
      myTokenType = null;
    }

    final void locateToken() {
      if (myTokenType != null) return;
      _locateToken();

      if (myTokenType == DOC_WHITESPACE) {
        myAfterLineBreak = CharArrayUtil.containLineBreaks(myBuffer, getTokenStart(), getTokenEnd());
      }
    }

    private void _locateToken() {
      if (myTokenEndOffset == myBufferEndOffset) {
        myTokenType = null;
        myBufferIndex = myBufferEndOffset;
        return;
      }

      myBufferIndex = myTokenEndOffset;

      if (myAfterLineBreak) {

        myAfterLineBreak = false;
        myInLeadingSpace = true;

        if (myTokenEndOffset < myBufferEndOffset && myBuffer.charAt(myTokenEndOffset) == '*'
            && (myTokenEndOffset + 1 >= myBufferEndOffset || myBuffer.charAt(myTokenEndOffset + 1) != '/')) {
          myTokenEndOffset++;
          myTokenType = DOC_COMMENT_LEADING_ASTERISKS;
          return;
        }
      }

      if (myInLeadingSpace) {
        myInLeadingSpace = false;
        boolean lf = false;
        while (myTokenEndOffset < myBufferEndOffset && Character.isWhitespace(myBuffer.charAt(myTokenEndOffset))) {
          if (myBuffer.charAt(myTokenEndOffset) == '\n') lf = true;
          myTokenEndOffset++;
        }

        final int state = myFlex.yystate();
        if (state == _ScalaDocLexer.COMMENT_DATA ||
            myTokenEndOffset < myBufferEndOffset && (myBuffer.charAt(myTokenEndOffset) == '@' ||
                                                     myBuffer.charAt(myTokenEndOffset) == '{' ||
                                                     myBuffer.charAt(myTokenEndOffset) == '\"' ||
                                                     myBuffer.charAt(myTokenEndOffset) == '<') && state != _ScalaDocLexer.COMMENT_INNER_CODE) {
          myFlex.yybegin(_ScalaDocLexer.COMMENT_DATA_START);
        }

        if (myBufferIndex < myTokenEndOffset) {
          myTokenType = lf || state == _ScalaDocLexer.PARAM_TAG_SPACE || state == _ScalaDocLexer.TAG_DOC_SPACE || 
              state == _ScalaDocLexer.INLINE_TAG_NAME || state == _ScalaDocLexer.DOC_TAG_VALUE_IN_PAREN ||
              hasWhitespacesOnly(myBuffer, myBufferIndex, myTokenEndOffset - 1)
                  ? DOC_WHITESPACE
                  : DOC_COMMENT_DATA;

          if (!lf && state == _ScalaDocLexer.COMMENT_INNER_CODE) {
            myTokenType = DOC_INNER_CODE;
          }

          return;
        }
      }

      flexLocateToken();
    }

    private void flexLocateToken() {
      try {
        if (needItalic) {
          myTokenType = DOC_ITALIC_TAG;
          --myBufferIndex;
          myTokenEndOffset = myBufferIndex + 2;
          needItalic = false;
          needCorrectAfterItalic = true;
          return;
        } else if (needCorrectAfterItalic) {
          needCorrectAfterItalic = false;
          ++myBufferIndex;
        }

        myState = myFlex.yystate();
        myFlex.goTo(myBufferIndex);
        myTokenType = myFlex.advance();
        myTokenEndOffset = myFlex.getTokenEnd();

        if (myTokenType == DOC_BOLD_TAG && myTokenEndOffset < myBufferEndOffset - 1
            && myBuffer.charAt(myTokenEndOffset) == '\'' && myBuffer.charAt(myTokenEndOffset + 1) != '\'') {
          needItalic = true;
          myTokenType = DOC_ITALIC_TAG;
          --myTokenEndOffset;
        }
        if (myTokenType == DOC_BOLD_TAG) {
          hasPreviousBold = true;
        } else if (myTokenType == DOC_ITALIC_TAG && hasPreviousBold) {
          hasPreviousBold = false;

        }
      }
      catch (IOException e) {
        // Can't be
      }
    }

    private boolean hasWhitespacesOnly(CharSequence buffer, int start, int end) {
      int i = start;
      while (i < end) {
        if (buffer.charAt(i) > ' ') //see String#trim method
          return false;

        i += 1;
      }
      return true;
    }
  }
}
