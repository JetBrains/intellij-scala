package org.jetbrains.plugins.scala.lang.lexer.core;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypesEx;
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes;
import com.intellij.openapi.util.text.StringUtil;

import static com.intellij.openapi.util.text.StringUtil.endsWith;
import static org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType.*;
import static org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.*;

@SuppressWarnings({"ALL"})
%%

%class _ScalaCoreLexer
%implements FlexLexer
%unicode
%public

%function advance
%type IElementType

%eof{
  return;
%eof}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////// USER CODE //////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%{
    public _ScalaCoreLexer(boolean isScala3) {
      this((java.io.Reader) null);
      this.isScala3 = isScala3;
    }

    private static abstract class InterpolatedStringLevel {
      private int value = 0;

      public final boolean isRaw;

      public InterpolatedStringLevel(boolean isRaw) {
        this.isRaw = isRaw;
      }
      public InterpolatedStringLevel(CharSequence interpolator) {
        this(StringUtil.equal(interpolator, "raw", true));
      }

      public int get() {
        return value;
      }

      public boolean isZero() {
        return value == 0;
      }

      public void increase() {
        ++value;
      }

      public void decrease() {
        --value;
      }

      public abstract int getState();
    }

    private static class RegularLevel extends InterpolatedStringLevel {
      public RegularLevel(CharSequence intepolator) {
        super(intepolator);
      }
      public int getState() {
        return INSIDE_INTERPOLATED_STRING;
      }
    }

    private static class MultilineLevel extends InterpolatedStringLevel {
      public MultilineLevel(CharSequence intepolator) {
        super(intepolator);
      }
      public int getState() {
        return INSIDE_MULTI_LINE_INTERPOLATED_STRING;
      }
    }

    private boolean isScala3;

    //
    // NOTE: when adding new mutable state, do not forget to update `reset_ScalaLexer`
    //
    //to get id after $ in interpolated String
    private boolean haveIdInString = false;
    private boolean haveIdInMultilineString = false;
    // Currently opened interpolated Strings. Each int represents the number of the opened left structural braces in the String
    private Stack<InterpolatedStringLevel> nestedString = new Stack<>();
    private CharSequence lastSeenInterpolator = null;

    private boolean isInsideRawInterpolator() {
      return !nestedString.isEmpty() && nestedString.peek().isRaw;
    }

    public void resetCustom() {
      haveIdInString = false;
      haveIdInMultilineString = false;
      nestedString.clear();
      lastSeenInterpolator = null;
    }

    public boolean isInterpolatedStringState() {
        return isInsideInterpolatedString() ||
               haveIdInString ||
               haveIdInMultilineString ||
               yystate() == INSIDE_INTERPOLATED_STRING ||
               yystate() == INSIDE_MULTI_LINE_INTERPOLATED_STRING;
    }

    private boolean shouldProcessBracesForInterpolated() {
      return isInsideInterpolatedString();
    }
    private boolean isInsideInterpolatedString() {
      return !nestedString.isEmpty();
    }

    @NotNull
    private IElementType processOutsideString() {
      return processOutsideString(tINTERPOLATED_STRING_END);
    }

    private IElementType processOutsideString(IElementType typ) {
      if (isInsideInterpolatedString())
        nestedString.pop();
      yybegin(COMMON_STATE);
      return process(typ);
    }

    @NotNull
    private IElementType process(@NotNull final IElementType type){
      if ((type == tIDENTIFIER || type == kTHIS)) {
        if (haveIdInString) {
          haveIdInString = false;
          yybegin(INSIDE_INTERPOLATED_STRING);
        } else if (haveIdInMultilineString) {
          haveIdInMultilineString = false;
          yybegin(INSIDE_MULTI_LINE_INTERPOLATED_STRING);
        }
      }

      if (yystate() == YYINITIAL && type != tWHITE_SPACE_IN_LINE && type != tLBRACE && type != tLPARENTHESIS) {
        yybegin(COMMON_STATE);
      }

      // see comments to tINTERPOLATED_RAW_STRING and tINTERPOLATED_MULTILINE_RAW_STRING and
      final IElementType typeAdjusted;
      if (type == tINTERPOLATED_STRING && isInsideRawInterpolator())
        typeAdjusted =  tINTERPOLATED_RAW_STRING;
      else if (type == tINTERPOLATED_MULTILINE_STRING && isInsideRawInterpolator())
        typeAdjusted = tINTERPOLATED_MULTILINE_RAW_STRING;
      else
        typeAdjusted = type;

      return typeAdjusted;
    }

    @NotNull
    private IElementType processDollarInsideString(boolean isInsideMultiline) {
        final IElementType token;

        // TODO: remove this chech, this should always be false, cause $$ is handled by INTERPOLATED_STRING_ESCAPE pattern earlier
        boolean isDollarEscape = yycharat(1) == '$';
        if (isDollarEscape) {
            yypushback(yylength() - 2);
            token = tINTERPOLATED_STRING_ESCAPE;
        }
        else {
            if (isInsideMultiline) {
                haveIdInMultilineString = true;
            } else {
                haveIdInString = true;
            }
            yybegin(INJ_COMMON_STATE);
            yypushback(yylength() - 1);
            token = tINTERPOLATED_STRING_INJECTION;
        }
        return process(token);
    }

    private IElementType processScala3(@NotNull IElementType type) {
        return process(isScala3 ? type : tIDENTIFIER);
    }
