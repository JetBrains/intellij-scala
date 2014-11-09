package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl


import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import com.intellij.reference.SoftReference
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelector
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * User: Alexander Podkhalyuzin
 * Date: 20.06.2009
 */

class ScImportSelectorStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement])
  extends StubBaseWrapper[ScImportSelector](parent, elemType) with ScImportSelectorStub {
  var referenceText: StringRef = _
  var name: StringRef = _
  private var myReference: SoftReference[ScStableCodeReferenceElement] = new SoftReference[ScStableCodeReferenceElement](null)
  var aliasImport: Boolean = false

  def this(parent : StubElement[ParentPsi],
          elemType : IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement], refText: String,
          importedName: String, isAliasedImport: Boolean) {
    this (parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.referenceText = StringRef.fromString(refText)
    this.name = StringRef.fromString(importedName)
    this.aliasImport = isAliasedImport
  }

  def isAliasedImport: Boolean = aliasImport

  def reference: ScStableCodeReferenceElement = {
    val referenceElement = myReference.get
    if (referenceElement != null && (referenceElement.getContext eq getPsi)) return myReference.get
    val res =
      if (referenceText == StringRef.fromString("")) null
      else ScalaPsiElementFactory.createReferenceFromText(StringRef.toString(referenceText), getPsi, null)
    myReference = new SoftReference[ScStableCodeReferenceElement](res)
    res
  }

  def importedName: String = StringRef.toString(name)
}