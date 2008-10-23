package org.jetbrains.plugins.scala.lang.psi.impl.base


import com.intellij.psi.util.PsiUtil
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
    checkSetModifierProperty(name, value)
    if (hasModifierProperty(name) == value) return
    name match {
      case "override" => if (value) getNode.addChild(ScalaPsiElementFactory.createModifierFromText("override", getManager))
        else getNode.removeChild(findChildByType(ScalaTokenTypes.kOVERRIDE).getNode)
      case "private" => if (value) getNode.addChild(ScalaPsiElementFactory.createModifierFromText("private", getManager))
        else getNode.removeChild(findChildByType(ScalaTokenTypes.kPRIVATE).getNode)
      case "protected" => if (value) getNode.addChild(ScalaPsiElementFactory.createModifierFromText("protected", getManager))
        else getNode.removeChild(findChildByType(ScalaTokenTypes.kPROTECTED).getNode)
      case "final" => if (value) getNode.addChild(ScalaPsiElementFactory.createModifierFromText("final", getManager))
        else getNode.removeChild(findChildByType(ScalaTokenTypes.kFINAL).getNode)
      case "implicit" => if (value) getNode.addChild(ScalaPsiElementFactory.createModifierFromText("implicit", getManager))
        else getNode.removeChild(findChildByType(ScalaTokenTypes.kIMPLICIT).getNode)
      case "abstract" => if (value) getNode.addChild(ScalaPsiElementFactory.createModifierFromText("abstract", getManager))
        else getNode.removeChild(findChildByType(ScalaTokenTypes.kABSTRACT).getNode)
      case "sealed" => if (value) getNode.addChild(ScalaPsiElementFactory.createModifierFromText("sealed", getManager))
        else getNode.removeChild(findChildByType(ScalaTokenTypes.kSEALED).getNode)
      case _ => return
    }
    if (value) getNode.addChild(ScalaPsiElementFactory.createNewLineNode(getManager, " "))
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