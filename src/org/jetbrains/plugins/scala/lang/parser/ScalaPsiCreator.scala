package org.jetbrains.plugins.scala.lang.parser

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.PsiElement

import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.psi.impl._
import org.jetbrains.plugins.scala.lang.psi.impl.literals._
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._
import org.jetbrains.plugins.scala.lang.psi.impl.types._
import org.jetbrains.plugins.scala.lang.psi.impl.patterns._
import org.jetbrains.plugins.scala.lang.psi.impl.top._, org.jetbrains.plugins.scala.lang.psi.impl.primitives._

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl

object ScalaPsiCreator {
  def createElement (node : ASTNode) : PsiElement = {

   node.getElementType() match {
     /********************** TOKENS **********************/

       /********************* LITERALS *********************/
//       case ScalaTokenTypes.tINTEGER  => new ScIntegerImpl(node)
//       case ScalaTokenTypes.tFLOAT  => new ScFloatImpl(node)
//       case ScalaElementTypes.BOOLEAN_LITERAL  => new ScBooleanImpl(node)
//       case ScalaTokenTypes.tCHAR => new ScCharacterImpl(node)

//       case ScalaElementTypes.STRING_LITERAL => new ScStringImpl(node)
//       case ScalaTokenTypes.tSTRING_BEGIN => new ScStringBeginImpl(node)
//       case ScalaTokenTypes.tSTRING => new ScStringContentImpl(node)
//       case ScalaTokenTypes.tSTRING_END => new ScStringEndImpl(node)

//       case ScalaTokenTypes.kNULL => new ScNullImpl(node)

      /********************** KEYWORDS *********************/
//      case ScalaTokenTypes.tIDENTIFIER => new ScIdentifierImpl(node)

//      case ScalaTokenTypes.kTHIS => new ScThisImpl(node)
//      case ScalaTokenTypes.kIF => new ScIfImpl(node)
//      case ScalaTokenTypes.kELSE => new ScElseImpl(node)
//      case ScalaTokenTypes.kWHILE => new ScWhileImpl(node)
//      case ScalaTokenTypes.kRETURN => new ScReturnImpl(node)
//      case ScalaTokenTypes.kTHROW => new ScThrowImpl(node)
//      case ScalaTokenTypes.kWITH => new ScWithImpl(node)
//      case ScalaTokenTypes.kSUPER => new ScSuperImpl(node)
//      case ScalaTokenTypes.tASSIGN => new ScAssignImpl(node)
//      case ScalaTokenTypes.kTYPE => new ScKeyTypeImpl(node)
//      case ScalaTokenTypes.tINNER_CLASS => new ScSharpImpl(node)
//      case ScalaTokenTypes.tFUNTYPE => new ScFunTypeImpl(node)

    /********************** TYPES ************************/

    case ScalaElementTypes.STABLE_ID => new ScStableIdImpl(node)
    case ScalaElementTypes.PATH => new ScPathImpl(node)
    case ScalaElementTypes.SIMPLE_TYPE => new ScSimpleTypeImpl(node)
    case ScalaElementTypes.TYPE => new ScTypeImpl(node)
    case ScalaElementTypes.TYPES => new ScTypesImpl(node)
    case ScalaElementTypes.TYPE_ARGS => new ScTypeArgsImpl(node)

    /******************* EXPRESSIONS*********************/

    case ScalaElementTypes.PREFIX_EXPR => new ScPrefixExprImpl(node)
    case ScalaElementTypes.PREFIX => new ScPrefixImpl(node)
    case ScalaElementTypes.INFIX_EXPR => new ScInfixExprImpl(node)
    case ScalaElementTypes.POSTFIX_EXPR => new ScPostfixExprImpl(node)
    case ScalaElementTypes.EXPR1 => new ScCompositeExprImpl(node)
    case ScalaElementTypes.EXPR => new ScExprImpl(node)
    case ScalaElementTypes.EXPRS => new ScExprsImpl(node)
    case ScalaElementTypes.ARG_EXPRS => new ScArgumentExprsImpl(node)
    case ScalaElementTypes.BLOCK_EXPR => new ScBlockExprImpl(node)
    case ScalaElementTypes.BLOCK_STAT => new ScBlockStatImpl(node)

    /******************* PATTERNS *********************/
    case ScalaElementTypes.PATTERN1 => new ScPattern1Impl(node)
    case ScalaElementTypes.PATTERN => new ScPatternImpl(node)
    case ScalaElementTypes.CASE_CLAUSE => new ScCaseClauseImpl(node)


    /*****************************************************/
    /********************** PRIMITIVES ********************/
    /*****************************************************/
//    case ScalaTokenTypes.tDOT => new ScDotImpl( node )
//    case ScalaTokenTypes.tCOLON => new ScColonImpl( node )
//    case ScalaTokenTypes.tCOMMA => new ScCommaImpl( node )
//    case ScalaTokenTypes.tSEMICOLON => new ScSemicolonImpl( node )
//    case ScalaElementTypes.STATEMENT_SEPARATOR => new ScStatementSeparatorImpl(node)
//    case ScalaTokenTypes.tLINE_TERMINATOR => new ScLineTerminatorImpl(node)

    /*****************************************************/
    /********************** BRACKETS *********************/
    /*****************************************************/

//    case ScalaTokenTypes.tLSQBRACKET => new ScLSQBracketImpl(node)
//    case ScalaTokenTypes.tRSQBRACKET => new ScRSQBracketImpl(node)
//    case ScalaTokenTypes.tLPARENTHIS => new ScLParenthisImpl(node)
//    case ScalaTokenTypes.tRPARENTHIS => new ScRParenthisImpl(node)
//    case ScalaTokenTypes.tLBRACE => new ScLBraceImpl(node)
//    case ScalaTokenTypes.tRBRACE => new ScRBraceImpl(node)

    /*****************************************************/
    /********************** TOP **************************/
    /*****************************************************/

     case ScalaElementTypes.PACKAGING => new ScPackaging( node )
     case ScalaElementTypes.QUAL_ID => new ScQualId( node )

     case ScalaElementTypes.TOP_STAT_SEQ => new ScTopStatSeq( node )
     case ScalaElementTypes.TOP_STAT => new ScTopStat( node )

     /***************************************************/
     /********************* IMPORT **********************/
     /***************************************************/

//    case ScalaTokenTypes.kIMPORT => new ScImport( node )
     case ScalaElementTypes.IMPORT_STMT => new ScImportStmt( node )
     case ScalaElementTypes.IMPORT_EXPR => new ScImportExpr( node )
     case ScalaElementTypes.IMPORT_EXPRS => new ScImportExprs( node )


     case ScalaTokenTypes.kPACKAGE => new ScPackage( node )
     case ScalaElementTypes.CLASS_DEF => new ScClassDef( node )
     case ScalaElementTypes.OBJECT_DEF => new ScObjectDef( node )
     case ScalaElementTypes.TRAIT_DEF => new ScTraitDef( node )

     case ScalaElementTypes.CLASS_TEMPLATE => new ScClassTemplate( node )
     case ScalaElementTypes.TRAIT_TEMPLATE => new ScTraitTemplate( node )

     case ScalaElementTypes.TEMPLATE_PARENTS => new ScTemplateParents( node )
     case ScalaElementTypes.TEMPLATE_BODY => new ScTemplateBody( node )

     case _ => new ScalaPsiElementImpl( node )

   }
         
  }
}
