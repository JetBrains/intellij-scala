package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import com.intellij.util.SofterReference
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelector
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createReferenceFromText

/**
  * User: Alexander Podkhalyuzin
  * Date: 20.06.2009
  */
class ScImportSelectorStubImpl(parent: StubElement[_ <: PsiElement],
                               elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                               override val referenceText: Option[String],
                               override val importedName: Option[String],
                               override val isAliasedImport: Boolean,
                               override val isWildcardSelector: Boolean,
                               override val isGivenSelector: Boolean,
                               override val typeText: Option[String])
  extends StubBase[ScImportSelector](parent, elementType) with ScImportSelectorStub with PsiOwner[ScImportSelector] {

  private var referenceReference: SofterReference[Option[ScStableCodeReference]] = _

  override def reference: Option[ScStableCodeReference] = {
    getFromOptionalReference(referenceReference) {
      case (context, child) =>
        referenceText.map {
          createReferenceFromText(_, context, child)
        }
    } (referenceReference = _)
  }
}