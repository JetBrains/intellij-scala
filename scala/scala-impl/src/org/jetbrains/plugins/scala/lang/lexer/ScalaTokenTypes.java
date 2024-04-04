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
import org.jetbrains.plugins.scalaDirective.lang.parser.ScalaDirectiveElementTypes;
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType;
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes;

import static com.intellij.psi.xml.XmlTokenType.*;
import static org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType.Double;
import static org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType.Float;
import static org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType.Integer;
import static org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType.Long;
import static org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType.*;

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

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Strings & chars //////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    IElementType tSTRING = new ScalaTokenType("string content");
    IElementType tMULTILINE_STRING = new ScalaTokenType("multiline string");
    IElementType tINTERPOLATED_STRING = new ScalaTokenType("interpolated string");
    // TODO: add a space between `interpolated` and `multiline` in debug name
    IElementType tINTERPOLATED_MULTILINE_STRING = new ScalaTokenType("interpolated multiline string");
    IElementType tINTERPOLATED_STRING_ID = new ScalaTokenType("interpolated string id");
    IElementType tINTERPOLATED_STRING_INJECTION = new ScalaTokenType("interpolated string injection");
    IElementType tINTERPOLATED_STRING_END = new ScalaTokenType("interpolated string end");
    // TODO: rename it, it represents "$$" (dollar escape) "interpolated string escape" is misleading
    IElementType tINTERPOLATED_STRING_ESCAPE = new ScalaTokenType("interpolated string escape");
    IElementType tWRONG_STRING = new ScalaTokenType("wrong string content");
    IElementType tWRONG_LINE_BREAK_IN_STRING = new ScalaTokenType("wrong line break in string");

    // These 2 -RAW tokens are only required in highlighting lexer for now.
    // (see org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighter.CompoundLexer)
    // They are remapped to non-RAW versions in parser.
    // We could preserve them in PSI tree, but until it's required somewhere I decided no to do so.
    // This will help avoiding a lot of modifications in other subsystems.
    IElementType tINTERPOLATED_MULTILINE_RAW_STRING = new ScalaTokenType("interpolated multiline raw string");
    IElementType tINTERPOLATED_RAW_STRING = new ScalaTokenType("interpolated raw string");

    IElementType tCHAR = new ScalaTokenType("Character");
    IElementType tSYMBOL = new ScalaTokenType("Symbol");


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

    IElementType kCATCH    = new ScalaKeywordTokenType("catch");
    IElementType kDEF      = new ScalaKeywordTokenType("def");
    IElementType kDO       = new ScalaKeywordTokenType("do");
    IElementType kELSE     = new ScalaKeywordTokenType("else");
    IElementType kEXTENDS  = new ScalaKeywordTokenType("extends");
    IElementType kFALSE    = new ScalaKeywordTokenType("false");
    IElementType kFINALLY  = new ScalaKeywordTokenType("finally");
    IElementType kFOR      = new ScalaKeywordTokenType("for");
    IElementType kFOR_SOME = new ScalaKeywordTokenType("forSome");
    IElementType kIF       = new ScalaKeywordTokenType("if");
    IElementType kIMPORT   = new ScalaKeywordTokenType("import");
    IElementType kMATCH    = new ScalaKeywordTokenType("match");
    IElementType kNULL     = new ScalaKeywordTokenType("null");
    IElementType kPACKAGE  = new ScalaKeywordTokenType("package");

    IElementType kRETURN = new ScalaKeywordTokenType("return");
    IElementType kSUPER  = new ScalaKeywordTokenType("super");
    IElementType kTHIS   = new ScalaKeywordTokenType("this");
    IElementType kTHROW  = new ScalaKeywordTokenType("throw");
    IElementType kTRY    = new ScalaKeywordTokenType("try");
    IElementType kTRUE   = new ScalaKeywordTokenType("true");
    IElementType kTYPE   = new ScalaKeywordTokenType("type");
    IElementType kVAL    = new ScalaKeywordTokenType("val");
    IElementType kVAR    = new ScalaKeywordTokenType("var");
    IElementType kWHILE  = new ScalaKeywordTokenType("while");
    IElementType kWITH   = new ScalaKeywordTokenType("with");
    IElementType kYIELD  = new ScalaKeywordTokenType("yield");
    IElementType kMACRO  = new ScalaKeywordTokenType("macro");
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////// variables and constants //////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    IElementType tIDENTIFIER = new ScalaTokenType("identifier");

    ////////////////////////// xml tag /////////////////////////////////////////////////////////////////////////////////////
    IElementType tOPENXMLTAG = new ScalaTokenType("opened xml tag");
    IElementType tCLOSEXMLTAG = new ScalaTokenType("closed xml tag");
    IElementType tBADCLOSEXMLTAG = new ScalaTokenType("closing tag without opening");
    IElementType tXMLTAGPART = new ScalaTokenType("tag part");
    IElementType tXML_COMMENT_START = new ScalaTokenType("Xml Comment Start");
    IElementType tXML_COMMENT_END = new ScalaTokenType("Xml Comment End");


    IElementType tDOT = new ScalaTokenType(".");
    IElementType tCOMMA = new ScalaTokenType(",");
    IElementType tSEMICOLON = new ScalaTokenType(";");


    IElementType tUNDER = new ScalaTokenType("_");
    IElementType tCOLON = new ScalaTokenType(":");
    IElementType tASSIGN = new ScalaTokenType("=");
    IElementType tFUNTYPE = new ScalaTokenType("=>");
    // TODO: remove tFUNTYPE_ASCII from everywhere, it's not actually used in lexer or parser
    //  it's some aintcient outdated code from 2008
    IElementType tFUNTYPE_ASCII = new ScalaTokenType(Character.toString('\u21D2'));
    IElementType tCHOOSE = new ScalaTokenType("<-");
    IElementType tLOWER_BOUND = new ScalaTokenType(">:");
    IElementType tUPPER_BOUND = new ScalaTokenType("<:");
    IElementType tVIEW = new ScalaTokenType("<%");
    IElementType tINNER_CLASS = new ScalaTokenType("#");
    IElementType tAT = new ScalaTokenType("@");

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
            tDOC_COMMENT,
            ScalaDocElementTypes.SCALA_DOC_COMMENT,
            ScalaDirectiveElementTypes.SCALA_DIRECTIVE
    );

    TokenSet WHITES_SPACES_AND_COMMENTS_TOKEN_SET = TokenSet.orSet(COMMENTS_TOKEN_SET, WHITES_SPACES_TOKEN_SET);

    TokenSet KEYWORDS = TokenSet.create(
            kABSTRACT,
            kCASE,
            kCATCH,
            ClassKeyword(),
            kDEF,
            kDO,
            kELSE,
            EndKeyword(),
            EnumKeyword(),
            ExportKeyword(),
            kEXTENDS,
            ExtensionKeyword(),
            kFALSE,
            kFINAL,
            kFINALLY,
            kFOR,
            kFOR_SOME, // scala 2 only
            GivenKeyword(),
            kIF,
            kIMPLICIT,
            kIMPORT,
            kLAZY,
            kMATCH,
            NewKeyword(),
            kNULL,
            ObjectKeyword(),
            kOVERRIDE,
            kPACKAGE,
            kPRIVATE,
            kPROTECTED,
            kRETURN,
            kSEALED,
            kSUPER,
            ThenKeyword(),
            kTHIS, // scala 2 only
            kTHROW,
            TraitKeyword(),
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

    // Soft keywords are highlighted by `ScalaColorSchemeAnnotator`
    TokenSet SOFT_KEYWORDS = TokenSet.create(
            AsKeyword(),
            DerivesKeyword(),
            EndKeyword(),
            ExtensionKeyword(),
            OpaqueKeyword(),
            InlineKeyword(),
            TransparentKeyword(),
            UsingKeyword(),
            OpenKeyword(),
            InfixKeyword()
    );

    TokenSet IDENTIFIER_TOKEN_SET = TokenSet.create(tIDENTIFIER, tINTERPOLATED_STRING_ID);

    TokenSet STRING_LITERAL_TOKEN_SET = TokenSet.create(
            tSTRING,
            tWRONG_STRING,
            // tWRONG_LINE_BREAK_IN_STRING // TODO: should we add it here?
            tMULTILINE_STRING,
            tINTERPOLATED_STRING,
            tINTERPOLATED_MULTILINE_STRING,
            tINTERPOLATED_STRING_END
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

    TokenSet YIELD_OR_DO = TokenSet.create(kYIELD, kDO);

    TokenSet LBRACE_OR_COLON_TOKEN_SET = TokenSet.create(tLBRACE, tCOLON);

    /*
        In the scala 2 compiler:

        def isExprIntroToken(token: Token): Boolean =
          !isValidSoftModifier && (isLiteralToken(token) || (token match {
            case IDENTIFIER | BACKQUOTED_IDENT |
                 THIS | SUPER | IF | FOR | NEW | USCORE | TRY | WHILE |
                 DO | RETURN | THROW | LPAREN | LBRACE | XMLSTART => true
            case _ => false
          }))
     */
    TokenSet EXPR_START_TOKEN_SET = TokenSet.orSet(
            LITERALS,
            TokenSet.create(
                    kNULL,

                    tIDENTIFIER,
                    kTHIS,
                    kSUPER,
                    kIF,
                    kFOR,
                    NewKeyword(),
                    tUNDER,
                    kTRY,
                    kWHILE,
                    kDO,
                    kRETURN,
                    kTHROW,
                    tLPARENTHESIS,
                    tLBRACE,
                    XML_START_TAG_START
            )
            //IDENTIFIER | BACKQUOTED_IDENT |
            //        THIS | SUPER | IF | FOR | NEW | USCORE | TRY | WHILE |
            //        DO | RETURN | THROW | LPAREN | LBRACE | XMLSTART
    );
}
