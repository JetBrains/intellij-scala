package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.JavaArrayFactoryUtil.ScAnnotationsFactory
import org.jetbrains.plugins.scala.extensions.StubBasedExt
import org.jetbrains.plugins.scala.lang.TokenSets.TokenSetExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScModifierListImpl.AllModifiers
import org.jetbrains.plugins.scala.lang.psi.stubs.ScModifiersStub

import scala.collection.mutable.ArrayBuffer

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/
class ScModifierListImpl private (stub: ScModifiersStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementTypes.MODIFIERS, node) with ScModifierList {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScModifiersStub) = this(stub, null)

  override def toString: String = "Modifiers"

  def getApplicableAnnotations: Array[PsiAnnotation] = PsiAnnotation.EMPTY_ARRAY

  def hasModifierProperty(name: String): Boolean = {
    byStubOrPsi(_.modifiers.contains(name)) {
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

  def accessModifier: Option[ScAccessModifier] = Option(getStubOrPsiChild(ScalaElementTypes.ACCESS_MODIFIER))

  def modifiers: Array[String] = byStubOrPsi(_.modifiers)(AllModifiers.filter(hasModifierProperty))

  def hasExplicitModifiers: Boolean = byStubOrPsi(_.hasExplicitModifiers) {
    !findChildrenByType(TokenSets.MODIFIERS + ScalaElementTypes.ACCESS_MODIFIER).isEmpty
  }

  def hasExplicitModifier(name: String) = false

  def setModifierProperty(name: String, value: Boolean) {
    def space = createNewLineNode(" ")
    checkSetModifierProperty(name, value)
    if (hasModifierProperty(name) == value) return

    def addAfter(modifier: String): Unit = {
      val wasEmpty = getFirstChild == null
      if (!wasEmpty) getNode.addChild(space)
      getNode.addChild(createModifierFromText(modifier).getNode)
      if (wasEmpty) getNode.addChild(space)
    }

    def addBefore(modifier: String): Unit = {
      val node = createModifierFromText(modifier).getNode

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
        getNode.addChild(node)
        parent.getNode.addChild(space, nextSibling.getNode)
        return
      }
      getNode.addChild(node, first.getNode)
      getNode.addChild(space, first.getNode)
    }
    name match {
      case "override" => if (value) {
        addBefore("override")
      }
        else getNode.removeChild(findChildByType[PsiElement](ScalaTokenTypes.kOVERRIDE).getNode)
      case "private" => if (value) {
        addBefore("private")
      }
        else {
        for (child <- getChildren if child.isInstanceOf[ScAccessModifier] && child.asInstanceOf[ScAccessModifier].isPrivate) {
          getNode.removeChild(child.getNode)
          return
        }
      }
      case "protected" => if (value) {
        addBefore("protected")
      }
        else {
        for (child <- getChildren if child.isInstanceOf[ScAccessModifier] && child.asInstanceOf[ScAccessModifier].isProtected) {
          getNode.removeChild(child.getNode)
          return
        }
      }
      case "final" => if (value) {
        addBefore("final")
      }
        else getNode.removeChild(findChildByType[PsiElement](ScalaTokenTypes.kFINAL).getNode)
      case "implicit" => if (value) {
        addBefore("implicit")
      }
        else getNode.removeChild(findChildByType[PsiElement](ScalaTokenTypes.kIMPLICIT).getNode)
      case "abstract" => if (value) {
        addBefore("abstract")
      }
        else getNode.removeChild(findChildByType[PsiElement](ScalaTokenTypes.kABSTRACT).getNode)
      case "sealed" => if (value) {
        addBefore("sealed")
      }
        else getNode.removeChild(findChildByType[PsiElement](ScalaTokenTypes.kSEALED).getNode)
      case "lazy" => if (value) {
        addBefore("lazy")
      }
        else getNode.removeChild(findChildByType[PsiElement](ScalaTokenTypes.kLAZY).getNode)
      case "case" => if (value) {
        addAfter("case")
      }
        else getNode.removeChild(findChildByType[PsiElement](ScalaTokenTypes.kCASE).getNode)
      case _ =>
    }
  }

  def checkSetModifierProperty(name: String, value: Boolean) {
    //todo implement me!
  }

  def getAnnotations: Array[PsiAnnotation] = {
    getParent match {
      case null => PsiAnnotation.EMPTY_ARRAY
      case parent =>
        val annotations = parent.stubOrPsiChildren(ScalaElementTypes.ANNOTATIONS, ScAnnotationsFactory)
        if (annotations.length == 0) PsiAnnotation.EMPTY_ARRAY
        else {
          val scAnnotations = annotations(0).getAnnotations
          val result = PsiAnnotation.ARRAY_FACTORY.create(scAnnotations.length)
          scAnnotations.copyToArray(result)
          result
        }
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

  def has(prop: IElementType): Boolean = {
    val modifier = getStubOrPsiChild(ScalaElementTypes.ACCESS_MODIFIER)
    prop match {
      case ScalaTokenTypes.kPRIVATE if modifier != null => modifier.access match {
        case ScAccessModifier.Type.PRIVATE | ScAccessModifier.Type.THIS_PRIVATE => true
        case _ => false
      }
      case ScalaTokenTypes.kPROTECTED if modifier != null => modifier.access match {
        case ScAccessModifier.Type.PROTECTED | ScAccessModifier.Type.THIS_PROTECTED => true
        case _ => false
      }
      case _ =>
        byStubOrPsi(_.modifiers.contains(prop2String(prop)))(findChildByType(prop) != null)
    }
  }

  def addAnnotation(qualifiedName: String): PsiAnnotation = {
    null
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitModifierList(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => s.visitModifierList(this)
      case _ => super.accept(visitor)
    }
  }
}

object ScModifierListImpl {
  private val AllModifiers: Array[String] = Array("override", "private", "protected", "public", "final", "implicit", "abstract", "sealed", "lazy", "case")
}