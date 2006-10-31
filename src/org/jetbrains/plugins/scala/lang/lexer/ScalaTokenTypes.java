package org.jetbrains.plugins.scala.lang.lexer;

import com.intellij.psi.tree.IElementType;

/**
 * Author: Ilya Sergey
 * Date: 24.09.2006
 * Time: 12:39:38
 */
public interface ScalaTokenTypes {

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// White spaces in line /////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    IElementType tWHITE_SPACE_IN_LINE = new ScalaElementType("white space in line");

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// White spaces in line /////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    IElementType tLINE_TERMINATOR = new ScalaElementType("white space line terminate");

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Stub /////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    IElementType tSTUB = new ScalaElementType("stub");

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Comments /////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    IElementType tCOMMENT = new ScalaElementType("comment");

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Strings & chars //////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    IElementType tSTRING = new ScalaElementType("string content");
    IElementType tSTRING_BEGIN = new ScalaElementType("begin of string");
    IElementType tSTRING_END = new ScalaElementType("end of string");
                                                        
    IElementType tCHAR = new ScalaElementType("Character");

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

    IElementType tASSGN = new ScalaElementType("=");
    IElementType tPLUS = new ScalaElementType("+");
    IElementType tMINUS = new ScalaElementType("-");
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
    IElementType tLPARENTHIS = new ScalaElementType("(");
    IElementType tRPARENTHIS = new ScalaElementType(")");

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
    IElementType kIF = new ScalaElementType("if");
    IElementType kIMPLICIT = new ScalaElementType("implicit");
    IElementType kIMPORT = new ScalaElementType("import");
    IElementType kMATCH = new ScalaElementType("match");
    IElementType kNEW = new ScalaElementType("new");
    IElementType kNULL = new ScalaElementType("null");
    IElementType kOBJECT = new ScalaElementType("object");
    IElementType kOVERRIDE = new ScalaElementType("override");
    IElementType kPACKAGE = new ScalaElementType("package");
    IElementType kPRIVATE = new ScalaElementType("private");
    IElementType kPROTECTED = new ScalaElementType("protected");
    IElementType kREQUIRES = new ScalaElementType("requires");
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
    IElementType kWITH = new ScalaElementType("whith");
    IElementType kYIELD = new ScalaElementType("yield");
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////// variables and constants //////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    IElementType tIDENTIFIER = new ScalaElementType("identifier");

    ////////////////////////// xml tag /////////////////////////////////////////////////////////////////////////////////////
    IElementType tOPENXMLTAG = new ScalaElementType("opened xml tag");
    IElementType tCLOSEXMLTAG = new ScalaElementType("closed xml tag");
    IElementType tBEGINSCALAEXPR = new ScalaElementType("begin of scala expression");
    IElementType tENDSCALAEXPR = new ScalaElementType("end of scala expression");


    IElementType tDOT = new ScalaElementType(".");
    IElementType tCOMMA = new ScalaElementType(",");
    IElementType tSEMICOLON = new ScalaElementType(";");


    IElementType tUNDER = new ScalaElementType("_");
    IElementType tCOLON = new ScalaElementType(":");
    IElementType tASSIGN = new ScalaElementType("=");
    IElementType tAND = new ScalaElementType("&");
    IElementType tFUNTYPE = new ScalaElementType("=>");
    IElementType tFUNTYPE_ASCII = new ScalaElementType(Character.toString('\u21D2'));
    IElementType tCHOOSE = new ScalaElementType("<-");
    IElementType tLOWER_BOUND = new ScalaElementType(">:");
    IElementType tUPPER_BOUND = new ScalaElementType("<:");
    IElementType tVIEW = new ScalaElementType("<%");
    IElementType tINNER_CLASS = new ScalaElementType("#");
    IElementType tAT = new ScalaElementType("@");
    IElementType tQUESTION = new ScalaElementType("?");
    
}