package org.jetbrains.plugins.scala.lang.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import java.util.*;
import java.lang.reflect.Field;
import org.jetbrains.annotations.NotNull;

%%

%class _ScalaLexer
%implements FlexLexer, ScalaTokenTypes
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
    private IElementType process(IElementType type){
        return type;
    }

%}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//////////////////  reserved words  ////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

KEYWORDS  =   "abstract" | "case"    | "catch"     | "class"      | "def"
            | "do"       | "else"    | "extends"   | "false"      | "final"
            | "finally"  | "for"     | "if"        | "implicit"   | "import"
            | "match"    | "new"     | "null"      | "object"     | "override"
            | "package"  | "private" | "protected" | "requires"   | "return"
            | "sealed"   | "super"   | "this"      | "throw"      | "trait"
            | "try"      | "true"    | "type"      | "val"        | "var"
            | " while"   | "with"    | "yield"

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

//identifier = [a-zA-Z_]+[a-zA-Z0-9]*
identifier = {plainid} | "'" {stringLiteral} "'"

charEscapeSeq = "\\" "u" {hexDigit} {hexDigit} {hexDigit} {hexDigit}


upper = [A-Z_]
lower = [a-z]
letter = {upper} | {lower}
digit = [0-9]

special = [^ ("0"| "1"| "2"| "3"| "4"| "5"| "6"| "7"| "8"| "9"| "'" | "\"" | "." | ";" | ",")]

op = {special}+
idrest = ({letter} | {digit})* ("_" op)?

varid = ({lower} {idrest})
plainid = ({upper} {idrest})
        | {varid}
        | {op}


idrest = ({letter} | {digit})* ("_" op)?

charNoDoubleQuote = [^"\""]
stringElement = {charNoDoubleQuote} | {charEscapeSeq}
stringLiteral = {stringElement}*



////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////// Common symbols //////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

LineTerminator = \r|\n|\f|\r\n
InLineTerminator = [ \t\f]
InputCharacter = [^\r\n\f]

WhiteSpaceInLine = {InLineTerminator}
WhiteSpaceLineTerminate = {LineTerminator}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////// Comments ////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

