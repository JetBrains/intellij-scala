package org.jetbrains.plugins.scala.lang.scaladoc.lexer;

import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;

import java.io.IOException;

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.07.2008
 */

public class ScalaDocLexer extends MergingLexerAdapter {
  private static final TokenSet TOKENS_TO_MERGE = TokenSet.create(
    ScalaDocTokenType.DOC_COMMENT_DATA,
    ScalaDocTokenType.DOC_WHITESPACE
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
      start(buffer, startOffset, endOffset);
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
        while (myTokenEndOffset < myBufferEndOffset && myBuffer.charAt(myTokenEndOffset) == '*' &&
               (myTokenEndOffset + 1 >= myBufferEndOffset || myBuffer.charAt(myTokenEndOffset + 1) != '/')) {
          myTokenEndOffset++;
        }

        myInLeadingSpace = true;
        if (myBufferIndex < myTokenEndOffset) {
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
                                                     myBuffer.charAt(myTokenEndOffset) == '<')) {
          myFlex.yybegin(_ScalaDocLexer.COMMENT_DATA_START);
        }

        if (myBufferIndex < myTokenEndOffset) {
          myTokenType = lf || state == _ScalaDocLexer.PARAM_TAG_SPACE || state == _ScalaDocLexer.TAG_DOC_SPACE || state == _ScalaDocLexer.INLINE_TAG_NAME || state == _ScalaDocLexer.DOC_TAG_VALUE_IN_PAREN
                        ? ScalaDocTokenType.DOC_WHITESPACE
                        : ScalaDocTokenType.DOC_COMMENT_DATA;

          return;
        }
      }

      flexLocateToken();
    }

    private void flexLocateToken() {
      try {
        myState = myFlex.yystate();
        myFlex.goTo(myBufferIndex);
        myTokenType = myFlex.advance();
        myTokenEndOffset = myFlex.getTokenEnd();
      }
      catch (IOException e) {
        // Can't be
      }
    }
  }
}
