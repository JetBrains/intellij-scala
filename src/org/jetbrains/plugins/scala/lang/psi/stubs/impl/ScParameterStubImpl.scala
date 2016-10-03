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
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createExpressionWithContextFromText, createTypeElementFromText}

/**
  * User: Alexander Podkhalyuzin
  * Date: 19.10.2008
  */

class ScParameterStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                   elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                                                   private val nameRef: StringRef,
                                                   private val typeTextRef: StringRef,
                                                   val isStable: Boolean,
                                                   val isDefaultParameter: Boolean,
                                                   val isRepeated: Boolean,
                                                   val isVal: Boolean,
                                                   val isVar: Boolean,
                                                   val isCallByNameParameter: Boolean,
                                                   private val defaultExprTextRef: Option[StringRef],
                                                   private val deprecatedNameRef: Option[StringRef])
  extends StubBase[ScParameter](parent, elementType) with ScParameterStub {

  private var myTypeElement: SofterReference[Option[ScTypeElement]] = null
  private var myDefaultExpression: SofterReference[Option[ScExpression]] = null

  override def getName: String = StringRef.toString(nameRef)

  override def typeText: String = StringRef.toString(typeTextRef)

  override def defaultExprText: Option[String] = defaultExprTextRef.map {
    StringRef.toString
  }

  override def deprecatedName: Option[String] = deprecatedNameRef.map {
    StringRef.toString
  }

  def typeElement: Option[ScTypeElement] = {
    if (myTypeElement != null) {
      myTypeElement.get match {
        case null =>
        case None => return None
        case result@Some(element) if element.getContext eq getPsi => return result
      }
    }

    val result = Option(typeText).filter {
      _.nonEmpty
    }.map {
      createTypeElementFromText(_, getPsi, null)
    }
    myTypeElement = new SofterReference[Option[ScTypeElement]](result)
    result
  }

  def defaultExpr: Option[ScExpression] = {
    if (myDefaultExpression != null) {
      myDefaultExpression.get match {
        case null =>
        case None => return None
        case result@Some(expression) if expression.getContext eq getPsi => return result
      }
    }

    val result = defaultExprText.filter {
      _.nonEmpty
    }.map {
      createExpressionWithContextFromText(_, getPsi, null)
    }
    myDefaultExpression = new SofterReference[Option[ScExpression]](result)
    result
  }
}