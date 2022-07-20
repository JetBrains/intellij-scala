package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import com.intellij.util.SofterReference
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructorInvocation
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateParents
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createConstructorFromText

import scala.collection.immutable.ArraySeq


final class ScTemplateParentsStubImpl(
  parent:                         StubElement[_ <: PsiElement],
  elementType:                    IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
  override val parentClausesText: Array[String]
) extends StubBase[ScTemplateParents](parent, elementType)
    with ScTemplateParentsStub
    with PsiOwner[ScTemplateParents] {

  private var constructorAndParentTypeElementsReference: SofterReference[Seq[ScConstructorInvocation]] = _

  override def parentClauses: Seq[ScConstructorInvocation] =
    getFromReference(constructorAndParentTypeElementsReference) { case (context, child) =>
        val parentElems = parentClausesText.map(
          createConstructorFromText(_, context, child)
        )

        ArraySeq.unsafeWrapArray(parentElems)
      } (constructorAndParentTypeElementsReference = _)
}