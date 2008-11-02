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
    private boolean newLineAllowed(){
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
          | \u005C | \u002F     //slashes

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

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////// String & chars //////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


ESCAPE_SEQUENCE=\\[^\r\n]
UNICODE_ESCAPE=!(!(\\u{hexDigit}{hexDigit}{hexDigit}{hexDigit}) | \\u000A)
SOME_ESCAPE=\\{octalDigit} {octalDigit}? {octalDigit}?
CHARACTER_LITERAL="'"([^\\\'\r\n]|{ESCAPE_SEQUENCE}|{UNICODE_ESCAPE}|{SOME_ESCAPE})("'"|\\) | \'\\u000A\'

STRING_BEGIN = \"([^\\\"\r\n]|{ESCAPE_SEQUENCE})*
STRING_LITERAL={STRING_BEGIN} \" |
               \"\"\" ( (\"(\")?)? [^\"] )* \"\"\"                                                 // Multi-line string

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


{STRING_LITERAL}                        {   yybegin(PROCESS_NEW_LINE);
                                            return process(tSTRING);
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
"\\u21D2"                               {   return process(tFUNTYPE); }
"\\u2190"                               {   return process(tCHOOSE); }
\u21D2                                  {   return process(tFUNTYPE); }
\u2190                                  {   return process(tCHOOSE); }
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

