package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl


import api.expr.ScAnnotation
import api.toplevel.ScEarlyDefinitions
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import api.base.ScStableCodeReferenceElement
import com.intellij.util.io.StringRef
import com.intellij.util.PatchedSoftReference
import api.base.types.ScTypeElement
import psi.impl.ScalaPsiElementFactory

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.06.2009
 */

class ScAnnotationStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement])
        extends StubBaseWrapper[ScAnnotation](parent, elemType) with ScAnnotationStub {
  var name: StringRef = StringRef.fromString("")
  private var typeText: StringRef = _
  private var myTypeElement: PatchedSoftReference[ScTypeElement] = null

  def this(parent : StubElement[ParentPsi],
          elemType : IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
          name: StringRef, typeText: StringRef) {
    this (parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.name = name
    this.typeText = typeText
  }

  def getName: String = StringRef.toString(name)
  def getTypeText: String = StringRef.toString(typeText)
  def getTypeElement: ScTypeElement = {
    if (myTypeElement != null && myTypeElement.get != null) return myTypeElement.get
    val res: ScTypeElement = {
        ScalaPsiElementFactory.createTypeElementFromText(getTypeText, getPsi, null)
    }
    myTypeElement = new PatchedSoftReference[ScTypeElement](res)
    res
  }
}