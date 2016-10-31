package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import com.intellij.util.SofterReference
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScIdList, ScPatternList}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScValueOrVariableStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.{MaybeStringRefExt, StringRefArrayExt}

/**
  * @author adkozlov
  */
class ScValueOrVariableStubImpl[V <: ScValueOrVariable](parent: StubElement[_ <: PsiElement],
                                                        elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                                                        val isDeclaration: Boolean,
                                                        private val namesRefs: Array[StringRef],
                                                        protected[impl] val typeTextRef: Option[StringRef],
                                                        protected[impl] val bodyTextRef: Option[StringRef],
                                                        private val containerTextRef: Option[StringRef],
                                                        val isLocal: Boolean)
  extends StubBase[V](parent, elementType) with ScValueOrVariableStub[V]
    with ScTypeElementOwnerStub[V] with ScExpressionOwnerStub[V] {

  private var idsContainerReference: SofterReference[Option[ScIdList]] = null
  private var patternsContainerReference: SofterReference[Option[ScPatternList]] = null

  def names: Array[String] = namesRefs.asStrings

  def bindingsContainerText: Option[String] = containerTextRef.asString

  def patternsContainer: Option[ScPatternList] = {
    if (isDeclaration) return None

    patternsContainerReference = updateOptionalReference(patternsContainerReference) {
      case (context, child) =>
        bindingsContainerText.map {
          createPatterListFromText(_, context, child)
        }
    }
    patternsContainerReference.get
  }

  def idsContainer: Option[ScIdList] = {
    if (!isDeclaration) return None

    idsContainerReference = updateOptionalReference(idsContainerReference) {
      case (context, child) =>
        bindingsContainerText.map {
          createIdsListFromText(_, context, child)
        }
    }
    idsContainerReference.get
  }
}
