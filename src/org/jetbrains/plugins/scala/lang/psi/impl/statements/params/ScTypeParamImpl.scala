package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements
package params

import api.base.types.ScTypeElement
import api.toplevel.ScTypeParametersOwner
import com.intellij.psi.search.LocalSearchScope
import java.lang.String
import lexer.ScalaTokenTypes
import com.intellij.lang.ASTNode
import com.intellij.psi._

import psi.stubs.ScTypeParamStub
import toplevel.PsiClassFake
import api.statements.params._
import base.ScTypeBoundsOwnerImpl
import toplevel.synthetic.JavaIdentifier
import icons.Icons
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType}
import types.result.{Failure, Success}
import extensions.toPsiNamedElementExt
import api.toplevel.typedef.ScTemplateDefinition

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScTypeParamImpl extends ScalaStubBasedElementImpl[ScTypeParam] with ScTypeBoundsOwnerImpl with ScTypeParam with PsiClassFake {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScTypeParamStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "TypeParameter: " + name

  def getOffsetInFile: Int = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScTypeParamStub].getPositionInFile
    }
    getTextRange.getStartOffset
  }
  
  def getContainingFileName: String = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScTypeParamStub].getContainingFileName
    }
    val containingFile = getContainingFile
    if (containingFile == null) return "NoFile"
    containingFile.name
  }

  def getIndex: Int = 0
  def getOwner: PsiTypeParameterListOwner = getContext.getContext match {
    case c : PsiTypeParameterListOwner => c
    case _ => null
  }

  override def getContainingClass: ScTemplateDefinition = null

  def isCovariant: Boolean = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScTypeParamStub].isCovariant
    }
    findChildByType(ScalaTokenTypes.tIDENTIFIER) match {
      case null => false
      case x => x.getText == "+"
    }
  }

  def isContravariant: Boolean = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScTypeParamStub].isContravariant
    }
    findChildByType(ScalaTokenTypes.tIDENTIFIER) match {
      case null => false
      case x => x.getText == "-"
    }
  }

  def typeParameterText: String = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScTypeParamStub].typeParameterText
    }
    getText
  }

  def owner  = getContext.getContext.asInstanceOf[ScTypeParametersOwner]

  override def getUseScope  = new LocalSearchScope(owner).intersectWith(super.getUseScope)

  def nameId = findLastChildByType(TokenSets.ID_SET)

  override def getNameIdentifier: PsiIdentifier = new JavaIdentifier(nameId)

  override def viewTypeElement: List[ScTypeElement] = {
    val stub = getStub
    if (stub != null) {
      stub.asInstanceOf[ScTypeParamStub].getViewTypeElement.toList
    } else super.viewTypeElement
  }

  override def contextBoundTypeElement: List[ScTypeElement] = {
    val stub = getStub
    if (stub != null) {
      stub.asInstanceOf[ScTypeParamStub].getContextBoundTypeElement.toList
    } else super.contextBoundTypeElement
  }

  override def lowerTypeElement: Option[ScTypeElement] = {
    val stub = getStub
    if (stub != null) {
      stub.asInstanceOf[ScTypeParamStub].getLowerTypeElement
    } else super.lowerTypeElement
  }

  override def upperTypeElement: Option[ScTypeElement] = {
    val stub = getStub
    if (stub != null) {
      stub.asInstanceOf[ScTypeParamStub].getUpperTypeElement
    } else super.upperTypeElement
  }

  def addAnnotation(p1: String): PsiAnnotation = null

  def getAnnotations: Array[PsiAnnotation] = PsiAnnotation.EMPTY_ARRAY

  def getApplicableAnnotations: Array[PsiAnnotation] = PsiAnnotation.EMPTY_ARRAY

  def findAnnotation(p1: String): PsiAnnotation = null

  override def getIcon(flags: Int) = {
    Icons.TYPE_ALIAS
  }

  override def getSuperTypes: Array[PsiClassType] = {
    // For Java
    upperBound match {
      case Success(t, _) =>
        val psiType = if (hasTypeParameters) {
          t match {
            case ScParameterizedType(des, _) => ScType.toPsi(des, getProject, getResolveScope)
            case _ => ScType.toPsi(t, getProject, getResolveScope)
          }
        } else {
          ScType.toPsi(t, getProject, getResolveScope)
        }
        psiType match {
          case x: PsiClassType => Array(x)
          case _ => Array() // TODO
        }
      case Failure(_, _) => Array()
    }
  }
}