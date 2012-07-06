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
import static org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes.*;

/**
 * @author ilyas
 *         Date: 24.09.2006
 */
public interface ScalaTokenTypes {

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Wrong token //////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  final IElementType tWRONG = new ScalaElementType("wrong token");

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// White spaces in line /////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  final IElementType tWHITE_SPACE_IN_LINE = new ScalaElementType("white space in line");

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Stub /////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  final IElementType tSTUB = new ScalaElementType("stub");

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Comments /////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  final IElementType tDOC_COMMENT = new ScalaElementType("DocComment");
  final IElementType tLINE_COMMENT = new ScalaElementType("comment");
  final IElementType tBLOCK_COMMENT = new ScalaElementType("BlockComment");
  final IElementType tSH_COMMENT = new ScalaElementType("ShellComment");

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Strings & chars //////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  final IElementType tSTRING = new ScalaElementType("string content");
  final IElementType tMULTILINE_STRING = new ScalaElementType("multiline string");
  final IElementType tINTERPOLATED_STRING = new ScalaElementType("interpolated string");
  final IElementType tINTERPOLATED_MULTILINE_STRING = new ScalaElementType("interpolatedmultiline string");
  final IElementType tINTERPOLATED_STRING_ID = new ScalaElementType("interpolated string id");
  final IElementType tINTERPOLATED_STRING_INJECTION = new ScalaElementType("interpolated string injection");
  final IElementType tINTERPOLATED_STRING_END = new ScalaElementType("interpolated string end");
  final IElementType tINTERPOLATED_STRING_ESCAPE = new ScalaElementType("interpolated string escape");
  final IElementType tWRONG_STRING = new ScalaElementType("wrong string content");

  final IElementType tCHAR = new ScalaElementType("Character");
  final IElementType tSYMBOL = new ScalaElementType("Symbol");

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  ///////////////////////// integer and float literals ///////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  final IElementType tINTEGER = new ScalaElementType("integer");
  final IElementType tFLOAT = new ScalaElementType("float");

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Operators ////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  final IElementType tEQUAL = new ScalaElementType("==");
  final IElementType tNOTEQUAL = new ScalaElementType("!=");
  final IElementType tLESS = new ScalaElementType("<");
  final IElementType tLESSOREQUAL = new ScalaElementType("<=");
  final IElementType tGREATER = new ScalaElementType(">");
  final IElementType tGREATEROREQUAL = new ScalaElementType(">=");

  final IElementType tTILDA = new ScalaElementType("~");
  final IElementType tNOT = new ScalaElementType("!");
  final IElementType tSTAR = new ScalaElementType("*");
  final IElementType tDIV = new ScalaElementType("/");

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Braces ///////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  final IElementType tLSQBRACKET = new ScalaElementType("[");
  final IElementType tRSQBRACKET = new ScalaElementType("]");
  final IElementType tLBRACE = new ScalaElementType("{");
  final IElementType tRBRACE = new ScalaElementType("}");
  final IElementType tLPARENTHESIS = new ScalaElementType("(");
  final IElementType tRPARENTHESIS = new ScalaElementType(")");

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  ///////////////////////// keywords /////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  final IElementType kABSTRACT = new ScalaElementType("abstract");
  final IElementType kCASE = new ScalaElementType("case");
  final IElementType kCATCH = new ScalaElementType("catch");
  final IElementType kCLASS = new ScalaElementType("class");
  final IElementType kDEF = new ScalaElementType("def");
  final IElementType kDO = new ScalaElementType("do");
  final IElementType kELSE = new ScalaElementType("else");
  final IElementType kEXTENDS = new ScalaElementType("extends");
  final IElementType kFALSE = new ScalaElementType("false");
  final IElementType kFINAL = new ScalaElementType("final");
  final IElementType kFINALLY = new ScalaElementType("finally");
  final IElementType kFOR = new ScalaElementType("for");
  final IElementType kFOR_SOME = new ScalaElementType("forSome");
  final IElementType kIF = new ScalaElementType("if");
  final IElementType kIMPLICIT = new ScalaElementType("implicit");
  final IElementType kIMPORT = new ScalaElementType("import");
  final IElementType kLAZY = new ScalaElementType("lazy");
  final IElementType kMATCH = new ScalaElementType("match");
  final IElementType kNEW = new ScalaElementType("new");
  final IElementType kNULL = new ScalaElementType("null");
  final IElementType kOBJECT = new ScalaElementType("object");
  final IElementType kOVERRIDE = new ScalaElementType("override");
  final IElementType kPACKAGE = new ScalaElementType("package");
  final IElementType kPRIVATE = new ScalaElementType("private");
  final IElementType kPROTECTED = new ScalaElementType("protected");
  final IElementType kREQUIRES = new ScalaElementType("requires");
  final IElementType kRETURN = new ScalaElementType("return");
  final IElementType kSEALED = new ScalaElementType("sealed");
  final IElementType kSUPER = new ScalaElementType("super");
  final IElementType kTHIS = new ScalaElementType("this");
  final IElementType kTHROW = new ScalaElementType("throw");
  final IElementType kTRAIT = new ScalaElementType("trait");
  final IElementType kTRY = new ScalaElementType("try");
  final IElementType kTRUE = new ScalaElementType("true");
  final IElementType kTYPE = new ScalaElementType("type");
  final IElementType kVAL = new ScalaElementType("val");
  final IElementType kVAR = new ScalaElementType("var");
  final IElementType kWHILE = new ScalaElementType("while");
  final IElementType kWITH = new ScalaElementType("with");
  final IElementType kYIELD = new ScalaElementType("yield");
  final IElementType kMACRO = new ScalaElementType("macro");
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  ///////////////////////// variables and constants //////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  final IElementType tIDENTIFIER = new ScalaElementType("identifier");

