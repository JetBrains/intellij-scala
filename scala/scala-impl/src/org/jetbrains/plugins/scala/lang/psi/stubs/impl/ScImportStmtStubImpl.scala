package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt

/**
  * User: Alexander Podkhalyuzin
  * Date: 18.06.2009
  */
class ScImportStmtStubImpl(parent: StubElement[_ <: PsiElement],
                           elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                           private val importTextRef: StringRef)
  extends StubBase[ScImportStmt](parent, elementType) with ScImportStmtStub {
  def importText: String = StringRef.toString(importTextRef)
}