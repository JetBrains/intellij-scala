package org.jetbrains.plugins.scala.lang.psi.impl.base


import api.expr.ScAnnotations
import com.intellij.psi.util.PsiUtil
import java.lang.String
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import stubs.{ScModifiersStub, ScParameterStub}

import com.intellij.psi._
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.base._

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScModifierListImpl extends ScalaStubBasedElementImpl[ScModifierList] with ScModifierList {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScModifiersStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "Modifiers"

  def hasModifierProperty(name: String) = {
    name match {
      case "override" => has(ScalaTokenTypes.kOVERRIDE)
      case "private" => has(ScalaTokenTypes.kPRIVATE)
      case "protected" => has(ScalaTokenTypes.kPROTECTED)
      case "public" => !(has(ScalaTokenTypes.kPROTECTED) || has(ScalaTokenTypes.kPRIVATE))
      case "final" => has(ScalaTokenTypes.kFINAL)
      case "implicit" => has(ScalaTokenTypes.kIMPLICIT)
      case "abstract" => has(ScalaTokenTypes.kABSTRACT)
      case "sealed" => has(ScalaTokenTypes.kSEALED)
      case "lazy" => has(ScalaTokenTypes.kLAZY)
      case "case" => has(ScalaTokenTypes.kCASE)
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
        else {
        for (child <- getChildren if child.isInstanceOf[ScAccessModifier] && child.asInstanceOf[ScAccessModifier].isPrivate) {
          getNode.removeChild(child.getNode)
          return
        }
      }
      case "protected" => if (value) getNode.addChild(ScalaPsiElementFactory.createModifierFromText("protected", getManager))
        else {
        for (child <- getChildren if child.isInstanceOf[ScAccessModifier] && child.asInstanceOf[ScAccessModifier].isProtected) {
          getNode.removeChild(child.getNode)
          return
        }
      }
      case "final" => if (value) getNode.addChild(ScalaPsiElementFactory.createModifierFromText("final", getManager))
        else getNode.removeChild(findChildByType(ScalaTokenTypes.kFINAL).getNode)
      case "implicit" => if (value) getNode.addChild(ScalaPsiElementFactory.createModifierFromText("implicit", getManager))
        else getNode.removeChild(findChildByType(ScalaTokenTypes.kIMPLICIT).getNode)
      case "abstract" => if (value) getNode.addChild(ScalaPsiElementFactory.createModifierFromText("abstract", getManager))
        else getNode.removeChild(findChildByType(ScalaTokenTypes.kABSTRACT).getNode)
      case "sealed" => if (value) getNode.addChild(ScalaPsiElementFactory.createModifierFromText("sealed", getManager))
        else getNode.removeChild(findChildByType(ScalaTokenTypes.kSEALED).getNode)
      case "lazy" => if (value) getNode.addChild(ScalaPsiElementFactory.createModifierFromText("lazy", getManager))
        else getNode.removeChild(findChildByType(ScalaTokenTypes.kLAZY).getNode)
      case "case" => if (value) getNode.addChild(ScalaPsiElementFactory.createModifierFromText("case", getManager))
        else getNode.removeChild(findChildByType(ScalaTokenTypes.kCASE).getNode)
      case _ => return
    }
    if (value) getNode.addChild(ScalaPsiElementFactory.createNewLineNode(getManager, " "))
  }

  def checkSetModifierProperty(name: String, value: Boolean) {
    //todo implement me!
  }

  def getAnnotations: Array[PsiAnnotation] = {
    getParent.getNode.findChildByType(ScalaElementTypes.ANNOTATIONS) match {
      case null =>  PsiAnnotation.EMPTY_ARRAY
      case x => x.getPsi.asInstanceOf[ScAnnotations].getAnnotations.map(_.asInstanceOf[PsiAnnotation])
    }

  }

  def findAnnotation(name: String): PsiAnnotation = {
    getAnnotations.find(_.getQualifiedName == name) match {
      case None => null
      case Some(x) => x
    }
  }

  def has(prop: IElementType) = {
    val access = findChildByClass(classOf[ScAccessModifier])
    prop match {
      case ScalaTokenTypes.kPRIVATE if access != null => access.access match {
        case access.Access.PRIVATE | access.Access.THIS_PRIVATE => true
        case _ => false
      }
      case ScalaTokenTypes.kPROTECTED if access != null => access.access match {
        case access.Access.PROTECTED | access.Access.THIS_PROTECTED => true
        case _ => false
      }
      case _ => findChildByType(prop) != null
    }
  }

  def addAnnotation(qualifiedName: String): PsiAnnotation = {
    null
  }
}