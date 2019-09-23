/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.lang.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType;
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes;

import static com.intellij.psi.xml.XmlTokenType.*;
import static org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType.*;

/**
 * @author ilyas
 * Date: 24.09.2006
 */
public interface ScalaTokenTypes {

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// White spaces in line /////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    IElementType tWHITE_SPACE_IN_LINE = new ScalaTokenType("white space in line");

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Stub /////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    IElementType tSTUB = new ScalaTokenType("stub");

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Comments /////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    IElementType tDOC_COMMENT = new ScalaTokenType("DocComment");
    IElementType tLINE_COMMENT = new ScalaTokenType("comment");
    IElementType tBLOCK_COMMENT = new ScalaTokenType("BlockComment");
    IElementType tSH_COMMENT = new ScalaTokenType("ShellComment");

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Strings & chars //////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    IElementType tSTRING = new ScalaTokenType("string content");
    IElementType tMULTILINE_STRING = new ScalaTokenType("multiline string");
    IElementType tINTERPOLATED_STRING = new ScalaTokenType("interpolated string");
    IElementType tINTERPOLATED_MULTILINE_STRING = new ScalaTokenType("interpolatedmultiline string");
    IElementType tINTERPOLATED_STRING_ID = new ScalaTokenType("interpolated string id");
    IElementType tINTERPOLATED_STRING_INJECTION = new ScalaTokenType("interpolated string injection");
    IElementType tINTERPOLATED_STRING_END = new ScalaTokenType("interpolated string end");
    IElementType tINTERPOLATED_STRING_ESCAPE = new ScalaTokenType("interpolated string escape");
    IElementType tWRONG_STRING = new ScalaTokenType("wrong string content");

    IElementType tCHAR = new ScalaTokenType("Character");
    IElementType tSYMBOL = new ScalaTokenType("Symbol");

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Operators ////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    IElementType tEQUAL = new ScalaTokenType("==");
    IElementType tNOTEQUAL = new ScalaTokenType("!=");
    IElementType tLESS = new ScalaTokenType("<");
    IElementType tLESSOREQUAL = new ScalaTokenType("<=");
    IElementType tGREATER = new ScalaTokenType(">");
    IElementType tGREATEROREQUAL = new ScalaTokenType(">=");

    IElementType tTILDA = new ScalaTokenType("~");
    IElementType tNOT = new ScalaTokenType("!");
    IElementType tSTAR = new ScalaTokenType("*");
    IElementType tDIV = new ScalaTokenType("/");

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Braces ///////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    IElementType tLSQBRACKET = new ScalaTokenType("[");
    IElementType tRSQBRACKET = new ScalaTokenType("]");
    IElementType tLBRACE = new ScalaTokenType("{");
    IElementType tRBRACE = new ScalaTokenType("}");
    IElementType tLPARENTHESIS = new ScalaTokenType("(");
    IElementType tRPARENTHESIS = new ScalaTokenType(")");

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////// keywords /////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ScalaModifierTokenType kABSTRACT = ScalaModifierTokenType.of(ScalaModifier.Abstract);
    ScalaModifierTokenType kCASE = ScalaModifierTokenType.of(ScalaModifier.Case);
    ScalaModifierTokenType kIMPLICIT = ScalaModifierTokenType.of(ScalaModifier.Implicit);
    ScalaModifierTokenType kFINAL = ScalaModifierTokenType.of(ScalaModifier.Final);
    ScalaModifierTokenType kLAZY = ScalaModifierTokenType.of(ScalaModifier.Lazy);
    ScalaModifierTokenType kOVERRIDE = ScalaModifierTokenType.of(ScalaModifier.Override);
    ScalaModifierTokenType kPRIVATE = ScalaModifierTokenType.of(ScalaModifier.Private);
    ScalaModifierTokenType kPROTECTED = ScalaModifierTokenType.of(ScalaModifier.Protected);
    ScalaModifierTokenType kSEALED = ScalaModifierTokenType.of(ScalaModifier.Sealed);
    ScalaModifierTokenType kINLINE = ScalaModifierTokenType.of(ScalaModifier.Inline);


