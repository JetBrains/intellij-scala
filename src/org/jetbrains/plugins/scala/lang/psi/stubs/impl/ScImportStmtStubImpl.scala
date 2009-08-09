package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import api.base.ScModifierList
import api.toplevel.imports.ScImportStmt
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IStubElementType}
import com.intellij.util.io.StringRef
/**
 * User: Alexander Podkhalyuzin
 * Date: 18.06.2009
 */

class ScImportStmtStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                          elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement])
        extends StubBaseWrapper[ScImportStmt](parent, elemType) with ScImportStmtStub {
  private var importText: StringRef = _

  def this(parent : StubElement[ParentPsi],
          elemType : IStubElementType[_ <: StubElement[_], _ <: PsiElement],
          importText: String) {
    this (parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.importText = StringRef.fromString(importText)
  }


  def getImportText: String = StringRef.toString(importText)
}