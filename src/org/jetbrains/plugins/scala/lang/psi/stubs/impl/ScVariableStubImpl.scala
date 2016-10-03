package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl


import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import com.intellij.util.SofterReference
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScIdList, ScPatternList}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariable
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.{MaybeStringRefExt, StringRefArrayExt, StubBaseExt}

/**
 * User: Alexander Podkhalyuzin
 * Date: 18.10.2008
 */

class ScVariableStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                                                  val isDeclaration: Boolean,
                                                  private val namesRefs: Array[StringRef],
                                                  private val typeTextRef: Option[StringRef],
                                                  private val bodyTextRef: Option[StringRef],
                                                  private val containerTextRef: Option[StringRef],
                                                  val isLocal: Boolean)
  extends StubBase[ScVariable](parent, elementType) with ScVariableStub {

  private var typeElementReference: SofterReference[Option[ScTypeElement]] = null
  private var bodyExpressionReference: SofterReference[Option[ScExpression]] = null
  private var idsContainerReference: SofterReference[Option[ScIdList]] = null
  private var patternsContainerReference: SofterReference[Option[ScPatternList]] = null

  def names: Array[String] = namesRefs.asStrings

  def typeText: Option[String] = typeTextRef.asString

  def bodyText: Option[String] = bodyTextRef.asString

  def bindingsContainerText: Option[String] = containerTextRef.asString

  def typeElement: Option[ScTypeElement] = {
    typeElementReference = this.updateOptionalReference(typeElementReference) {
      case (context, child) =>
        typeText.map {
          createTypeElementFromText(_, context, child)
        }
    }
    typeElementReference.get
  }

  def bodyExpression: Option[ScExpression] = {
    bodyExpressionReference = this.updateOptionalReference(bodyExpressionReference) {
      case (context, child) =>
        bodyText.map {
          createExpressionWithContextFromText(_, context, child)
        }
    }
    bodyExpressionReference.get
  }

  def patternsContainer: Option[ScPatternList] = {
    if (isDeclaration) return None

    patternsContainerReference = this.updateOptionalReference(patternsContainerReference) {
      case (context, child) =>
        bindingsContainerText.map {
          createPatterListFromText(_, context, child)
        }
    }
    patternsContainerReference.get
  }

  def idsContainer: Option[ScIdList] = {
    if (!isDeclaration) return None

    idsContainerReference = this.updateOptionalReference(idsContainerReference) {
      case (context, child) =>
        bindingsContainerText.map {
          createIdsListFromText(_, context, child)
        }
    }
    idsContainerReference.get
  }
}