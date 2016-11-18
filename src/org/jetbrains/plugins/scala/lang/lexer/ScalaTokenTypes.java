/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

/**
 * @author ilyas
 *         Date: 24.09.2006
 */
public interface ScalaTokenTypes {

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Wrong token //////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  IElementType tWRONG = new ScalaElementType("wrong token");

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// White spaces in line /////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  IElementType tWHITE_SPACE_IN_LINE = new ScalaElementType("white space in line");

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Stub /////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  IElementType tSTUB = new ScalaElementType("stub");

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Comments /////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  IElementType tDOC_COMMENT = new ScalaElementType("DocComment");
  IElementType tLINE_COMMENT = new ScalaElementType("comment");
  IElementType tBLOCK_COMMENT = new ScalaElementType("BlockComment");
  IElementType tSH_COMMENT = new ScalaElementType("ShellComment");

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Strings & chars //////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  IElementType tSTRING = new ScalaElementType("string content");
  IElementType tMULTILINE_STRING = new ScalaElementType("multiline string");
  IElementType tINTERPOLATED_STRING = new ScalaElementType("interpolated string");
  IElementType tINTERPOLATED_MULTILINE_STRING = new ScalaElementType("interpolatedmultiline string");
  IElementType tINTERPOLATED_STRING_ID = new ScalaElementType("interpolated string id");
  IElementType tINTERPOLATED_STRING_INJECTION = new ScalaElementType("interpolated string injection");
  IElementType tINTERPOLATED_STRING_END = new ScalaElementType("interpolated string end");
  IElementType tINTERPOLATED_STRING_ESCAPE = new ScalaElementType("interpolated string escape");
  IElementType tWRONG_STRING = new ScalaElementType("wrong string content");

  IElementType tCHAR = new ScalaElementType("Character");
  IElementType tSYMBOL = new ScalaElementType("Symbol");

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  ///////////////////////// integer and float literals ///////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  IElementType tINTEGER = new ScalaElementType("integer");
  IElementType tFLOAT = new ScalaElementType("float");

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Operators ////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  IElementType tEQUAL = new ScalaElementType("==");
  IElementType tNOTEQUAL = new ScalaElementType("!=");
  IElementType tLESS = new ScalaElementType("<");
  IElementType tLESSOREQUAL = new ScalaElementType("<=");
  IElementType tGREATER = new ScalaElementType(">");
  IElementType tGREATEROREQUAL = new ScalaElementType(">=");

  IElementType tTILDA = new ScalaElementType("~");
  IElementType tNOT = new ScalaElementType("!");
  IElementType tSTAR = new ScalaElementType("*");
  IElementType tDIV = new ScalaElementType("/");

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Braces ///////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  IElementType tLSQBRACKET = new ScalaElementType("[");
  IElementType tRSQBRACKET = new ScalaElementType("]");
  IElementType tLBRACE = new ScalaElementType("{");
  IElementType tRBRACE = new ScalaElementType("}");
  IElementType tLPARENTHESIS = new ScalaElementType("(");
  IElementType tRPARENTHESIS = new ScalaElementType(")");

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  ///////////////////////// keywords /////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  IElementType kABSTRACT = new ScalaElementType("abstract");
  IElementType kCASE = new ScalaElementType("case");
  IElementType kCATCH = new ScalaElementType("catch");
  IElementType kCLASS = new ScalaElementType("class");
  IElementType kDEF = new ScalaElementType("def");
  IElementType kDO = new ScalaElementType("do");
  IElementType kELSE = new ScalaElementType("else");
  IElementType kEXTENDS = new ScalaElementType("extends");
  IElementType kFALSE = new ScalaElementType("false");
  IElementType kFINAL = new ScalaElementType("final");
  IElementType kFINALLY = new ScalaElementType("finally");
  IElementType kFOR = new ScalaElementType("for");
  IElementType kFOR_SOME = new ScalaElementType("forSome");
  IElementType kIF = new ScalaElementType("if");
  IElementType kIMPLICIT = new ScalaElementType("implicit");
  IElementType kIMPORT = new ScalaElementType("import");
  IElementType kLAZY = new ScalaElementType("lazy");
  IElementType kMATCH = new ScalaElementType("match");
  IElementType kNEW = new ScalaElementType("new");
  IElementType kNULL = new ScalaElementType("null");
  IElementType kOBJECT = new ScalaElementType("object");
  IElementType kOVERRIDE = new ScalaElementType("override");
  IElementType kPACKAGE = new ScalaElementType("package");
  IElementType kPRIVATE = new ScalaElementType("private");
  IElementType kPROTECTED = new ScalaElementType("protected");
  IElementType kRETURN = new ScalaElementType("return");
  IElementType kSEALED = new ScalaElementType("sealed");
  IElementType kSUPER = new ScalaElementType("super");
  IElementType kTHIS = new ScalaElementType("this");
  IElementType kTHROW = new ScalaElementType("throw");
  IElementType kTRAIT = new ScalaElementType("trait");
  IElementType kTRY = new ScalaElementType("try");
  IElementType kTRUE = new ScalaElementType("true");
  IElementType kTYPE = new ScalaElementType("type");
  IElementType kVAL = new ScalaElementType("val");
  IElementType kVAR = new ScalaElementType("var");
  IElementType kWHILE = new ScalaElementType("while");
  IElementType kWITH = new ScalaElementType("with");
  IElementType kYIELD = new ScalaElementType("yield");
  IElementType kMACRO = new ScalaElementType("macro");
  IElementType kINLINE = new ScalaElementType("inline");
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  ///////////////////////// variables and constants //////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  IElementType tIDENTIFIER = new ScalaElementType("identifier");