  ////////////////////////// xml tag /////////////////////////////////////////////////////////////////////////////////////
  final IElementType tOPENXMLTAG = new ScalaElementType("opened xml tag");
  final IElementType tCLOSEXMLTAG = new ScalaElementType("closed xml tag");
  final IElementType tBADCLOSEXMLTAG = new ScalaElementType("closing tag without opening");
  final IElementType tXMLTAGPART = new ScalaElementType("tag part");
  final IElementType tBEGINSCALAEXPR = new ScalaElementType("begin of scala expression");
  final IElementType tENDSCALAEXPR = new ScalaElementType("end of scala expression");
  final IElementType tXML_COMMENT_START = new ScalaElementType("Xml Comment Start");
  final IElementType tXML_COMMENT_END = new ScalaElementType("Xml Comment End");


  final IElementType tDOT = new ScalaElementType(".");
  final IElementType tCOMMA = new ScalaElementType(",");
  final IElementType tSEMICOLON = new ScalaElementType(";");


  final IElementType tUNDER = new ScalaElementType("_");
  final IElementType tCOLON = new ScalaElementType(":");
  final IElementType tASSIGN = new ScalaElementType("=");
  final IElementType tAND = new ScalaElementType("&");
  final IElementType tOR = new ScalaElementType("|");
  final IElementType tFUNTYPE = new ScalaElementType("=>");
  final IElementType tFUNTYPE_ASCII = new ScalaElementType(Character.toString('\u21D2'));
  final IElementType tCHOOSE = new ScalaElementType("<-");
  final IElementType tLOWER_BOUND = new ScalaElementType(">:");
  final IElementType tUPPER_BOUND = new ScalaElementType("<:");
  final IElementType tVIEW = new ScalaElementType("<%");
  final IElementType tINNER_CLASS = new ScalaElementType("#");
  final IElementType tAT = new ScalaElementType("@");
  final IElementType tQUESTION = new ScalaElementType("?");

  public static TokenSet WHITES_SPACES_FOR_FORMATTER_TOKEN_SET = TokenSet.create(
      tWHITE_SPACE_IN_LINE,
      ScalaDocTokenType.DOC_WHITESPACE,
      XML_REAL_WHITE_SPACE,
      XML_WHITE_SPACE,
      TAG_WHITE_SPACE
  );

  public static TokenSet WHITES_SPACES_TOKEN_SET = TokenSet.create(
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

  public static TokenSet KEYWORDS = TokenSet.create(
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

  TokenSet XML_ELEMENTS = TokenSet.create(XML_PI(), XML_ATTRIBUTE(), XML_CD_SECT(),
      XML_COMMENT(), XML_ELEMENT(), XML_EMPTY_TAG(), XML_END_TAG(), XML_EXPR(), XML_PATTERN(),
      XML_START_TAG(), ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_START, ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_END,
      XML_EQ, XML_ATTRIBUTE_VALUE_START_DELIMITER, XML_NAME, XML_TAG_END, XML_CDATA_END, XML_PI_END,
      XML_EMPTY_ELEMENT_END, XML_START_TAG_START, XML_END_TAG_START, XML_CDATA_START, XML_PI_START,
      XML_DATA_CHARACTERS, XML_COMMENT_CHARACTERS);
}