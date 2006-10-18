package org.jetbrains.plugins.scala.lang.parser

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.PsiElement

import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.psi.impl._
import org.jetbrains.plugins.scala.lang.psi.impl.literals._
import org.jetbrains.plugins.scala.lang.psi.impl.types._

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
     case ScalaElementTypes.NULL => new ScNullImpl(node)

    /********************** TYPES ************************/

    case ScalaElementTypes.IDENTIFIER => new ScIdentifierImpl(node)
    case ScalaElementTypes.THIS => new ScThisImpl(node)
    case ScalaElementTypes.WITH => new ScWithImpl(node)
    case ScalaElementTypes.SUPER => new ScSuperImpl(node)
    case ScalaElementTypes.DOT => new ScDotImpl(node)
    case ScalaElementTypes.LSQBRACKET => new ScLsqbracketImpl(node)
    case ScalaElementTypes.RSQBRACKET => new ScRsqbracketImpl(node)
    case ScalaElementTypes.KEY_TYPE => new ScTypeImpl(node)
    case ScalaElementTypes.INNER_CLASS => new ScSharpImpl(node)

    case ScalaElementTypes.STABLE_ID => new ScStableIdImpl(node)
    case ScalaElementTypes.PATH => new ScPathImpl(node)
    case ScalaElementTypes.SIMPLE_TYPE => new ScSimpleTypeImpl(node)


     /*
     case ScalaTokenTypes.kPACKAGE => new ScPackage( node )
     case ScalaTokenTypes.kCLASS => new ScClass( node )
     case ScalaTokenTypes.kOBJECT => new ScObject( node )
     case ScalaTokenTypes.kTRAIT => new ScTrait( node )
     case ScalaTokenTypes.kIMPORT => new ScImport( node )
     */
     case _ => new ScalaPsiElementImpl( node )

   }
         
  }
}
