package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl


import api.base.ScStableCodeReferenceElement
import psi.impl.ScalaPsiElementFactory
import api.toplevel.imports.ScImportSelector
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import com.intellij.util.io.StringRef
import com.intellij.reference.SoftReference

/**
 * User: Alexander Podkhalyuzin
 * Date: 20.06.2009
 */

class ScImportSelectorStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement])
extends StubBaseWrapper[ScImportSelector](parent, elemType) with ScImportSelectorStub {
  var referenceText: StringRef = _
  var name: StringRef = _
  private var myReference: SoftReference[ScStableCodeReferenceElement] = null
  var aliasImport: Boolean = false

  def this(parent : StubElement[ParentPsi],
          elemType : IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement], refText: String,
          importedName: String, isAliasImport: Boolean) {
    this (parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.referenceText = StringRef.fromString(refText)
    this.name = StringRef.fromString(importedName)
    this.aliasImport = isAliasImport
  }

  def isAliasImport: Boolean = aliasImport

  def reference: ScStableCodeReferenceElement = {
    if (myReference != null && myReference.get != null) return myReference.get
    val res = if (referenceText == "") {
      null
    } else {
      ScalaPsiElementFactory.createReferenceFromText(StringRef.toString(referenceText), getPsi, null)
    }
    myReference = new SoftReference[ScStableCodeReferenceElement](res)
    res
  }

  def importedName: String = StringRef.toString(name)
}