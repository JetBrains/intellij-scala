package org.jetbrains.plugins.scala.lang.lexer.core;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import java.util.*;
import java.lang.reflect.Field;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypesEx;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;

%%

%class _ScalaCoreLexer
%implements FlexLexer, ScalaTokenTypesEx
%unicode
%public

%function advance
%type IElementType

%eof{ return;
%eof}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////// USER CODE //////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%{
    // Stack for braces
    private Stack <IElementType> braceStack = new Stack<IElementType>();

    /* Defines, is in this section new line is whitespace or not? */
    public boolean newLineAllowed(){
      if (braceStack.isEmpty()){
        return true;
      } else {
        return ScalaTokenTypes.tLBRACE.equals(braceStack.peek());
      }
    }

    /* Changes state depending on brace stack */
    private void changeState(){
      if (braceStack.isEmpty()) {
        yybegin(YYINITIAL);
      } else if ( tLPARENTHESIS.equals(braceStack.peek()) || tLSQBRACKET.equals(braceStack.peek()) ){
        yybegin(NEW_LINE_DEPRECATED);
      } else {
        yybegin(COMMON_STATE);
      }
    }

    /* removes brace from stack */
    private IElementType popBraceStack(IElementType elem){
     if (
          !braceStack.isEmpty() &&
          (
            (elem.equals(tRSQBRACKET) && tLSQBRACKET.equals(braceStack.peek())) ||
            (elem.equals(tRBRACE) && tLBRACE.equals(braceStack.peek())) ||
            (elem.equals(tRPARENTHESIS) && tLPARENTHESIS.equals(braceStack.peek()))
          )
        ) {
          braceStack.pop();
          return process(elem);
        } else if (elem.equals(tFUNTYPE)) {
          if (!braceStack.isEmpty() && kCASE.equals(braceStack.peek())) {
            braceStack.pop();
          }
          return process(elem);
        } else {
          return process(elem);
        }
    }

    private IElementType process(IElementType type){
        return type;
    }
%}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////      integers and floats     /////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

