package org.jetbrains.plugins.scala.lang.lexer.core;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import java.util.*;
import java.lang.reflect.Field;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypesEx;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes;
import com.intellij.openapi.util.text.StringUtil;

%%

%class _ScalaCoreLexer
%implements FlexLexer, ScalaTokenTypesEx
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
    private static abstract class InterpolatedStringLevel {
      private int value = 0;
      
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
      public int getState() {
        return INSIDE_INTERPOLATED_STRING;
      }
    }
    
    private static class MultilineLevel extends InterpolatedStringLevel { 
      public int getState() {
        return INSIDE_MULTI_LINE_INTERPOLATED_STRING;
      }
    }

    //do we need to close interpolated String ${}
    private boolean insideInterpolatedStringBracers = false;
    private boolean insideInterpolatedMultilineStringBracers = false;
    //to get id after $ in interpolated String
    private boolean haveIdInString = false;
    private boolean haveIdInMultilineString = false;
    //bracers count inside injection
    private int structuralBracers = 0;
    // Currently opened interpolated Strings. Each int represents the number of the opened left structural braces in the String 
    private Stack<InterpolatedStringLevel> nestedString = new Stack<InterpolatedStringLevel>();
    
    public boolean isInterpolatedStringState() {
        return shouldProcessBracesForInterpolated()    || haveIdInString || haveIdInMultilineString || 
               yystate() == INSIDE_INTERPOLATED_STRING || yystate() == INSIDE_MULTI_LINE_INTERPOLATED_STRING;
    }
    
    private boolean shouldProcessBracesForInterpolated() {
      return !nestedString.empty();      
    }
    
    private void changeStringLevel() {
      if (!nestedString.isEmpty()) nestedString.pop();
      yybegin(COMMON_STATE);
    }

    private IElementType process(IElementType type){
      if ((type == tIDENTIFIER || type == kTHIS) && (haveIdInString || haveIdInMultilineString)) {

        if (haveIdInString) {
          haveIdInString = false;
          yybegin(INSIDE_INTERPOLATED_STRING);
        } else {
          haveIdInMultilineString = false;
          yybegin(INSIDE_MULTI_LINE_INTERPOLATED_STRING);
        }
      }
      
      if (yystate() == YYINITIAL && type != tWHITE_SPACE_IN_LINE && type != tLBRACE && type != tLPARENTHESIS) {
        yybegin(COMMON_STATE);
      }

      return type;
    }
    
    
    private void splitInjection() {
      CharSequence seq = yytext();
      for (int i = 1; i < seq.length(); ++i) {
        if (seq.charAt(i) == '$') {
          yypushback(seq.length() - i);
          return;
        }
      }
    }
%}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////      integers and floats     /////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

integerLiteral = ({decimalNumeral} | {hexNumeral} | {octalNumeral}) (L | l)?
decimalNumeral = 0 | {nonZeroDigit} {digit}*
hexNumeral = 0 (x | X) {hexDigit}+
octalNumeral = 0{octalDigit}+
digit = [0-9]
nonZeroDigit = [1-9]
octalDigit = [0-7]
hexDigit = [0-9A-Fa-f]

floatingPointLiteral =
      {digit}+ "." {digit}* {exponentPart}? {floatType}?
    | "." {digit}+ {exponentPart}? {floatType}?
    | {digit}+ {exponentPart} {floatType}?
    | {digit}+ {exponentPart}? {floatType}

exponentPart = (E | e) ("+" | "-")? {digit}+
floatType = F | f | D | d

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////      identifiers      ////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

identifier = {plainid} | "`" {stringLiteralExtra} "`"

digit = [0-9]
special = \u0021 | \u0023
          | [\u0025-\u0026]
          | [\u002A-\u002B]
          | \u002D | \u005E
          | \u003A
          | [\u003C-\u0040]
          | \u007E
          | \u005C | \u002F | [:unicode_math_symbol:] | [:unicode_other_symbol:] | \u2694


// Vertical line
op = \u007C ({special} | \u007C)+
     | {special} ({special} | \u007C)*
octalDigit = [0-7]

idrest1 = [:jletter:]? [:jletterdigit:]* ("_" {op})?
idrest = [:jletter:]? [:jletterdigit:]* ("_" {op} | "_" {idrest1} )?
varid = [:jletter:] {idrest}

plainid = {varid}
          | {op}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////// Comments ////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

