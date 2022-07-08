package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import com.intellij.util.SofterReference
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createReferenceFromText

class ScImportExprStubImpl(parent: StubElement[_ <: PsiElement],
                           elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                           override val referenceText: Option[String],
                           override val hasWildcardSelector: Boolean,
                           override val hasGivenSelector: Boolean)
  extends StubBase[ScImportExpr](parent, elementType) with ScImportExprStub with PsiOwner[ScImportExpr] {

  private var referenceReference: SofterReference[Option[ScStableCodeReference]] = null

  override def reference: Option[ScStableCodeReference] = {
    getFromOptionalReference(referenceReference) {
      case (context, child) =>
        referenceText.map {
          createReferenceFromText(_, context, child)
        }
    } (referenceReference = _)
  }
}