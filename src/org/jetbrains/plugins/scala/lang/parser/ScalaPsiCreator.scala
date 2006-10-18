package org.jetbrains.plugins.scala.lang.parser

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.psi.impl._
import org.jetbrains.plugins.scala.lang.psi.impl.literals._
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl

/**
 * User: Dmitry.Krasilschikov
 * Date: 03.10.2006
 * Time: 19:28:50
 */
object ScalaPsiCreator {
  def createElement (node : ASTNode) : PsiElement = {

   node.getElementType() match {
     /******************* LITERALS ***********************/

     case ScalaElementTypes.INTEGER_LITERAL  => new ScInteger(node)
     case ScalaElementTypes.FLOATING_POINT_LITERAL  => new ScFloat(node)

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