    IElementType kCATCH = new ScalaTokenType("catch");
    IElementType kCLASS = new ScalaTokenType("class");
    IElementType kDEF = new ScalaTokenType("def");
    IElementType kDO = new ScalaTokenType("do");
    IElementType kELSE = new ScalaTokenType("else");
    IElementType kEXTENDS = new ScalaTokenType("extends");
    IElementType kFALSE = new ScalaTokenType("false");
    IElementType kFINALLY = new ScalaTokenType("finally");
    IElementType kFOR = new ScalaTokenType("for");
    IElementType kFOR_SOME = new ScalaTokenType("forSome");
    IElementType kIF = new ScalaTokenType("if");
    IElementType kIMPORT = new ScalaTokenType("import");
    IElementType kMATCH = new ScalaTokenType("match");
    IElementType kNEW = new ScalaTokenType("new");
    IElementType kNULL = new ScalaTokenType("null");
    IElementType kOBJECT = new ScalaTokenType("object");
    IElementType kPACKAGE = new ScalaTokenType("package");

    IElementType kRETURN = new ScalaTokenType("return");
    IElementType kSUPER = new ScalaTokenType("super");
    IElementType kTHIS = new ScalaTokenType("this");
    IElementType kTHROW = new ScalaTokenType("throw");
    IElementType kTRAIT = new ScalaTokenType("trait");
    IElementType kTRY = new ScalaTokenType("try");
    IElementType kTRUE = new ScalaTokenType("true");
    IElementType kTYPE = new ScalaTokenType("type");
    IElementType kVAL = new ScalaTokenType("val");
    IElementType kVAR = new ScalaTokenType("var");
    IElementType kWHILE = new ScalaTokenType("while");
    IElementType kWITH = new ScalaTokenType("with");
    IElementType kYIELD = new ScalaTokenType("yield");
    IElementType kMACRO = new ScalaTokenType("macro");
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////// variables and constants //////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    IElementType tIDENTIFIER = new ScalaTokenType("identifier");

    ////////////////////////// xml tag /////////////////////////////////////////////////////////////////////////////////////
    IElementType tOPENXMLTAG = new ScalaTokenType("opened xml tag");
    IElementType tCLOSEXMLTAG = new ScalaTokenType("closed xml tag");
    IElementType tBADCLOSEXMLTAG = new ScalaTokenType("closing tag without opening");
    IElementType tXMLTAGPART = new ScalaTokenType("tag part");
    IElementType tBEGINSCALAEXPR = new ScalaTokenType("begin of scala expression");
    IElementType tENDSCALAEXPR = new ScalaTokenType("end of scala expression");
    IElementType tXML_COMMENT_START = new ScalaTokenType("Xml Comment Start");
    IElementType tXML_COMMENT_END = new ScalaTokenType("Xml Comment End");


    IElementType tDOT = new ScalaTokenType(".");
    IElementType tCOMMA = new ScalaTokenType(",");
    IElementType tSEMICOLON = new ScalaTokenType(";");


    IElementType tUNDER = new ScalaTokenType("_");
    IElementType tCOLON = new ScalaTokenType(":");
    IElementType tASSIGN = new ScalaTokenType("=");
    IElementType tAND = new ScalaTokenType("&");
    IElementType tOR = new ScalaTokenType("|");
    IElementType tFUNTYPE = new ScalaTokenType("=>");
    IElementType tFUNTYPE_ASCII = new ScalaTokenType(Character.toString('\u21D2'));
    IElementType tCHOOSE = new ScalaTokenType("<-");
    IElementType tLOWER_BOUND = new ScalaTokenType(">:");
    IElementType tUPPER_BOUND = new ScalaTokenType("<:");
    IElementType tVIEW = new ScalaTokenType("<%");
    IElementType tINNER_CLASS = new ScalaTokenType("#");
    IElementType tAT = new ScalaTokenType("@");
    IElementType tQUESTION = new ScalaTokenType("?");

