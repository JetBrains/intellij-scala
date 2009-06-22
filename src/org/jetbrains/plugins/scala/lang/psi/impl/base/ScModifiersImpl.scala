package org.jetbrains.plugins.scala.lang.psi.impl.base


import api.expr.ScAnnotations
import com.intellij.util.ArrayFactory
import java.lang.String
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import stubs.ScModifiersStub

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.api.base._

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScModifierListImpl extends ScalaStubBasedElementImpl[ScModifierList] with ScModifierList {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScModifiersStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "Modifiers"

  def hasModifierProperty(name: String): Boolean = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScModifiersStub].getModifiers.exists(_ == name)
    }
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

  private def prop2String(prop: IElementType): String = {
    prop match {
      case ScalaTokenTypes.kOVERRIDE => "override"
      case ScalaTokenTypes.kPRIVATE => "private"
      case ScalaTokenTypes.kPROTECTED => "protected"
      case ScalaTokenTypes.kFINAL => "final"
      case ScalaTokenTypes.kIMPLICIT => "implicit"
      case ScalaTokenTypes.kABSTRACT => "abstract"
      case ScalaTokenTypes.kSEALED => "sealed"
      case ScalaTokenTypes.kLAZY => "lazy"
      case ScalaTokenTypes.kCASE => "case"
      case _ => ""
    }
  }


  def getModifiersStrings: Array[String] = {
    Array("override", "private", "protected", "public", "final", "implicit", "abstract", "sealed", "lazy", "case").
      filter(hasModifierProperty(_))
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
    val stub = getStub
    if (stub != null) {
      val annotations: Array[ScAnnotations] = stub.getParentStub.
              getChildrenByType(ScalaElementTypes.ANNOTATIONS, new ArrayFactory[ScAnnotations]{
        def create(count: Int): Array[ScAnnotations] = new Array[ScAnnotations](count)
      })
      if (annotations.length > 0) {
        return annotations.apply(0).getAnnotations.map(_.asInstanceOf[PsiAnnotation])
      } else return PsiAnnotation.EMPTY_ARRAY
    }
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
    val access = getStubOrPsiChild(ScalaElementTypes.ACCESS_MODIFIER)
    prop match {
      case ScalaTokenTypes.kPRIVATE if access != null => access.access match {
        case access.Access.PRIVATE | access.Access.THIS_PRIVATE => true
        case _ => false
      }
      case ScalaTokenTypes.kPROTECTED if access != null => access.access match {
        case access.Access.PROTECTED | access.Access.THIS_PROTECTED => true
        case _ => false
      }
      case _ => {
        val stub = getStub
        if (stub != null) {
          stub.asInstanceOf[ScModifiersStub].getModifiers.exists(_ == prop2String(prop))
        } else findChildByType(prop) != null
      }
    }
  }

  def addAnnotation(qualifiedName: String): PsiAnnotation = {
    null
  }
}