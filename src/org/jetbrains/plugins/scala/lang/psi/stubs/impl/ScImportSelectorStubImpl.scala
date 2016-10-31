package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import com.intellij.util.SofterReference
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelector
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createReferenceFromText
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.MaybeStringRefExt

/**
  * User: Alexander Podkhalyuzin
  * Date: 20.06.2009
  */
class ScImportSelectorStubImpl(parent: StubElement[_ <: PsiElement],
                               elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                               private val referenceTextRef: Option[StringRef],
                               private val importedNameRef: Option[StringRef],
                               val isAliasedImport: Boolean)
  extends StubBase[ScImportSelector](parent, elementType) with ScImportSelectorStub with PsiOwner[ScImportSelector] {

  private var referenceReference: SofterReference[Option[ScStableCodeReferenceElement]] = null

  override def referenceText: Option[String] = referenceTextRef.asString

  override def reference: Option[ScStableCodeReferenceElement] = {
    referenceReference = updateOptionalReference(referenceReference) {
      case (context, child) =>
        referenceText.map {
          createReferenceFromText(_, context, child)
        }
    }
    referenceReference.get
  }

  override def importedName: Option[String] = importedNameRef.asString
}