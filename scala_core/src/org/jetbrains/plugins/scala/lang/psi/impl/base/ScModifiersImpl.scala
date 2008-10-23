package org.jetbrains.plugins.scala.lang.psi.impl.base

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType;
import com.intellij.psi._
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.base._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScModifierListImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScModifierList {

  override def toString: String = "Modifiers"

  def hasModifierProperty(name: String) = {
    name match {
      case "override" => has(ScalaTokenTypes.kOVERRIDE)
      case "private" => has(ScalaTokenTypes.kPRIVATE)
      case "protected" => has(ScalaTokenTypes.kPROTECTED)
      case "final" => has(ScalaTokenTypes.kFINAL)
      case "implicit" => has(ScalaTokenTypes.kIMPLICIT)
      case "abstract" => has(ScalaTokenTypes.kABSTRACT)
      case "sealed" => has(ScalaTokenTypes.kSEALED)
      case _ => false
    }
  }

  def hasExplicitModifier(name: String) = false

  def setModifierProperty(name: String, value: Boolean) {
    //todo implement me!
  }

  def checkSetModifierProperty(name: String, value: Boolean) {
    //todo implement me!
  }

  //todo implement me!
  def getAnnotations = PsiAnnotation.EMPTY_ARRAY

  //todo implement me!
  def findAnnotation(name: String) = null

  def has (prop : IElementType) = findChildByType(prop) != null
}