  ////////////////////////// xml tag /////////////////////////////////////////////////////////////////////////////////////
  IElementType tOPENXMLTAG = new ScalaElementType("opened xml tag");
  IElementType tCLOSEXMLTAG = new ScalaElementType("closed xml tag");
  IElementType tBADCLOSEXMLTAG = new ScalaElementType("closing tag without opening");
  IElementType tXMLTAGPART = new ScalaElementType("tag part");
  IElementType tBEGINSCALAEXPR = new ScalaElementType("begin of scala expression");
  IElementType tENDSCALAEXPR = new ScalaElementType("end of scala expression");
  IElementType tXML_COMMENT_START = new ScalaElementType("Xml Comment Start");
  IElementType tXML_COMMENT_END = new ScalaElementType("Xml Comment End");


  IElementType tDOT = new ScalaElementType(".");
  IElementType tCOMMA = new ScalaElementType(",");
  IElementType tSEMICOLON = new ScalaElementType(";");


  IElementType tUNDER = new ScalaElementType("_");
  IElementType tCOLON = new ScalaElementType(":");
  IElementType tASSIGN = new ScalaElementType("=");
  IElementType tAND = new ScalaElementType("&");
  IElementType tOR = new ScalaElementType("|");
  IElementType tFUNTYPE = new ScalaElementType("=>");
  IElementType tFUNTYPE_ASCII = new ScalaElementType(Character.toString('\u21D2'));
  IElementType tCHOOSE = new ScalaElementType("<-");
  IElementType tLOWER_BOUND = new ScalaElementType(">:");
  IElementType tUPPER_BOUND = new ScalaElementType("<:");
  IElementType tVIEW = new ScalaElementType("<%");
  IElementType tINNER_CLASS = new ScalaElementType("#");
  IElementType tAT = new ScalaElementType("@");
  IElementType tQUESTION = new ScalaElementType("?");

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
          kEXTENDS,
          kFALSE,
          kFINAL,
          kFINALLY,
          kFOR,
          kFOR_SOME,
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
          kTHIS,
          kTHROW,
          kTRAIT,
          kTRY,
          kTRUE,
          kTYPE,
          kVAL,
          kVAR,
          kWHILE,
          kWITH,
          kYIELD,
          kMACRO
  );

  TokenSet IDENTIFIER_TOKEN_SET = TokenSet.create(tIDENTIFIER);
  TokenSet STRING_LITERAL_TOKEN_SET = TokenSet.create(tSTRING, tWRONG_STRING, tMULTILINE_STRING, tINTERPOLATED_MULTILINE_STRING, tINTERPOLATED_STRING);
  TokenSet BRACES_TOKEN_SET = TokenSet.create(tLSQBRACKET, tRSQBRACKET, tLBRACE, tRBRACE, tLPARENTHESIS, tRPARENTHESIS);
}