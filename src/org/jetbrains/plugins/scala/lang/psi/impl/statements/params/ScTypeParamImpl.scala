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
import types.ScType
import types.result.{Failure, Success}

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScTypeParamImpl extends ScalaStubBasedElementImpl[ScTypeParam] with ScTypeBoundsOwnerImpl with ScTypeParam with PsiClassFake {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScTypeParamStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "TypeParameter"

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
    containingFile.getName
  }

  def getIndex: Int = 0
  def getOwner: PsiTypeParameterListOwner = getContext.getContext match {
    case c : PsiTypeParameterListOwner => c
    case _ => null
  }

  override def getContainingClass() = null

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

  def owner  = getContext.getContext.asInstanceOf[ScTypeParametersOwner]

  override def getUseScope  = new LocalSearchScope(owner)

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
        val psiType = ScType.toPsi(t, getProject, getResolveScope)
        psiType match {
          case x: PsiClassType => Array(x)
          case _ => Array() // TODO
        }
      case Failure(_, _) => Array()
    }
  }
}