%}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////      integers and floats     /////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

longLiteal = {integerLiteral} [Ll]
integerLiteral = {decimalNumeral} | {hexNumeral} | {binaryNumeral}
// this includes octal literals, which are deprecated since 2.11.0
decimalNumeral = [0-9] {digitOrUnderscore}*
hexNumeral = 0 [Xx] {hexDigitOrUnderscore}+
binaryNumeral = 0 [Bb] {binaryDigitOrUnderscore}+
digitOrUnderscore = [_0-9]
octalDigitOrUndescrore = [_0-7]
hexDigitOrUnderscore = [_0-9A-Fa-f]
binaryDigitOrUnderscore = [_01]

doubleLiteral = ({floatingDecimalNumber} [Dd]?)
          | ({fractionPart} [Dd])
floatingLiteral = ({floatingDecimalNumber} | {fractionPart}) [Ff]

floatingDecimalNumber = {digits} "." {digits}? {exponentPart}?
          | "." {fractionPart}
          | {digits} {exponentPart}

digits = [0-9] {digitOrUnderscore}*
exponentPart = [Ee] [+-]? {digits}
fractionPart = {digits} {exponentPart}?

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////      identifiers      ////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//these symbols can't be used as a single-character operator (https://github.com/scala/scala/pull/9801)
opchar1    = "#" | ":" | "=" | "@"
opchar2    = "!" | "%" | "&" | "*" | "+" | "-" | "/"
           | "<" | ">" | "?" | "\\" | "^" | "|" | "~"
           //see https://en.wikipedia.org/wiki/Unicode_character_property#General_Category
           | \p{So}
           | \p{Sm}
opchar     = {opchar1} | {opchar2}
op         = {opchar1} {opchar}+
           | {opchar2} {opchar}*
idrest     = [:jletterdigit:]* ("_" ({op} | {opchar1}))?
varid      = [:jletter:] {idrest}
plainid    = {varid} | {op}
identifier = {plainid} | "`" {stringLiteralExtra} "`"

octalDigit = [0-7]

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////// Comments ////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

END_OF_LINE_COMMENT="/""/"[^\r\n]*

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////// String & chars //////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// Latest (WIP) Dotty / Scala 3 syntax: https://dotty.epfl.ch/docs/internals/syntax.html
// Scala 2.13 syntax: https://www.scala-lang.org/files/archive/spec/2.13/13-syntax-summary.html
// Scala 2.12 syntax: https://www.scala-lang.org/files/archive/spec/2.12/13-syntax-summary.html
// Scala 2.11 syntax: https://www.scala-lang.org/files/archive/spec/2.11/13-syntax-summary.html

