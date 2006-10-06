package org.jetbrains.plugins.scala.lang.parser

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.psi.impl._
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
     case ScalaTokenTypes.kPACKAGE => new ScPackage( node )
     case ScalaTokenTypes.kCLASS => new ScClass( node )
     case ScalaTokenTypes.kOBJECT => new ScObject( node )
     case ScalaTokenTypes.kTRAIT => new ScTrait( node )
     case _ => new ScalaPsiElementImpl( node )
   }
         
  }
}
