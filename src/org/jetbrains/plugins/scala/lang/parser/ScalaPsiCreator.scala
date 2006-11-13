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
import org.jetbrains.plugins.scala.lang.psi.impl.top.templates._
import org.jetbrains.plugins.scala.lang.psi.impl.top.defs._
import org.jetbrains.plugins.scala.lang.psi.impl.top.params._
import org.jetbrains.plugins.scala.lang.psi.impl.top.templateStatements._
import org.jetbrains.plugins.scala.lang.psi.impl.top._, org.jetbrains.plugins.scala.lang.psi.impl.primitives._

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl

object ScalaPsiCreator {
  def createElement (node : ASTNode) : PsiElement = {

   node.getElementType() match {

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

     case ScalaElementTypes.IMPORT_STMT => new ScImportStmt( node )
     case ScalaElementTypes.IMPORT_EXPR => new ScImportExpr( node )
     case ScalaElementTypes.IMPORT_EXPRS => new ScImportExprs( node )

     /***************************************************/
     /********************** DEF ************************/
     /***************************************************/

     case ScalaTokenTypes.kPACKAGE => new ScPackage( node )
     case ScalaElementTypes.CLASS_DEF => new ScClassDefinition( node )
     case ScalaElementTypes.OBJECT_DEF => new ScObjectDefinition( node )
     case ScalaElementTypes.TRAIT_DEF => new ScTraitDefinition( node )

     case ScalaElementTypes.CLASS_PARAM_CLAUSE => new ScClassParamClause( node )
     case ScalaElementTypes.TMPL_TYPE_PARAM_CLAUSE => new ScTmplTypeParameterClause( node )

     /***************************************************/
     /******************** TEMPLATES ********************/
     /***************************************************/

     case ScalaElementTypes.OBJECT_TEMPLATE => new ScObjectTemplate( node )
     case ScalaElementTypes.CLASS_TEMPLATE => new ScClassTemplate( node )
     case ScalaElementTypes.TRAIT_TEMPLATE => new ScTraitTemplate( node )

     /******************* parents ****************/
     case ScalaElementTypes.TEMPLATE_PARENTS => new ScTemplateParents( node )
     case ScalaElementTypes.MIXIN_PARENTS => new ScMixinParents( node )

     /******************* body *******************/
     case ScalaElementTypes.TEMPLATE_BODY => new ScTemplateBody( node )


     /***************************************************/
     /*************** TEMPLATE STATEMENTS ***************/
     /***************************************************/

     /*************** DECLARATION ***************/
     case ScalaElementTypes.VALUE_DECLARATION => new ScValueDeclaration(node)
     case ScalaElementTypes.VARIABLE_DECLARATION => new ScVariableDeclaration(node)
     case ScalaElementTypes.FUNCTION_DECLARATION => new ScFunctionDeclaration(node)
     case ScalaElementTypes.TYPE_DECLARATION => new ScTypeDeclaration(node)

     /*************** DEFINITION ***************/
     case ScalaElementTypes.PATTERN_DEFINITION => new ScPatternDefinition(node)
     case ScalaElementTypes.VARIABLE_DEFINITION => new ScVariableDefinition(node)
     case ScalaElementTypes.FUNCTION_DEFINITION => new ScFunctionDefinition(node)
     case ScalaElementTypes.TYPE_DEFINITION => new ScTypeDefinition(node)

     /**************** OTHERS ******************/
     case ScalaElementTypes.FUN_SIG => new ScFunctionSignature(node)
     case ScalaElementTypes.FUN_TYPE_PARAM_CLAUSE => new ScFunctionTypeParamClause(node)

     /********************** TOKENS **********************/

      /********************* LITERALS *********************/
       case ScalaElementTypes.LITERAL => new ScLiteralImpl(node)       

      case ScalaTokenTypes.tIDENTIFIER => new ScIdentifierImpl(node)



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
//    case ScalaElementTypes.EXPR1 => new ScCompositeExprImpl(node)
    case ScalaElementTypes.EXPR => new ScExprImpl(node)
    case ScalaElementTypes.BINDING => new ScBindingImpl(node)
    case ScalaElementTypes.AN_FUN => new ScAnFunImpl(node)
    case ScalaElementTypes.EXPRS => new ScExprsImpl(node)
    case ScalaElementTypes.ARG_EXPRS => new ScArgumentExprsImpl(node)
    case ScalaElementTypes.BLOCK_EXPR => new ScBlockExprImpl(node)
    case ScalaElementTypes.BLOCK_STAT => new ScBlockStatImpl(node)

    case ScalaElementTypes.IF_STMT => new ScIfStmtImpl(node)
    case ScalaElementTypes.WHILE_STMT => new ScWhileStmtImpl(node)
    case ScalaElementTypes.RETURN_STMT => new ScReturnStmtImpl(node)
    case ScalaElementTypes.THROW_STMT => new ScThrowStmtImpl(node)
    case ScalaElementTypes.ASSIGN_STMT => new ScAssignStmtImpl(node)
    case ScalaElementTypes.TYPED_EXPR_STMT => new ScTypedStmtImpl(node)
    case ScalaElementTypes.MATCH_STMT => new ScMatchStmtImpl(node)

    /******************* PATTERNS *********************/
    case ScalaElementTypes.PATTERN1 => new ScPattern1Impl(node)
    case ScalaElementTypes.PATTERN => new ScPatternImpl(node)
    case ScalaElementTypes.PATTERNS => new ScPatternsImpl(node)
    case ScalaElementTypes.WILD_PATTERN => new ScWildPatternImpl(node)
    case ScalaElementTypes.CASE_CLAUSE => new ScCaseClauseImpl(node)




     case _ => new ScalaPsiElementImpl( node )

   }
         
  }
}
