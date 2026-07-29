package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubBase, StubElement}
import com.intellij.psi.tree.IElementType
import com.intellij.util.SofterReference
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createReferenceFromText
import org.jetbrains.plugins.scala.lang.psi.stubs.ScImportExprStub

class ScImportExprStubImpl(parent: StubElement[_ <: PsiElement],
                           elementType: IElementType,
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
