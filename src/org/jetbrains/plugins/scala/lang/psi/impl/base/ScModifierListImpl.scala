package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base


import api.expr.ScAnnotations
import com.intellij.util.ArrayFactory
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import stubs.ScModifiersStub

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.api.base._
import collection.mutable.ArrayBuffer

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScModifierListImpl extends ScalaStubBasedElementImpl[ScModifierList] with ScModifierList {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScModifiersStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "Modifiers"

  def getApplicableAnnotations: Array[PsiAnnotation] = PsiAnnotation.EMPTY_ARRAY

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

  def accessModifier: Option[ScAccessModifier] = {
    val stub = getStub
    if (stub != null) {
      val am = stub.findChildStubByType(ScalaElementTypes.ACCESS_MODIFIER)
      if (am != null) {
        return Some(am.getPsi)
      } else return None
    }
    findChild(classOf[ScAccessModifier])
  }

  def getModifiersStrings: Array[String] = ScModifierListImpl.AllModifiers.filter(hasModifierProperty(_))

  def hasExplicitModifiers: Boolean = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScModifiersStub].hasExplicitModifiers
    }

    val access = getStubOrPsiChild(ScalaElementTypes.ACCESS_MODIFIER)
    access != null || findChildrenByType(TokenSets.MODIFIERS).size > 0
  }

  def hasExplicitModifier(name: String) = false

  def setModifierProperty(name: String, value: Boolean) {
    checkSetModifierProperty(name, value)
    if (hasModifierProperty(name) == value) return
    def addAfter(node: ASTNode) {
      val space = ScalaPsiElementFactory.createNewLineNode(getManager, " ")
      if (getFirstChild != null)
        getNode.addChild(space)
      getNode.addChild(node)
    }
    def addBefore(node: ASTNode) {
      val first = getFirstChild
      if (first == null) {
        val buf = new ArrayBuffer[ASTNode]()
        var nextSibling = getNextSibling
        while (ScalaTokenTypes.WHITES_SPACES_AND_COMMENTS_TOKEN_SET.contains(nextSibling.getNode.getElementType)) {
          buf += nextSibling.getNode
          nextSibling = nextSibling.getNextSibling
        }
        
        val parent = getParent
        for (node <- buf) {
          parent.getNode.removeChild(node)
          parent.getNode.addChild(node, getNode)
        }
        val space = ScalaPsiElementFactory.createNewLineNode(getManager, " ")
        getNode.addChild(node)
        parent.getNode.addChild(space, nextSibling.getNode)
        return
      }
      val space = ScalaPsiElementFactory.createNewLineNode(getManager, " ")
      getNode.addChild(node, first.getNode)
      getNode.addChild(space, first.getNode)
    }
    name match {
      case "override" => if (value) {
        val node = ScalaPsiElementFactory.createModifierFromText("override", getManager)
        addBefore(node)
      }
        else getNode.removeChild(findChildByType(ScalaTokenTypes.kOVERRIDE).getNode)
      case "private" => if (value) {
        val node = ScalaPsiElementFactory.createModifierFromText("private", getManager)
        addBefore(node)
      }
        else {
        for (child <- getChildren if child.isInstanceOf[ScAccessModifier] && child.asInstanceOf[ScAccessModifier].isPrivate) {
          getNode.removeChild(child.getNode)
          return
        }
      }
      case "protected" => if (value) {
        val node = ScalaPsiElementFactory.createModifierFromText("protected", getManager)
        addBefore(node)
      }
        else {
        for (child <- getChildren if child.isInstanceOf[ScAccessModifier] && child.asInstanceOf[ScAccessModifier].isProtected) {
          getNode.removeChild(child.getNode)
          return
        }
      }
      case "final" => if (value) {
        val node = ScalaPsiElementFactory.createModifierFromText("final", getManager)
        addBefore(node)
      }
        else getNode.removeChild(findChildByType(ScalaTokenTypes.kFINAL).getNode)
      case "implicit" => if (value) {
        val node = ScalaPsiElementFactory.createModifierFromText("implicit", getManager)
        addBefore(node)
      }
        else getNode.removeChild(findChildByType(ScalaTokenTypes.kIMPLICIT).getNode)
      case "abstract" => if (value) {
        val node = ScalaPsiElementFactory.createModifierFromText("abstract", getManager)
        addBefore(node)
      }
        else getNode.removeChild(findChildByType(ScalaTokenTypes.kABSTRACT).getNode)
      case "sealed" => if (value) {
        val node = ScalaPsiElementFactory.createModifierFromText("sealed", getManager)
        addBefore(node)
      }
        else getNode.removeChild(findChildByType(ScalaTokenTypes.kSEALED).getNode)
      case "lazy" => if (value) {
        val node = ScalaPsiElementFactory.createModifierFromText("lazy", getManager)
        addBefore(node)
      }
        else getNode.removeChild(findChildByType(ScalaTokenTypes.kLAZY).getNode)
      case "case" => if (value) {
        val node = ScalaPsiElementFactory.createModifierFromText("case", getManager)
        addAfter(node)
      }
        else getNode.removeChild(findChildByType(ScalaTokenTypes.kCASE).getNode)
      case _ => return
    }
  }

  def checkSetModifierProperty(name: String, value: Boolean) {
    //todo implement me!
  }

  def getAnnotations: Array[PsiAnnotation] = {
    val stub = getStub
    if (stub != null) {
      val annotations: Array[ScAnnotations] = stub.getParentStub.
              getChildrenByType(ScalaElementTypes.ANNOTATIONS, JavaArrayFactoryUtil.ScAnnotationsFactory)
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
      case None if name == "java.lang.Override" =>
        val factory = JavaPsiFacade.getInstance(getProject).getElementFactory
        factory.createAnnotationFromText("@" + name, this); // hack to disable AddOverrideAnnotationAction, 
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

object ScModifierListImpl {
  private val AllModifiers: Array[String] = Array("override", "private", "protected", "public", "final", "implicit", "abstract", "sealed", "lazy", "case")
}