// NOTE 1: octal escape literals are:
//  - deprecated since 2.11.0 (https://github.com/scala/scala/pull/2342)
//  - dropped in 2.13.0 (https://github.com/scala/scala/pull/6324)
// NOTE 2: \377 is max value for octal literals, see https://docs.oracle.com/javase/specs/jls/se7/html/jls-3.html#jls-3.10.6
octalDigit = [0-7]
OCTAL_ESCAPE_LITERAL = \\ ({octalDigit} | {octalDigit} {octalDigit} | [0-3] {octalDigit} {octalDigit})

// ATTENTION:
// This lexer rules are a little bit weaker that in compiler.
// (e.g. we compure even invalid char escape sequences, like \x \j
// In addition to this "usual" lexer we also have syntax-highligting lexer
// (see org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighter)
// which have more stirict rules for validating string contents
hexDigit             = [0-9A-Fa-f]
CHAR_ESCAPE_SEQUENCE = \\[^\r\n]
UNICODE_ESCAPE       = \\u+ {hexDigit}{hexDigit}{hexDigit}{hexDigit} // Scala supports 1. multiple `u` chars after `\` 2. even \u000A ('\n') and \u000D (unlike Java)
ESCAPE_SEQUENCE      = {UNICODE_ESCAPE} | {CHAR_ESCAPE_SEQUENCE}
CHARACTER_LITERAL    = "'"([^\\\'\r\n]|{ESCAPE_SEQUENCE}|{OCTAL_ESCAPE_LITERAL})("'"|\\) | \'\\u000A\' | "'''" // TODO: \'\\u000A\' is redundunt, remove

STRING_BEGIN = \"([^\\\"\r\n]|{CHAR_ESCAPE_SEQUENCE})*
STRING_LITERAL={STRING_BEGIN} \"
MULTI_LINE_STRING = \"\"\" ( (\"(\")?)? [^\"] )* \"\"\" (\")* // Multi-line string

////////String Interpolation////////
INTERPOLATED_STRING_ID = {varid}

INTERPOLATED_STRING_BEGIN = \"{INTERPOLATED_STRING_PART}*
INTERPOLATED_STRING_PART = {INTERPOLATED_STRING_PART_NOT_ESCAPED}|{ESCAPE_SEQUENCE}
INTERPOLATED_STRING_PART_NOT_ESCAPED = [^\\\"\r\n\$]

INTERPOLATED_MULTI_LINE_STRING_BEGIN = \"\"\"{INTERPOLATED_MULTI_LINE_STRING_PART}*
INTERPOLATED_MULTI_LINE_STRING_PART = ((\"(\")?)? [^\"\$])

// TODO: rename, it's missleading
INTERPOLATED_STRING_ESCAPE = "$$"
//INTERPOLATED_STRING_VARIABLE = "$"({identifier})
//INTERPOLATED_STRING_EXPRESSION_START = "${"
////////////////////////////////////


WRONG_STRING = {STRING_BEGIN}

charEscapeSeq = \\[^\r\n]
//charNoDoubleQuote = !( ![^"\""] | {LineTerminator})
//stringElement = {charNoDoubleQuote} | {charEscapeSeq}
//stringLiteral = {stringElement}*
charExtra = !( ![^`] | {LineTerminator})             //This is for `type` identifiers
stringElementExtra = {charExtra} | {charEscapeSeq}
stringLiteralExtra = {stringElementExtra}*
symbolLiteral = "\'" {plainid}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////// Common symbols //////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

LineTerminator = \r | \n | \r\n | \u0085 |  \u2028 | \u2029 | \u000A | \u000a
WhiteSpace = " " | "\t" | "\f"
mNLS = {LineTerminator} ({LineTerminator} | {WhiteSpace})*

XML_BEGIN = "<" ("_" | [:jletter:]) | "<!--" | "<?" ("_" | [:jletter:]) | "<![CDATA["


////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////  states ///////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%state COMMON_STATE
%xstate WAIT_FOR_INTERPOLATED_STRING
%xstate INSIDE_INTERPOLATED_STRING
%xstate INSIDE_MULTI_LINE_INTERPOLATED_STRING
%xstate INJ_COMMON_STATE

%%

//YYINITIAL is alias for WAIT_FOR_XML, so state will be initial after every space or new line token
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////  XML processing ///////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

<YYINITIAL> {
  {XML_BEGIN}  {
    yybegin(COMMON_STATE);
    yypushback(yylength());
    return ScalaTokenTypesEx.SCALA_XML_CONTENT_START;
  }
}

{END_OF_LINE_COMMENT} { return process(tLINE_COMMENT); }


{INTERPOLATED_STRING_ID} / ({INTERPOLATED_STRING_BEGIN} | {INTERPOLATED_MULTI_LINE_STRING_BEGIN}) {
  yybegin(WAIT_FOR_INTERPOLATED_STRING);
  // TODO: remove this check: looks like it's a dead code,
  //  yytext() should only return text that is matched by INTERPOLATED_STRING_ID, which can't end with \"\"
  if (endsWith(yytext(), "\"\"")) yypushback(2);
  lastSeenInterpolator = yytext();
  IElementType token = haveIdInString || haveIdInMultilineString ? tIDENTIFIER : tINTERPOLATED_STRING_ID;
  return process(token);
}

<WAIT_FOR_INTERPOLATED_STRING> {
  {INTERPOLATED_STRING_BEGIN} {
    yypushback(yylength() - 1); // only push opening quote
    yybegin(INSIDE_INTERPOLATED_STRING);
    nestedString.push(new RegularLevel(lastSeenInterpolator));
    return process(tINTERPOLATED_STRING);
  }

  {INTERPOLATED_MULTI_LINE_STRING_BEGIN} {
    yybegin(INSIDE_MULTI_LINE_INTERPOLATED_STRING);
    nestedString.push(new MultilineLevel(lastSeenInterpolator));
    return process(tINTERPOLATED_MULTILINE_STRING);
  }
}

<INJ_COMMON_STATE> {identifier} {
  int length = yylength();
  int number = length;
  for (int i = 1; i < length; i++) {
    if (yycharat(i) == '$') {
      number = i;
      break;
    }
  }

  yypushback(length - number);
  boolean isThis = "this".contentEquals(yytext());
  return process(isThis ? kTHIS : tIDENTIFIER);
}

<INJ_COMMON_STATE> [^] {
  return process(tWRONG_STRING);
}

<INSIDE_INTERPOLATED_STRING> {
  {INTERPOLATED_STRING_ESCAPE} {
    return process(tINTERPOLATED_STRING_ESCAPE);
  }

  {INTERPOLATED_STRING_PART_NOT_ESCAPED}+ {
    return process(tINTERPOLATED_STRING);
  }
  {UNICODE_ESCAPE} {
    return process(tINTERPOLATED_STRING);
  }
  {CHAR_ESCAPE_SEQUENCE} {
    if (isInsideRawInterpolator()) {
      yypushback(1); // from "\t" push "t" back, also if we have "\" we don't want " to be captured
    }
    return process(tINTERPOLATED_STRING);
  }

  "$"{identifier} {
    return processDollarInsideString(false);
  }

  \" {
    return processOutsideString();
  }

  "$" / "{" {
    yybegin(COMMON_STATE);
    return process(tINTERPOLATED_STRING_INJECTION);
  }

  \r*\n {
    //don't add new lines to string itself, add empty error
    yypushback(yylength());
    return processOutsideString(tWRONG_LINE_BREAK_IN_STRING);
  }

  [^] {
    return process(tWRONG_STRING);
  }
}

<INSIDE_MULTI_LINE_INTERPOLATED_STRING> {
  {INTERPOLATED_STRING_ESCAPE} {
    return process(tINTERPOLATED_STRING_ESCAPE);
  }

  (\"\") / "$" {
    return process(tINTERPOLATED_MULTILINE_STRING);
  }

  {INTERPOLATED_MULTI_LINE_STRING_PART}+ {
    return process(tINTERPOLATED_MULTILINE_STRING);
  }

  "$"{identifier} {
    return processDollarInsideString(true);
  }

  \"\"\" (\")+ {
    yypushback(yylength() - 1);
    return process(tINTERPOLATED_MULTILINE_STRING);
  }

  \"\"\" {
      return processOutsideString();
  }

  "$" / "{" {
      yybegin(COMMON_STATE);
      return process(tINTERPOLATED_STRING_INJECTION);
  }

  \" / [^\"] {
    return process(tINTERPOLATED_MULTILINE_STRING);
  }

  [^] {
    return process(tWRONG_STRING);
  }
}


"/**" ("*"? [^\/])* "*/" { //for comments in interpolated strings
    return process(ScalaDocElementTypes.SCALA_DOC_COMMENT);
}

"/*" ("*"? [^\/])* "*/" { //for comments in interpolated strings
    return process(tBLOCK_COMMENT);
}

{STRING_LITERAL}                        {   return process(tSTRING);  }

{MULTI_LINE_STRING}                     {   return process(tMULTILINE_STRING);  }

// TODO: incomplete strings should be handled the same way with interpolated strings
//  what can be parsed should be parsed as tSTRING,
//  tWRONG_LINE_BREAK_IN_STRING error token should be added at unexpected new line should
{WRONG_STRING}                          {   return process(tWRONG_STRING);  }


{symbolLiteral}                          {  return process(tSYMBOL);  }

"''" / [^']                              {  return process(tCHAR);  }
{CHARACTER_LITERAL}                      {  return process(tCHAR);  }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// braces ///////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
"["                                     {   return process(tLSQBRACKET); }
"]"                                     {   return process(tRSQBRACKET); }

"{"                                     {   if (shouldProcessBracesForInterpolated()) {
                                              nestedString.peek().increase();
                                            }

                                            return process(tLBRACE); }
"{"{XML_BEGIN}                          {   if (shouldProcessBracesForInterpolated()) {
                                              nestedString.peek().increase();
                                            }

                                            yypushback(yylength() - 1);
                                            yybegin(YYINITIAL);
                                            return process(tLBRACE); }

"}"                                     {   if (shouldProcessBracesForInterpolated()) {
                                              InterpolatedStringLevel level = nestedString.peek();
                                              level.decrease();

                                              if (level.isZero()) {
                                                yybegin(level.getState());
                                              }
                                            }
                                            return process(tRBRACE); }

"("                                     {   return process(tLPARENTHESIS); }

"("{XML_BEGIN}                          {   yypushback(yylength() - 1);
                                            yybegin(YYINITIAL);
                                            return process(tLPARENTHESIS); }
")"                                     {   return process(tRPARENTHESIS); }

"$" / "{" {
  if (isScala3) {
    return processScala3(SpliceStart());
  }
}

"'" / ("{" | "[") {
  if (isScala3) {
    return processScala3(QuoteStart());
  }
}

"=>>" { return processScala3(TypeLambdaArrow()); }

"?=>" { return processScala3(ImplicitFunctionArrow()); }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// keywords /////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

"abstract"                              {   return process(kABSTRACT); }

"case" / ({LineTerminator}|{WhiteSpace})+("class" | "object")
                                        {   return process(kCASE); }

"case"                                  {   return process(kCASE); }

"catch"                                 {   return process(kCATCH); }
"class"                                 {   return process(ClassKeyword()); }
"def"                                   {   return process(kDEF); }
"do"                                    {   return process(kDO); }
"else"                                  {   return process(kELSE); }
"enum"                                  {   return processScala3(EnumKeyword()); }
"export"                                {   return processScala3(ExportKeyword()); }
"extends"                               {   return process(kEXTENDS); }
"false"                                 {   return process(kFALSE); }
"final"                                 {   return process(kFINAL); }
"finally"                               {   return process(kFINALLY); }
"for"                                   {   return process(kFOR); }
"forSome"                               {   return process(kFOR_SOME); }
"given"                                 {   return processScala3(GivenKeyword()); }
"if"                                    {   return process(kIF); }
"implicit"                              {   return process(kIMPLICIT); }
"import"                                {   return process(kIMPORT); }
"lazy"                                  {   return process(kLAZY); }
"match"                                 {   return process(kMATCH); }
"new"                                   {   return process(NewKeyword()); }
"null"                                  {   return process(kNULL); }
"object"                                {   return process(ObjectKeyword()); }
"override"                              {   return process(kOVERRIDE); }
"package"                               {   return process(kPACKAGE); }
"private"                               {   return process(kPRIVATE); }
"protected"                             {   return process(kPROTECTED); }
"return"                                {   return process(kRETURN); }
"sealed"                                {   return process(kSEALED); }
"super"                                 {   return process(kSUPER); }
"then"                                  {   return processScala3(ThenKeyword()); }
"this"                                  {   return process(kTHIS); }
"throw"                                 {   return process(kTHROW); }
"trait"                                 {   return process(TraitKeyword()); }
"try"                                   {   return process(kTRY); }
"true"                                  {   return process(kTRUE); }
"type"                                  {   return process(kTYPE); }
"val"                                   {   return process(kVAL); }
"var"                                   {   return process(kVAR); }
"while"                                 {   return process(kWHILE); }
"with"                                  {   return process(kWITH); }
"yield"                                 {   return process(kYIELD); }
"macro"                                 {   return process(kMACRO); }
//"inline"                                {   return process(kINLINE); }

///////////////////// Reserved shorthands //////////////////////////////////////////

"*"                                     {   return process(tIDENTIFIER);  }

"_"                                     {   return process(tUNDER);  }
":"                                     {   return process(tCOLON);  }
"="                                     {   return process(tASSIGN);  }

"=>" | "\u21D2"                         {   return process(tFUNTYPE); }
"<-" | "\u2190"                         {   return process(tCHOOSE); }

"<:"                                    {   return process(tUPPER_BOUND); }
">:"                                    {   return process(tLOWER_BOUND); }
"<%"                                    {   return process(tVIEW); }
"#"                                     {   return process(tINNER_CLASS); }
"@"                                     {   return process(tAT);}

"&"                                     {   return process(tIDENTIFIER);}
"|"                                     {   return process(tIDENTIFIER);}
"+"                                     {   return process(tIDENTIFIER); }
"-"                                     {   return process(tIDENTIFIER);}
"~"                                     {   return process(tIDENTIFIER);}
"!"                                     {   return process(tIDENTIFIER);}

"."                                     {   return process(tDOT);}
";"                                     {   return process(tSEMICOLON);}
","                                     {   return process(tCOMMA);}

{identifier}                            {   return process(tIDENTIFIER); }

{integerLiteral} / "." {identifier}     {   return process(Integer());  }
{doubleLiteral}                         {   return process(Double());}
{floatingLiteral}                       {   return process(Float());      }
{longLiteal}                            {   return process(Long());}
{integerLiteral}                        {   return process(Integer());  }
{WhiteSpace}                            {   yybegin(YYINITIAL);
                                            return process(tWHITE_SPACE_IN_LINE);  }
{mNLS}                                  {   yybegin(YYINITIAL);
                                            return process(tWHITE_SPACE_IN_LINE); }

////////////////////// STUB ///////////////////////////////////////////////
.                                       {   return process(tSTUB); }

