package org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiBuilder;
import static org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.ScalaDocParsing.RESULT.*;

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.07.2008
 */
public class ScalaDocParsing implements ScalaDocElementTypes {


  static enum RESULT {
    ERROR, METHOD, FIELD
  }

  @NonNls
  private static final String SEE_TAG = "@see";
  @NonNls
  private static final String LINK_TAG = "@link";
  @NonNls
  private static final String LINKPLAIN_TAG = "@linkplain";
  @NonNls
  private static final String THROWS_TAG = "@throws";
  @NonNls
  private static final String EXCEPTION_TAG = "@exception";
  @NonNls
  private static final String PARAM_TAG = "@param";
  @NonNls
  private static final String VALUE_TAG = "@value";

  private final static TokenSet REFERENCE_BEGIN = TokenSet.create(DOC_TAG_VALUE_TOKEN,
          DOC_TAG_VALUE_SHARP_TOKEN);

  private boolean isInInlinedTag = false;
  private int myBraceCounter = 0;


  public boolean parse(PsiBuilder builder) {

    while (parseDataItem(builder)) ;
    if (builder.getTokenType() == DOC_COMMENT_END) {
      while (!builder.eof()) {
        builder.advanceLexer();
      }
    }
    return true;
  }

  /**
   * Parses doc comment at toplevel
   *
   * @param builder given builder
   * @return false in case of commnet end
   */
  private boolean parseDataItem(PsiBuilder builder) {
    if (timeToEnd(builder)) return false;
    if (ParserUtils.lookAhead(builder, DOC_INLINE_TAG_START, DOC_TAG_NAME) && !isInInlinedTag) {
      isInInlinedTag = true;
      parseTag(builder);
    } else if (DOC_TAG_NAME == builder.getTokenType() && !isInInlinedTag) {
      parseTag(builder);
    } else {
      builder.advanceLexer();
    }
    return true;
  }

  private static boolean timeToEnd(PsiBuilder builder) {
    return builder.eof() || builder.getTokenType() == DOC_COMMENT_END;
  }

  private boolean parseTag(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    if (isInInlinedTag) {
      ParserUtils.getToken(builder, DOC_INLINE_TAG_START);
    }
    assert builder.getTokenType() == DOC_TAG_NAME;
    String tagName = builder.getTokenText();
    builder.advanceLexer();

    if (isInInlinedTag) {
      if (LINK_TAG.equals(tagName) || LINKPLAIN_TAG.equals(tagName)) {
        parseSeeOrLinkTagReference(builder);
      } else if (VALUE_TAG.equals(tagName)) {
        parseSeeOrLinkTagReference(builder);
      }
    } else {
      if (THROWS_TAG.equals(tagName) || EXCEPTION_TAG.equals(tagName)) {
        parseReferenceOrType(builder);
      } else if (SEE_TAG.equals(tagName)) {
        parseSeeOrLinkTagReference(builder);
      } else if (PARAM_TAG.equals(tagName)) {
        parseParamTagReference(builder);
      }
    }


    while (!timeToEnd(builder)) {
      if (isInInlinedTag) {
        if (builder.getTokenType() == DOC_INLINE_TAG_START) {
          myBraceCounter++;
          builder.advanceLexer();
        } else if (builder.getTokenType() == DOC_INLINE_TAG_END) {
          if (myBraceCounter > 0) {
            myBraceCounter--;
            builder.advanceLexer();
          } else {
            builder.advanceLexer();
            isInInlinedTag = false;
            marker.done(DOC_INLINED_TAG);
            return true;
          }
        } else {
          builder.advanceLexer();
        }
      } else if (ParserUtils.lookAhead(builder, DOC_INLINE_TAG_START, DOC_TAG_NAME)) {
        isInInlinedTag = true;
        parseTag(builder);
      } else if (DOC_TAG_NAME == builder.getTokenType()) {
        marker.done(DOC_TAG);
        return true;
      } else {
        builder.advanceLexer();
      }
    }
    marker.done(isInInlinedTag ? DOC_INLINED_TAG : DOC_TAG);
    isInInlinedTag = false;
    return true;
  }

  private boolean parseParamTagReference(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    if (DOC_TAG_VALUE_TOKEN == builder.getTokenType()) {
      builder.advanceLexer();
      marker.done(DOC_PARAM_REF);
      return true;
    } else if (ParserUtils.lookAhead(builder, DOC_TAG_VALUE_LT, DOC_TAG_VALUE_TOKEN)) {
      builder.advanceLexer();
      builder.getTokenText();
      builder.advanceLexer();
      if (DOC_TAG_VALUE_GT == builder.getTokenType()) {
        builder.advanceLexer();
      }
      marker.done(DOC_PARAM_REF);
      return true;
    }
    marker.drop();
    return false;
  }

  private boolean parseSeeOrLinkTagReference(PsiBuilder builder) {
    IElementType type = builder.getTokenType();
    if (!REFERENCE_BEGIN.contains(type)) return false;
    PsiBuilder.Marker marker = builder.mark();
    if (DOC_TAG_VALUE_TOKEN == type) {
      builder.advanceLexer();
    }
    if (DOC_TAG_VALUE_SHARP_TOKEN == builder.getTokenType()) {
      builder.advanceLexer();
      RESULT result = parseFieldOrMethod(builder);
      if (result == ERROR) {
        marker.drop();
      } else {
        marker.done(result == METHOD ? DOC_METHOD_REF : DOC_FIELD_REF);
      }
      return true;
    }
    marker.drop();
    return true;
  }

  private RESULT parseFieldOrMethod(PsiBuilder builder) {
    if (builder.getTokenType() != DOC_TAG_VALUE_TOKEN) return ERROR;
    builder.advanceLexer();
    PsiBuilder.Marker params = builder.mark();
    if (DOC_TAG_VALUE_LPAREN != builder.getTokenType()) {
      params.drop();
      return FIELD;
    }
    builder.advanceLexer();
    while (parseMethodParameter(builder) && !timeToEnd(builder)) {
      while (DOC_TAG_VALUE_COMMA != builder.getTokenType() &&
              DOC_TAG_VALUE_RPAREN != builder.getTokenType() &&
              !timeToEnd(builder)) {
        builder.advanceLexer();
      }
      while (DOC_TAG_VALUE_COMMA == builder.getTokenType()) {
        builder.advanceLexer();
      }
    }
    if (builder.getTokenType() == DOC_TAG_VALUE_RPAREN) {
      builder.advanceLexer();
    }
    params.done(DOC_METHOD_PARAMS);
    return METHOD;
  }

  private boolean parseMethodParameter(PsiBuilder builder) {
    PsiBuilder.Marker param = builder.mark();
    if (DOC_TAG_VALUE_TOKEN == builder.getTokenType()) {
      builder.advanceLexer();
    } else {
      param.drop();
      return false;
    }

    if (DOC_TAG_VALUE_TOKEN == builder.getTokenType()) {
      builder.advanceLexer();
    }
    param.done(DOC_METHOD_PARAMETER);

    return true;
  }

  private boolean parseReferenceOrType(PsiBuilder builder) {
    IElementType type = builder.getTokenType();
    if (DOC_TAG_VALUE_TOKEN != type) return false;
    return true;
  }
}
