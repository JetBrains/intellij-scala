package org.jetbrains.plugins.scala.lang.parser

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.PsiElement

import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.psi.impl._
import org.jetbrains.plugins.scala.lang.psi.impl.literals._
import org.jetbrains.plugins.scala.lang.psi.impl.types._
import org.jetbrains.plugins.scala.lang.psi.impl.top._

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl

object ScalaPsiCreator {
  def createElement (node : ASTNode) : PsiElement = {

   node.getElementType() match {
     /******************* THE WHOLE FILE ******************/
     


     /******************* LITERALS ***********************/

     case ScalaElementTypes.INTEGER_LITERAL  => new ScIntegerImpl(node)
     case ScalaElementTypes.FLOATING_POINT_LITERAL  => new ScFloatImpl(node)
     case ScalaElementTypes.BOOLEAN_LITERAL  => new ScBooleanImpl(node)
     case ScalaElementTypes.CHARACTER_LITERAL  => new ScCharacterImpl(node)
     case ScalaElementTypes.STRING_LITERAL => new ScStringImpl(node)
     case ScalaElementTypes.STRING_BEGIN => new ScStringBeginImpl(node)
     case ScalaElementTypes.STRING_CONTENT => new ScStringContentImpl(node)
     case ScalaElementTypes.STRING_END => new ScStringEndImpl(node)
     case ScalaTokenTypes.kNULL => new ScNullImpl(node)

    /********************** TYPES ************************/

    case ScalaTokenTypes.tIDENTIFIER => new ScIdentifierImpl(node)
    case ScalaTokenTypes.kTHIS => new ScThisImpl(node)
    case ScalaTokenTypes.kWITH => new ScWithImpl(node)
    case ScalaTokenTypes.kSUPER => new ScSuperImpl(node)
    case ScalaTokenTypes.tDOT => new ScDotImpl(node)
    case ScalaTokenTypes.tCOMMA => new ScCommaImpl(node)
    case ScalaTokenTypes.tLSQBRACKET => new ScLsqbracketImpl(node)
    case ScalaTokenTypes.tRSQBRACKET => new ScRsqbracketImpl(node)
    case ScalaTokenTypes.tLPARENTHIS => new ScLParentImpl(node)
    case ScalaTokenTypes.tRPARENTHIS => new ScRParentImpl(node)
    case ScalaElementTypes.KEY_TYPE => new ScKeyTypeImpl(node)
    case ScalaElementTypes.INNER_CLASS => new ScSharpImpl(node)

    case ScalaElementTypes.STABLE_ID => new ScStableIdImpl(node)
    case ScalaElementTypes.PATH => new ScPathImpl(node)
    case ScalaElementTypes.SIMPLE_TYPE => new ScSimpleTypeImpl(node)
    case ScalaTokenTypes.tFUNTYPE => new ScFunTypeImpl(node)
    case ScalaElementTypes.TYPE => new ScTypeImpl(node)
    case ScalaElementTypes.TYPES => new ScTypesImpl(node)
    case ScalaElementTypes.TYPEARGS => new ScTypeArgsImpl(node)

    /********************** TOP ************************/

     case ScalaTokenTypes.kPACKAGE => new ScPackage( node )
     case ScalaTokenTypes.kCLASS => new ScClass( node )
     case ScalaTokenTypes.kOBJECT => new ScObject( node )
     case ScalaTokenTypes.kTRAIT => new ScTrait( node )
     case ScalaTokenTypes.kIMPORT => new ScImport( node )

     case _ => new ScalaPsiElementImpl( node )

   }
         
  }
}
