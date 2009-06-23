package org.jetbrains.plugins.scala.lang.psi.stubs.impl


import api.base.ScStableCodeReferenceElement
import api.toplevel.imports.ScImportExpr
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import com.intellij.util.io.StringRef
import com.intellij.util.PatchedSoftReference
import psi.impl.ScalaPsiElementFactory
/**
 * User: Alexander Podkhalyuzin
 * Date: 20.06.2009
 */

class ScImportExprStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement])
  extends StubBaseWrapper[ScImportExpr](parent, elemType) with ScImportExprStub {

  var referenceText: StringRef = StringRef.fromString("")
  var singleWildcard: Boolean = _
  private var myReference: PatchedSoftReference[Option[ScStableCodeReferenceElement]] = null

  def this(parent : StubElement[ParentPsi],
          elemType : IStubElementType[_ <: StubElement[_], _ <: PsiElement], refText: String, singleWildcard: Boolean) {
    this (parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    referenceText = StringRef.fromString(refText)
    this.singleWildcard = singleWildcard
  }

  def reference: Option[ScStableCodeReferenceElement] = {
    if (myReference != null && myReference.get != null) return myReference.get
    val res = if (referenceText == "") {
      None
    } else {
      val psi = ScalaPsiElementFactory.createReferenceFromText(StringRef.toString(referenceText), getPsi)
      if (psi != null) {
        Some(psi)
      } else None
    }
    myReference = new PatchedSoftReference[Option[ScStableCodeReferenceElement]](res)
    res
  }


  def isSingleWildcard: Boolean = singleWildcard
}