END_OF_LINE_COMMENT="/""/"[^\r\n]*
SH_COMMENT="#!" [^]* "!#" | "::#!" [^]* "::!#"

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////// String & chars //////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


ESCAPE_SEQUENCE=\\[^\r\n]
UNICODE_ESCAPE=!(!(\\u{hexDigit}{hexDigit}{hexDigit}{hexDigit}) | \\u000A)
SOME_ESCAPE=\\{octalDigit} {octalDigit}? {octalDigit}?
CHARACTER_LITERAL="'"([^\\\'\r\n]|{ESCAPE_SEQUENCE}|{UNICODE_ESCAPE}|{SOME_ESCAPE})("'"|\\) | \'\\u000A\' | "'''"

STRING_BEGIN = \"([^\\\"\r\n]|{ESCAPE_SEQUENCE})*
STRING_LITERAL={STRING_BEGIN} \"
MULTI_LINE_STRING = \"\"\" ( (\"(\")?)? [^\"] )* \"\"\" (\")* // Multi-line string

////////String Interpolation////////
INTERPOLATED_STRING_ID = {varid}

INTERPOLATED_STRING_BEGIN = \"([^\\\"\r\n\$]|{ESCAPE_SEQUENCE})*
INTERPOLATED_STRING_PART = ([^\\\"\r\n\$]|{ESCAPE_SEQUENCE})+

INTERPOLATED_MULTI_LINE_STRING_BEGIN = \"\"\" ( (\"(\")?)? [^\"\$] )*
INTERPOLATED_MULTI_LINE_STRING_PART = ( (\"(\")?)? [^\"\$] )+

INTERPOLATED_STRING_ESCAPE = "$$"
INTERPOLATED_STRING_VARIABLE = "$"({identifier})
//INTERPOLATED_STRING_EXPRESSION_START = "${"
////////////////////////////////////


WRONG_STRING = {STRING_BEGIN}

charEscapeSeq = \\[^\r\n]
charNoDoubleQuote = !( ![^"\""] | {LineTerminator})
stringElement = {charNoDoubleQuote} | {charEscapeSeq}  
stringLiteral = {stringElement}*
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

<YYINITIAL>{

{XML_BEGIN}                             {   yybegin(COMMON_STATE);
                                            yypushback(yytext().length());
                                            return SCALA_XML_CONTENT_START;
                                        }
}

{END_OF_LINE_COMMENT}                   { return process(tLINE_COMMENT); }

{SH_COMMENT}                            { return process(tSH_COMMENT); }


{INTERPOLATED_STRING_ID} / ({INTERPOLATED_STRING_BEGIN} | {INTERPOLATED_MULTI_LINE_STRING_BEGIN}) {
  yybegin(WAIT_FOR_INTERPOLATED_STRING);
  if (StringUtil.endsWith(yytext(), "\"\"")) yypushback(2);
  return haveIdInString || haveIdInMultilineString ? process(tIDENTIFIER) : process(tINTERPOLATED_STRING_ID) ;
}

<WAIT_FOR_INTERPOLATED_STRING> {
  {INTERPOLATED_STRING_BEGIN} {
    yybegin(INSIDE_INTERPOLATED_STRING);
    nestedString.push(new RegularLevel());
    return process(tINTERPOLATED_STRING);
  }

  {INTERPOLATED_MULTI_LINE_STRING_BEGIN} {
    yybegin(INSIDE_MULTI_LINE_INTERPOLATED_STRING);
    nestedString.push(new MultilineLevel());
    return process(tINTERPOLATED_MULTILINE_STRING);
  }
}

<INJ_COMMON_STATE> {identifier} {
  splitInjection();
  if ("this".contentEquals(yytext())) return process(kTHIS); 
  return process(tIDENTIFIER);
}

<INJ_COMMON_STATE> [^] {
  return process(tWRONG_STRING);
}

<INSIDE_INTERPOLATED_STRING> {
  {INTERPOLATED_STRING_ESCAPE} {
    return process(tINTERPOLATED_STRING_ESCAPE);
  }

  {INTERPOLATED_STRING_PART} {
    return process(tINTERPOLATED_STRING);
  }

  "$"{identifier} {
    if (yycharat(1) != '$') {
      haveIdInString = true;
      yybegin(INJ_COMMON_STATE);
      yypushback(yytext().length() - 1);
      return process(tINTERPOLATED_STRING_INJECTION);
    } else {
      yypushback(yytext().length() - 2);
      return process(tINTERPOLATED_STRING_ESCAPE);
    }
  }

  \" {
    changeStringLevel();
    return process(tINTERPOLATED_STRING_END);
  }

  "$" / "{" {
    yybegin(COMMON_STATE);
    return process(tINTERPOLATED_STRING_INJECTION);
  }
  
  [\r\n] {
    yybegin(COMMON_STATE);
    return process(tWRONG_STRING);
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

  {INTERPOLATED_MULTI_LINE_STRING_PART} {
    return process(tINTERPOLATED_MULTILINE_STRING);
  }

  "$"{identifier} {
    if (yycharat(1) != '$') {
      haveIdInMultilineString = true;
      yybegin(INJ_COMMON_STATE);
      yypushback(yytext().length() - 1);
      return process(tINTERPOLATED_STRING_INJECTION);
    } else {
      yypushback(yytext().length() - 2);
      return process(tINTERPOLATED_STRING_ESCAPE);
    }
  }

  \"\"\" (\")+ {
    yypushback(yytext().length() - 1);
    return process(tINTERPOLATED_MULTILINE_STRING);
  }

  \"\"\" {
      changeStringLevel();
      return process(tINTERPOLATED_STRING_END);
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

{WRONG_STRING}                          {   return process(tWRONG_STRING);  }


{symbolLiteral}                          {  return process(tSYMBOL);  }

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
                                            
                                            yypushback(yytext().length() - 1);
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

"("{XML_BEGIN}                          {   yypushback(yytext().length() - 1);
                                            yybegin(YYINITIAL);
                                            return process(tLPARENTHESIS); }
")"                                     {   return process(tRPARENTHESIS); }
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// keywords /////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

"abstract"                              {   return process(kABSTRACT); }

"case" / ({LineTerminator}|{WhiteSpace})+("class" | "object")
                                        {   return process(kCASE); }

"case"                                  {   return process(kCASE); }
                                            
"catch"                                 {   return process(kCATCH); }
"class"                                 {   return process(kCLASS); }
"def"                                   {   return process(kDEF); }
"do"                                    {   return process(kDO); }
"else"                                  {   return process(kELSE); }
"extends"                               {   return process(kEXTENDS); }
"false"                                 {   return process(kFALSE); }
"final"                                 {   return process(kFINAL); }
"finally"                               {   return process(kFINALLY); }
"for"                                   {   return process(kFOR); }
"forSome"                               {   return process(kFOR_SOME); }
"if"                                    {   return process(kIF); }
"implicit"                              {   return process(kIMPLICIT); }
"import"                                {   return process(kIMPORT); }
"lazy"                                  {   return process(kLAZY); }
"match"                                 {   return process(kMATCH); }
"new"                                   {   return process(kNEW); }
"null"                                  {   return process(kNULL); }
"object"                                {   return process(kOBJECT); }
"override"                              {   return process(kOVERRIDE); }
"package"                               {   return process(kPACKAGE); }
"private"                               {   return process(kPRIVATE); }
"protected"                             {   return process(kPROTECTED); }
"return"                                {   return process(kRETURN); }
"sealed"                                {   return process(kSEALED); }
"super"                                 {   return process(kSUPER); }
"this"                                  {   return process(kTHIS); }
"throw"                                 {   return process(kTHROW); }
"trait"                                 {   return process(kTRAIT); }
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
"?"                                     {   return process(tIDENTIFIER);  }

"_"                                     {   return process(tUNDER);  }
":"                                     {   return process(tCOLON);  }
"="                                     {   return process(tASSIGN);  }

"=>"                                    {   return process(tFUNTYPE); }
"\\u21D2"                               {   return process(tFUNTYPE); }
"\u21D2"                                {   return process(tFUNTYPE); }

"<-"                                    {   return process(tCHOOSE); }
"\\u2190"                               {   return process(tCHOOSE); }
"\u2190"                                {   return process(tCHOOSE); }

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
{integerLiteral} / "." {identifier}
                                        {   return process(tINTEGER);  }
{floatingPointLiteral}                  {   return process(tFLOAT);      }
{integerLiteral}                        {   return process(tINTEGER);  }
{WhiteSpace}                            {   yybegin(YYINITIAL);
                                            return process(tWHITE_SPACE_IN_LINE);  }
{mNLS}                                  {   yybegin(YYINITIAL);
                                            return process(tWHITE_SPACE_IN_LINE); }

////////////////////// STUB ///////////////////////////////////////////////
.                                       {   return process(tSTUB); }

