package org.jetbrains.plugins.scala
package lang
package psi
package uast
package declarations

import java.util

import com.intellij.psi.{PsiCodeBlock, PsiMethod, PsiNameIdentifierOwner, PsiType}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.base.ScMethodLike
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlock
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.{ScUAnchorOwner, ScUAnnotated, ScUElement}
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.expressions.ScUImplicitBlockExpression
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.uast.{UAnchorOwner, UExpression, UIdentifier, UMethod, UMethodAdapter, UParameter}

import scala.jdk.CollectionConverters._

/**
  * [[ScMethodLike]] adapter for the [[UMethod]]
  *
  * @param scElement Scala PSI element representing function (e.g. method, secondary constructors)
  */
final class ScUMethod(override protected val scElement: ScMethodLike,
                      override protected val parent: LazyUElement)
    extends UMethodAdapter(scElement)
    with ScUElement
    with UAnchorOwner
    with ScUAnnotated {

  override type PsiFacade = PsiMethod

  // TODO: separate primary constructor and add body conversion for it
  @Nullable
  override def getUastBody: UExpression = scElement match {
    case funDef: ScFunctionDefinition =>
      funDef.body.collect {
        case block: ScBlock => block.convertToUExpressionOrEmpty(this)
        case expressionBody =>
          ScUImplicitBlockExpression.convertAndWrapIntoBlock(
            expressionBody,
            LazyUElement.just(this)
          )
      }.orNull
    case _ => null
  }

  override def getUastParameters: util.List[UParameter] =
    scElement.parameters.flatMap(_.convertTo[UParameter](this)).asJava

  @Nullable
  override def getBody: PsiCodeBlock = scElement.getBody

  @Nullable
  override def getReturnType: PsiType = scElement match {
    case funDef: ScFunctionDefinition =>
      val rt = funDef.returnType
      rt.map(_.toPsiType).getOrElse(createUErrorType())
    case _ =>
      scElement.getReturnType
  }

  override def isConstructor: Boolean = scElement.isConstructor

  @Nullable
  override def getUastAnchor: UIdentifier = getSourcePsi match {
    case named: ScNamedElement =>
      createUIdentifier(named.nameId, this)
    case nameId: PsiNameIdentifierOwner =>
      createUIdentifier(nameId.getNameIdentifier, this)
    case otherwise =>
      Option(otherwise.getNavigationElement)
        .map(createUIdentifier(_, this))
        .orNull
  }
}
