package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl


import api.base.ScStableCodeReferenceElement
import api.toplevel.imports.ScImportExpr
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import com.intellij.util.io.StringRef
import psi.impl.ScalaPsiElementFactory
import com.intellij.reference.SoftReference

/**
 * User: Alexander Podkhalyuzin
 * Date: 20.06.2009
 */

class ScImportExprStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement])
  extends StubBaseWrapper[ScImportExpr](parent, elemType) with ScImportExprStub {

  var referenceText: StringRef = StringRef.fromString("")
  var singleWildcard: Boolean = _
  private var myReference: SoftReference[Option[ScStableCodeReferenceElement]] = null

  def this(parent : StubElement[ParentPsi],
          elemType : IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement], refText: String, singleWildcard: Boolean) {
    this (parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    referenceText = StringRef.fromString(refText)
    this.singleWildcard = singleWildcard
  }

  def reference: Option[ScStableCodeReferenceElement] = {
    if (myReference != null && myReference.get != null) return myReference.get
    val res = if (referenceText == StringRef.fromString("")) {
      None
    } else {
      val psi = ScalaPsiElementFactory.createReferenceFromText(StringRef.toString(referenceText), getPsi, null)
      if (psi != null) {
        Some(psi)
      } else None
    }
    myReference = new SoftReference[Option[ScStableCodeReferenceElement]](res)
    res
  }


  def isSingleWildcard: Boolean = singleWildcard
}