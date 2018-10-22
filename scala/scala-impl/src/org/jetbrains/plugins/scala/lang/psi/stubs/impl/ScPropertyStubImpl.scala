package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import com.intellij.util.SofterReference
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScIdList, ScPatternList}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.StringRefArrayExt

/**
  * @author adkozlov
  */
final class ScPropertyStubImpl[P <: ScValueOrVariable](parent: StubElement[_ <: PsiElement],
                                                       elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                                                       override val isDeclaration: Boolean,
                                                       override val isImplicit: Boolean,
                                                       private val namesRefs: Array[StringRef],
                                                       protected[impl] val typeTextRef: Option[StringRef],
                                                       protected[impl] val bodyTextRef: Option[StringRef],
                                                       private val containerTextRef: StringRef,
                                                       override val isLocal: Boolean)
  extends StubBase[P](parent, elementType) with ScPropertyStub[P] {

  private var idsContainerReference: SofterReference[Option[ScIdList]] = null
  private var patternsContainerReference: SofterReference[Option[ScPatternList]] = null

  def names: Array[String] = namesRefs.asStrings

  def bindingsContainerText: String = containerTextRef.getString

  def patternsContainer: Option[ScPatternList] = {
    if (isDeclaration) return None

    getFromOptionalReference(patternsContainerReference) {
      case (context, child) => createPatterListFromText(bindingsContainerText, context, child)
    }(patternsContainerReference = _)
  }

  def idsContainer: Option[ScIdList] = {
    if (!isDeclaration) return None

    getFromOptionalReference(idsContainerReference) {
      case (context, child) => createIdsListFromText(bindingsContainerText, context, child)
    }(idsContainerReference = _)
  }
}