Comment = {TraditionalComment} | {EndOfLineComment} | {DocumentationComment}
TraditionalComment = "/*".*~"*/"
EndOfLineComment = "//" {InputCharacter}* {LineTerminator}
DocumentationComment = "/**" {CommentContent} "*"+ "/"
CommentContent = ( [^*] | \*+ [^/*] )*

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////  boolean values ///////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

booleanLiteral = "true" | "false"

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////  xml tag  /////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

openXmlBracket = "<"
closeXmlBracket = ">"

openXmlTag = {openXmlBracket} {stringLiteral} {closeXmlBracket}
closeXmlTag = {openXmlBracket} "\\" {stringLiteral} {closeXmlBracket}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////  states ///////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%state IN_BLOCK_COMMENT_STATE
// In block comment

%state IN_LINE_COMMENT_STATE
// In line comment

%state IN_STRING_STATE
// Inside the string... Boo!

%state IN_XML_STATE
//the scala expression between xml tags
%%
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//////// rules declarations ////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

<YYINITIAL>{
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////// comments ///////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

"/*"                                    {   yybegin(IN_BLOCK_COMMENT_STATE);
                                            return process(tCOMMENT);
                                        }
"//"                                    {   yybegin(IN_LINE_COMMENT_STATE);
                                            return process(tCOMMENT);
                                        }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////// Strings /////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

"\""                                    {   yybegin(IN_STRING_STATE);
                                            return process(tSTRING);
                                        }
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// braces ///////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
"["                                     {   return process(tLSQBRACKET); }
"]"                                     {   return process(tRSQBRACKET); }

"{"                                     {   return process(tLBRACE); }
"}"                                     {   return process(tRBRACE); }

"("                                     {   return process(tLPARENTHIS); }
")"                                     {   return process(tRPARENTHIS); }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// keywords /////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

"abstract"                              {   return process(kABSTRACT); }
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
"if"                                    {   return process(kIF); }
"implicit"                              {   return process(kIMPLICIT); }
"import"                                {   return process(kIMPORT); }
"match"                                 {   return process(kMATCH); }
"new"                                   {   return process(kNEW); }
"null"                                  {   return process(kNULL); }
"object"                                {   return process(kOBJECT); }
"override"                              {   return process(kOVERRIDE); }
"package"                               {   return process(kPACKAGE); }
"private"                               {   return process(kPRIVATE); }
"protected"                             {   return process(kPROTECTED); }
"requires"                              {   return process(kREQUIRES); }
"return"                                {   return process(kRETURN); }
"sealed"                                {   return process(kSEALED); }
"super"                                 {   return process(kSUPER); }
"this"                                  {   return process(kTHIS); }
"this"                                  {   return process(kTHIS); }
"throw"                                 {   return process(kTHROW); }
"trait"                                 {   return process(kTRAIT); }
"try"                                   {   return process(kTRY); }
"true"                                  {   return process(kTRUE); }
"type"                                  {   return process(kTYPE); }
"val"                                   {   return process(kVAL); }
"var"                                   {   return process(kVAR); }
"while"                                 {   return process(kWHILE); }
"with"                                  {   return process(kWHITH); }
"yield"                                 {   return process(kYIELD); }

///////////////////// Reserved shorthands //////////////////////////////////////////
"_"                                     {   return process(kUNDER);  }
":"                                     {   return process(kCOLON);  }
"="                                     {   return process(kASSIGN);  }
"=>"                                    {   return process(kFUNTYPE); }
"'\u21D2'"                              {   return process(kFUNTYPE_ASCII); }
"<-"                                    {   return process(kCHOOSE); }
"<:"                                    {   return process(kLOWER_BOUND); }
">:"                                    {   return process(kUPPER_BOUND); }
"#"                                     {   return process(kINNER_CLASS); }
"@"                                     {   return process(kAT);}


////////////////////// Identifier /////////////////////////////////////////

{identifier}                            {   return process(tIDENTIFIER); }
{integerLiteral}                        {   return process(tINTEGER);  }
{floatingPointLiteral}                  {   return process(tFLOAT);      }

///////////////////// Operators //////////////////////////////////////////


////////////////////// XML /////////////////////////////////////////

//{openXmlTag}                                {   yybegin(IN_XML_STATE);
//                                            return process(tOPENXMLTAG); }

////////////////////// white spaces in line ///////////////////////////////////////////////
{WhiteSpaceInLine}                      {   return tWHITE_SPACE_IN_LINE;  }

////////////////////// STUB ///////////////////////////////////////////////
.|{LineTerminator}                      {   return tSTUB; }

}



////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// In block comment /////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
<IN_BLOCK_COMMENT_STATE>{

"*/"                                    {   yybegin(YYINITIAL);
                                            return process(tCOMMENT);
                                        }

.|{LineTerminator}                      {   return process(tCOMMENT); }

}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// In line comment //////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
<IN_LINE_COMMENT_STATE>{

{LineTerminator}                        {   yybegin(YYINITIAL);
                                            return process(tCOMMENT);
                                        }

.                                       {   return process(tCOMMENT); }

}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Inside a string  /////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
<IN_STRING_STATE>{

"\""                                    {   yybegin(YYINITIAL);
                                            return process(tSTRING);
                                        }

.|{LineTerminator}                      {   return process(tSTRING); }

}

//todo: it is nesseccary organize stack of statements to control opened and corresponding closed tags
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Inside a xml  /////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
<IN_XML_STATE>{

"{"                                     {   yybegin(YYINITIAL);
                                            return process(tBEGINSCALAEXPR);
                                        }

"}"                                     {   yybegin(IN_XML_STATE);
                                            return process(tENDSCALAEXPR);
                                        }

{openXmlTag}                            {   yybegin(IN_XML_STATE);
                                            return process(tOPENXMLTAG);
                                        }

{closeXmlTag}                           {   yybegin(YYINITIAL);
                                            return process(tCLOSEXMLTAG);
                                        }

.|{LineTerminator}                                       {   return process(tSTRING); }

}