integerLiteral = ({decimalNumeral} | {hexNumeral} | {octalNumeral}) (L | l)?
decimalNumeral = 0 | {nonZeroDigit} {digit}*
hexNumeral = 0 x {hexDigit}+
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
special =   \u0021 | \u0023
          | [\u0025-\u0026]
          | [\u002A-\u002B]
          | \u002D | \u005E
          | \u003A
          | [\u003C-\u0040]
          | \u2192  // right arrow
          | \u007E
          | \u005C | \u002F | \u00AC
          | \u00B1 | \u00D7
          | \u00F7 | \u03F6
          | [\u0606-\u0608]
          | \u2044 | \u2052
          | [\u207A-\u207C]
          | [\u208A-\u208C]
          | [\u2140-\u2144]
          | \u214B
          | [\u2190-\u219B]
          | \u21A0 | \u21A3
          | \u21a6
          | \u21ae
          | [\u21ce-\u21cf]
          | \u21d2
          | \u21d4
          | [\u21f4-\u22ff]
          | [\u2308-\u230b]
          | [\u2320-\u2321]
          | \u237c
          | [\u239b-\u23b3]
          | [\u23dc-\u23e1]
          | \u25b7
          | \u25c1
          | [\u25f8-\u25ff]
          | \u266f
          | [\u27c0-\u27c4]
          | [\u27c7-\u27ca]
          | \u27cc
          | [\u27d0-\u27e5]
          | [\u27f0-\u27ff]
          | [\u2900-\u2982]
          | [\u2999-\u29d7]
          | [\u29dc-\u29fb]
          | [\u29fe-\u2aff]
          | [\u2b30-\u2b44]
          | [\u2b47-\u2b4c]
          | \ufb29
          | \ufe62
          | [\ufe64-\ufe66]
          | \uff0b
          | [\uff1c-\uff1e]
          | \uff5c
          | \uff5e
          | \uffe2
          | [\uffe9-\uffec]
          | \u1d6c1
          | \u1d6db
          | \u1d6fb
          | \u1d715
          | \u1d735
          | \u1d74f
          | \u1d76f
          | \u1d789
          | \u1d7a9
          | [\u00a6-\u00a7]
          | \u00a9
          | \u00ae
          | \u00b0
          | \u00b6
          | \u0482
          | [\u060e-\u060f]
          | \u06e9
          | [\u06fd-\u06fe]
          | \u07f6
          | \u09fa
          | \u0b70
          | [\u0bf3-\u0bf8]
          | \u0bfa
          | \u0c7f
          | [\u0cf1-\u0cf2]
          | \u0d79
          | [\u0f01-\u0f03]
          | [\u0f13-\u0f17]
          | [\u0f1a-\u0f1f]
          | \u0f34
          | \u0f36
          | \u0f38
          | [\u0fbe-\u0fc5]
          | [\u0fc7-\u0fcc]
          | [\u0fce-\u0fcf]
          | [\u109e-\u109f]
          | \u1360
          | [\u1390-\u1399]
          | \u1940
          | [\u19e0-\u19ff]
          | [\u1b61-\u1b6a]
          | [\u1b74-\u1b7c]
          | [\u2100-\u2101]
          | [\u2103-\u2106]
          | [\u2108-\u2109]
          | \u2114
          | [\u2116-\u2118]
          | [\u211e-\u2123]
          | \u2125
          | \u2127
          | \u2129
          | \u212e
          | [\u213a-\u213b]
          | \u214a
          | [\u214c-\u214d]
          | \u214f
          | [\u2195-\u2199]
          | [\u219c-\u219f]
          | [\u21a1-\u21a2]
          | [\u21a4-\u21a5]
          | [\u21a7-\u21ad]
          | [\u21af-\u21cd]
          | [\u21d0-\u21d1]
          | \u21d3
          | [\u21d5-\u21f3]
          | [\u2300-\u2307]
          | [\u230c-\u231f]
          | [\u2322-\u2328]
          | [\u232b-\u237b]
          | [\u237d-\u239a]
          | [\u23b4-\u23db]
          | [\u23e2-\u23e7]
          | [\u2400-\u2426]
          | [\u2440-\u244a]
          | [\u249c-\u24e9]
          | [\u2500-\u25b6]
          | [\u25b8-\u25c0]
          | [\u25c2-\u25f7]
          | [\u2600-\u266e]
          | [\u2670-\u269d]
          | [\u26a0-\u26bc]
          | [\u26c0-\u26c3]
          | [\u2701-\u2704]
          | [\u2706-\u2709]
          | [\u270c-\u2727]
          | [\u2729-\u274b]
          | \u274d
          | [\u274f-\u2752]
          | \u2756
          | [\u2758-\u275e]
          | [\u2761-\u2767]
          | "|"
          
          | \u2794
          | [\u2798-\u27af]
          | [\u27b1-\u27be]

          /* TODO jzaugg Why are these commented out? I tried to uncomment them, but got many parser test failures. 
          | [\u2800-\u28ff]
          | [\u2b00-\u2b2f]
          | [\u2b45-\u2b46]
          | [\u2b50-\u2b54]
          | [\u2ce5-\u2cea]
          | [\u2e80-\u2e99]
          | [\u2e9b-\u2ef3]
          | [\u2f00-\u2fd5]
          | [\u2ff0-\u2ffb]
          | \u3004
          | [\u3012-\u3013]
          | \u3020
          | [\u3036-\u3037]
          | [\u303e-\u303f]
          | [\u3190-\u3191]
          | [\u3196-\u319f]
          | [\u31c0-\u31e3]
          | [\u3200-\u321e]
          | [\u322a-\u3243]
          | \u3250
          | [\u3260-\u327f]
          | [\u328a-\u32b0]
          | [\u32c0-\u32fe]
          | [\u3300-\u33ff]
          | [\u4dc0-\u4dff]
          | [\ua490-\ua4c6]
          | [\ua828-\ua82b]
          | \ufdfd
          | \uffe4
          | \uffe8
          | [\uffed-\uffee]
          | [\ufffc-\ufffd]
          | \u10102
          | [\u10137-\u1013f]
          | [\u10179-\u10189]
          | [\u10190-\u1019b]
          | [\u101d0-\u101fc]
          | [\u1d000-\u1d0f5]
          | [\u1d100-\u1d126]
          | [\u1d129-\u1d164]
          | [\u1d16a-\u1d16c]
          | [\u1d183-\u1d184]
          | [\u1d18c-\u1d1a9]
          | [\u1d1ae-\u1d1dd]
          | [\u1d200-\u1d241]
          | \u1d245
          | [\u1d300-\u1d356]
          | [\u1f000-\u1f02b]
          */


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
CHARACTER_LITERAL="'"([^\\\'\r\n]|{ESCAPE_SEQUENCE}|{UNICODE_ESCAPE}|{SOME_ESCAPE})("'"|\\) | \'\\u000A\'

STRING_BEGIN = \"([^\\\"\r\n]|{ESCAPE_SEQUENCE})*
STRING_LITERAL={STRING_BEGIN} \"
MULTI_LINE_STRING = \"\"\" ( (\"(\")?)? [^\"] )* \"\"\" // Multi-line string

WRONG_STRING = {STRING_BEGIN}

charEscapeSeq = \\[^\r\n]
charNoDoubleQuote = !( ![^"\""] | {LineTerminator})
stringElement = {charNoDoubleQuote} | {charEscapeSeq}  
stringLiteral = {stringElement}*
charExtra = !( ![^"\""`] | {LineTerminator})             //This is for `type` identifiers
stringElementExtra = {charExtra} | {charEscapeSeq}
stringLiteralExtra = {stringElementExtra}*
symbolLiteral = "\'" {plainid}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////// NewLine processing ///////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

notFollowNewLine =   "catch" | "else" | "extends" | "forSome" | "finally" | "match" | "requires"
                    | "with" | "yield" | "," | "." | ";" | ":" | "_" | "=" | "=>" | "<-" | "<:" | "<%"
                    | ">:" | "#" | "["  | ")" | "]" |"}" | "\\u2190" | "\\u21D2"
specNotFollow    =  "_" | "catch" | "else" | "extends" | "finally" | "match" | "requires" | "with" | "yield" | "case"

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

%state NEW_LINE_DEPRECATED
%state NEW_LINE_ALLOWED
%state COMMON_STATE

// Valid preceding token for newline encountered
%xstate PROCESS_NEW_LINE
%xstate WAIT_FOR_XML

%%

<YYINITIAL>{
"]"                                     {   yybegin(PROCESS_NEW_LINE);
                                            return process(tRSQBRACKET); }

"}"                                     {   yybegin(PROCESS_NEW_LINE);
                                            return process(tRBRACE); }

")"                                     {   yybegin(PROCESS_NEW_LINE);
                                            return process(tRPARENTHESIS); }
"=>"                                    {   return process(tFUNTYPE);  }

}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////  XML processing ///////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

<WAIT_FOR_XML>{

{XML_BEGIN}                             {   changeState();
                                            yypushback(yytext().length());
                                            return SCALA_XML_CONTENT_START;
                                        }

[^]                                     {   changeState();
                                            yypushback(yytext().length());
                                        }
}


////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////////  New line processing state ///////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
<PROCESS_NEW_LINE>{

{WhiteSpace}{XML_BEGIN}                         {  yybegin(WAIT_FOR_XML);
                                                   yypushback(2);
                                                   return process(tWHITE_SPACE_IN_LINE);  }

{WhiteSpace}                                    {  return process(tWHITE_SPACE_IN_LINE);  }


{END_OF_LINE_COMMENT}                           {  return process(tLINE_COMMENT); }

{SH_COMMENT}                                    { return process(tSH_COMMENT); }




{mNLS} / "case" ({LineTerminator}|{WhiteSpace})+("class" | "object")
                                                {   changeState();
                                                    if(newLineAllowed()){
                                                      return process(tLINE_TERMINATOR);
                                                    } else {
                                                     return process(tWHITE_SPACE_IN_LINE);
                                                    }
                                                }

{mNLS} / {specNotFollow} ([:jletter:] | [:jletterdigit:])
                                                {   changeState();
                                                    if(newLineAllowed()){
                                                      return process(tLINE_TERMINATOR);
                                                    } else {
                                                     return process(tWHITE_SPACE_IN_LINE);
                                                    }
                                                }

{mNLS} "case"                                   {   yypushback(4);
                                                   changeState();
                                                   return process(tWHITE_SPACE_IN_LINE);
                                                }

{mNLS} / {floatingPointLiteral}                 {   changeState();
                                                    if(newLineAllowed()){
                                                      return process(tLINE_TERMINATOR);
                                                    } else {
                                                     return process(tWHITE_SPACE_IN_LINE);
                                                    }
                                                }



{mNLS} / {notFollowNewLine}
                                                {   changeState();
                                                    return process(tWHITE_SPACE_IN_LINE);
                                                }

{mNLS}{XML_BEGIN}                               {   yybegin(WAIT_FOR_XML);
                                                    yypushback(2);
                                                    if(newLineAllowed()){
                                                      return process(tLINE_TERMINATOR);
                                                    } else {
                                                      return process(tWHITE_SPACE_IN_LINE);
                                                    }
                                                }

{mNLS} .                                        {   yypushback(1);
                                                    changeState();
                                                    if(newLineAllowed()){
                                                      return process(tLINE_TERMINATOR);
                                                    } else {
                                                      return process(tWHITE_SPACE_IN_LINE);
                                                    }
                                                }

{mNLS}                                          {   changeState();
                                                    if(newLineAllowed()){
                                                      return process(tLINE_TERMINATOR);
                                                    } else {
                                                      return process(tWHITE_SPACE_IN_LINE);
                                                    }
                                                }

.                                               {   yypushback(yylength());
                                                    changeState();
                                                }
}

// END OF <PROCESS_LINE> STATE


<NEW_LINE_ALLOWED> {
{mNLS}                                  { yybegin(WAIT_FOR_XML); return process(tLINE_TERMINATOR); }

.                                       { yypushback(1);  yybegin(YYINITIAL); }
}



{END_OF_LINE_COMMENT}                   { return process(tLINE_COMMENT); }

{SH_COMMENT}                            { return process(tSH_COMMENT); }


{STRING_LITERAL}                        {   yybegin(PROCESS_NEW_LINE);
                                            return process(tSTRING);
                                        }

{MULTI_LINE_STRING }                    {   yybegin(PROCESS_NEW_LINE);
                                            return process(tMULTILINE_STRING);
                                        }

{WRONG_STRING}                          {   yybegin(PROCESS_NEW_LINE);
                                            return process(tWRONG_STRING);
                                        }


{symbolLiteral}                          {   yybegin(PROCESS_NEW_LINE);
                                            return process(tSYMBOL);  }

{CHARACTER_LITERAL}                      {   yybegin(PROCESS_NEW_LINE);
                                            return process(tCHAR);  }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// braces ///////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
"["                                     {   braceStack.push(tLSQBRACKET);
                                            yybegin(NEW_LINE_DEPRECATED);
                                            return process(tLSQBRACKET); }
"]"                                     {   yybegin(PROCESS_NEW_LINE);
                                            return popBraceStack(tRSQBRACKET); }

"{"                                     {   braceStack.push(tLBRACE);
                                            return process(tLBRACE); }
"{"{XML_BEGIN}                          {   braceStack.push(tLBRACE);
                                            yypushback(yytext().length() - 1);
                                            yybegin(WAIT_FOR_XML);
                                            return process(tLBRACE); }

"}"                                     {   yybegin(PROCESS_NEW_LINE);
                                            return popBraceStack(tRBRACE); }

"("                                     {   braceStack.push(tLPARENTHESIS);
                                            yybegin(NEW_LINE_DEPRECATED);
                                            return process(tLPARENTHESIS); }

"("{XML_BEGIN}                          {   braceStack.push(tLPARENTHESIS);
                                            yypushback(yytext().length() - 1);
                                            yybegin(WAIT_FOR_XML);
                                            return process(tLPARENTHESIS); }
")"                                     {   yybegin(PROCESS_NEW_LINE);
                                            return popBraceStack(tRPARENTHESIS); }
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// keywords /////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

"abstract"                              {   return process(kABSTRACT); }

"case" / ({LineTerminator}|{WhiteSpace})+("class" | "object")
                                        {   return process(kCASE); }

"case"                                  {   braceStack.push(kCASE);
                                            yybegin(NEW_LINE_DEPRECATED);
                                            return process(kCASE); }
                                            
"catch"                                 {   return process(kCATCH); }
"class"                                 {   return process(kCLASS); }
"def"                                   {   return process(kDEF); }
"do"                                    {   return process(kDO); }
"else"                                  {   return process(kELSE); }
"extends"                               {   return process(kEXTENDS); }
"false"                                 {   yybegin(PROCESS_NEW_LINE);
                                            return process(kFALSE); }
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
"null"                                  {   yybegin(PROCESS_NEW_LINE);
                                            return process(kNULL); }
"object"                                {   return process(kOBJECT); }
"override"                              {   return process(kOVERRIDE); }
"package"                               {   return process(kPACKAGE); }
"private"                               {   return process(kPRIVATE); }
"protected"                             {   return process(kPROTECTED); }
"requires"                              {   return process(kREQUIRES); }
"return"                                {   yybegin(PROCESS_NEW_LINE);
                                            return process(kRETURN); }
"sealed"                                {   return process(kSEALED); }
"super"                                 {   return process(kSUPER); }
"this"                                  {   yybegin(PROCESS_NEW_LINE);
                                            return process(kTHIS); }
"throw"                                 {   return process(kTHROW); }
"trait"                                 {   return process(kTRAIT); }
"try"                                   {   return process(kTRY); }
"true"                                  {   yybegin(PROCESS_NEW_LINE);
                                            return process(kTRUE); }
"type"                                  {   yybegin(PROCESS_NEW_LINE);
                                            return process(kTYPE); }
"val"                                   {   return process(kVAL); }
"var"                                   {   return process(kVAR); }
"while"                                 {   return process(kWHILE); }
"with"                                  {   return process(kWITH); }
"yield"                                 {   return process(kYIELD); }

///////////////////// Reserved shorthands //////////////////////////////////////////

"*"                                     {   yybegin(PROCESS_NEW_LINE);
                                            return process(tIDENTIFIER);  }
"?"                                     {   yybegin(PROCESS_NEW_LINE);
                                            return process(tIDENTIFIER);  }

"_"                                     {   yybegin(PROCESS_NEW_LINE);
                                            return process(tUNDER);  }
":"                                     {   return process(tCOLON);  }
"="                                     {   return process(tASSIGN);  }
"=>"                                    {   return popBraceStack(tFUNTYPE); }
//"\\u21D2"                               {   return process(tFUNTYPE); }
//"\\u2190"                               {   return process(tCHOOSE); }
//\u21D2                                  {   return process(tFUNTYPE); }
//\u2190                                  {   return process(tCHOOSE); }
"<-"                                    {   return process(tCHOOSE); }
"<:"                                    {   return process(tUPPER_BOUND); }
">:"                                    {   return process(tLOWER_BOUND); }
"<%"                                    {   return process(tVIEW); }
"#"                                     {   return process(tINNER_CLASS); }
"@"                                     {   return process(tAT);}

"&"                                     {   yybegin(PROCESS_NEW_LINE);
                                            return process(tIDENTIFIER);}
"|"                                     {   yybegin(PROCESS_NEW_LINE);
                                            return process(tIDENTIFIER);}
"+"                                     {   yybegin(PROCESS_NEW_LINE);
                                            return process(tIDENTIFIER); }
"-"                                     {   yybegin(PROCESS_NEW_LINE);
                                            return process(tIDENTIFIER);}
"~"                                     {   yybegin(PROCESS_NEW_LINE);
                                            return process(tIDENTIFIER);}
"!"                                     {   yybegin(PROCESS_NEW_LINE);
                                            return process(tIDENTIFIER);}

"."                                     {   return process(tDOT);}
";"                                     {   return process(tSEMICOLON);}
","                                     {   return process(tCOMMA);}


{identifier}                            {   yybegin(PROCESS_NEW_LINE);
                                            return process(tIDENTIFIER); }
{integerLiteral} / "." ({LineTerminator}|{WhiteSpace})* {identifier}
                                        {   yybegin(PROCESS_NEW_LINE);
                                            return process(tINTEGER);  }
{floatingPointLiteral}                  {   yybegin(PROCESS_NEW_LINE);
                                            return process(tFLOAT);      }
{integerLiteral}                        {   yybegin(PROCESS_NEW_LINE);
                                            return process(tINTEGER);  }
{WhiteSpace}                            {   yybegin(WAIT_FOR_XML);
                                            return process(tWHITE_SPACE_IN_LINE);  }
{mNLS}                                  {   yybegin(WAIT_FOR_XML);
                                            return process(tWHITE_SPACE_IN_LINE); }

////////////////////// STUB ///////////////////////////////////////////////
.                                       {   return process(tSTUB); }

