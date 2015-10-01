package org.jetbrains.plugins.scala.lang.scaladoc.lexer;

import com.intellij.lexer.LexerBase;
import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.text.CharArrayUtil;

import java.io.IOException;

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.07.2008
 */

public class ScalaDocLexer extends MergingLexerAdapter {
  private static final TokenSet TOKENS_TO_MERGE = TokenSet.create(
    ScalaDocTokenType.DOC_COMMENT_DATA,
    ScalaDocTokenType.DOC_WHITESPACE,
    ScalaDocTokenType.DOC_INNER_CODE
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

    public AsteriskStripperLexer(final _ScalaDocLexer flex) {
      myFlex = flex;
    }

    public final void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
      myBuffer = buffer;
      myBufferIndex =  startOffset;
      myBufferEndOffset = endOffset;
      myTokenType = null;
      myTokenEndOffset = startOffset;
      myFlex.reset(myBuffer, startOffset, endOffset, initialState);
    }

    public final void start(char[] buffer, int startOffset, int endOffset, int initialState) {
      start(new String(buffer), startOffset, endOffset);
    }

    public int getState() {
      return myState;
    }

    public char[] getBuffer() {
      return CharArrayUtil.fromSequence(myBuffer);
    }

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

    protected final void locateToken() {
      if (myTokenType != null) return;
      _locateToken();

      if (myTokenType == ScalaDocTokenType.DOC_WHITESPACE) {
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
          myTokenType = ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS;
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
              myBuffer.toString().substring(myBufferIndex, myTokenEndOffset - 1).trim().length() == 0
                        ? ScalaDocTokenType.DOC_WHITESPACE
                        : ScalaDocTokenType.DOC_COMMENT_DATA;

          if (!lf && state == _ScalaDocLexer.COMMENT_INNER_CODE) {
            myTokenType = ScalaDocTokenType.DOC_INNER_CODE;
          }

          return;
        }
      }

      flexLocateToken();
    }

    private void flexLocateToken() {
      try {
        if (needItalic) {
          myTokenType = ScalaDocTokenType.DOC_ITALIC_TAG;
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

        if (myTokenType == ScalaDocTokenType.DOC_BOLD_TAG && myTokenEndOffset < myBufferEndOffset - 1
            && myBuffer.charAt(myTokenEndOffset) == '\'' && myBuffer.charAt(myTokenEndOffset + 1) != '\'') {
          needItalic = true;
          myTokenType = ScalaDocTokenType.DOC_ITALIC_TAG;
          --myTokenEndOffset;
        }
        if (myTokenType == ScalaDocTokenType.DOC_BOLD_TAG) {
          hasPreviousBold = true;
        } else if (myTokenType == ScalaDocTokenType.DOC_ITALIC_TAG && hasPreviousBold) {
          hasPreviousBold = false;

        }
      }
      catch (IOException e) {
        // Can't be
      }
    }
  }
}