    TokenSet WHITES_SPACES_FOR_FORMATTER_TOKEN_SET = TokenSet.create(
            tWHITE_SPACE_IN_LINE,
            ScalaDocTokenType.DOC_WHITESPACE,
            XML_REAL_WHITE_SPACE,
            XML_WHITE_SPACE,
            TAG_WHITE_SPACE
    );

    TokenSet WHITES_SPACES_TOKEN_SET = TokenSet.create(
            tWHITE_SPACE_IN_LINE,
            XML_REAL_WHITE_SPACE,
            XML_WHITE_SPACE,
            TAG_WHITE_SPACE
    );

    TokenSet COMMENTS_TOKEN_SET = TokenSet.create(
            tLINE_COMMENT,
            tBLOCK_COMMENT,
            tSH_COMMENT,
            tDOC_COMMENT,
            ScalaDocElementTypes.SCALA_DOC_COMMENT
    );

    TokenSet WHITES_SPACES_AND_COMMENTS_TOKEN_SET = TokenSet.orSet(COMMENTS_TOKEN_SET, WHITES_SPACES_TOKEN_SET);

    TokenSet KEYWORDS = TokenSet.create(
            kABSTRACT,
            kCASE,
            kCATCH,
            kCLASS,
            kDEF,
            kDO,
            kELSE,
            Enum(),
            Export(),
            kEXTENDS,
            kFALSE,
            kFINAL,
            kFINALLY,
            kFOR,
            kFOR_SOME, // scala 2 only
            Given(),
            kIF,
            kIMPLICIT,
            kIMPORT,
            kLAZY,
            kMATCH,
            kNEW,
            kNULL,
            kOBJECT,
            kOVERRIDE,
            kPACKAGE,
            kPRIVATE,
            kPROTECTED,
            kRETURN,
            kSEALED,
            kSUPER,
            Then(),
            kTHIS, // scala 2 only
            kTHROW,
            kTRAIT,
            kTRUE,
            kTRY,
            kTYPE,
            kVAL,
            kVAR,
            kWHILE,
            kWITH,
            kYIELD,
            kMACRO // scala 2 only
    );

    TokenSet IDENTIFIER_TOKEN_SET = TokenSet.create(tIDENTIFIER);

    TokenSet STRING_LITERAL_TOKEN_SET = TokenSet.create(
            tSTRING,
            tWRONG_STRING,
            tMULTILINE_STRING,
            tINTERPOLATED_STRING,
            tINTERPOLATED_MULTILINE_STRING
    );

    TokenSet VAL_VAR_TOKEN_SET = TokenSet.create(kVAL, kVAR);

    TokenSet NUMBER_TOKEN_SET = TokenSet.create(
            Long(),
            Integer(),
            Double(),
            Float()
    );

    TokenSet BOOLEAN_TOKEN_SET = TokenSet.create(kTRUE, kFALSE);

    TokenSet LITERALS = TokenSet.orSet(
            STRING_LITERAL_TOKEN_SET,
            NUMBER_TOKEN_SET,
            BOOLEAN_TOKEN_SET
    );

    TokenSet BRACES_TOKEN_SET = TokenSet.create(tLBRACE, tRBRACE);
    TokenSet PARENTHESIS_TOKEN_SET = TokenSet.create(tLPARENTHESIS, tRPARENTHESIS);
    TokenSet BRACKETS_TOKEN_SET = TokenSet.create(tLSQBRACKET, tRSQBRACKET);

    TokenSet LEFT_BRACE_OR_PAREN_TOKEN_SET = TokenSet.create(tLBRACE, tLPARENTHESIS);
    TokenSet RIGHT_BRACE_OR_PAREN_TOKEN_SET = TokenSet.create(tRBRACE, tRPARENTHESIS);

    TokenSet ANY_BRACKETS_TOKEN_SET = TokenSet.orSet(
            BRACES_TOKEN_SET,
            PARENTHESIS_TOKEN_SET,
            BRACKETS_TOKEN_SET
    );

    TokenSet LBRACE_LPARENT_TOKEN_SET = TokenSet.create(tLBRACE, tLPARENTHESIS);
}