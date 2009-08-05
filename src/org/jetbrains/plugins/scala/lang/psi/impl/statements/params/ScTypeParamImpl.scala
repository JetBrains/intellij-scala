package org.jetbrains.plugins.scala.lang.psi.impl.statements.params

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
/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScTypeParamImpl extends ScalaStubBasedElementImpl[ScTypeParam] with ScTypeBoundsOwnerImpl with ScTypeParam with PsiClassFake {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScTypeParamStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "TypeParameter"

  def getIndex() : Int = 0
  def getOwner() : PsiTypeParameterListOwner = getParent.getParent match {
    case c : PsiTypeParameterListOwner => c
    case _ => null
  }

  override def getContainingClass() = null

  def isCovariant = findChildByType(ScalaTokenTypes.tIDENTIFIER) match {
    case null => false
    case x => x.getText == "+"
  }

  def isContravariant = findChildByType(ScalaTokenTypes.tIDENTIFIER) match {
    case null => false
    case x => x.getText == "-"
  }

  def owner  = getParent.getParent.asInstanceOf[ScTypeParametersOwner]

  override def getUseScope  = new LocalSearchScope(owner)

  def nameId = findLastChildByType(TokenSets.ID_SET)

  override def getNameIdentifier: PsiIdentifier = new JavaIdentifier(nameId)


  override def viewTypeElement: Option[ScTypeElement] = {
    val stub = getStub
    if (stub != null) {
      stub.asInstanceOf[ScTypeParamStub].getViewTypeElement
    } else super.viewTypeElement
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
}