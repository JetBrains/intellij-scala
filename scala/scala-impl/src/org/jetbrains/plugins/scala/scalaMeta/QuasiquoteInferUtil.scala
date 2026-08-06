package org.jetbrains.plugins.scala.scalaMeta

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.base.patterns.ScInterpolationPatternImpl
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

trait QuasiquoteInferUtilApi {
  def isMetaQQ(ref: ScReference): Boolean
  def isMetaQQ(fun: ScFunction): Boolean
  def getMetaQQExpectedTypes(srr: ScalaResolveResult, stringContextApplicationRef: ScReferenceExpression): Seq[Parameter]
  def getMetaQQExprType(pat: ScInterpolatedStringLiteral): TypeResult
  def getMetaQQPatternTypes(pat: ScInterpolationPatternImpl): Seq[String]
}

object QuasiquoteInferUtil extends QuasiquoteInferUtilApi {
  override def isMetaQQ(ref: ScReference): Boolean =
    ScalaMetaApi.instance.isMetaQQ(ref)
  override def isMetaQQ(fun: ScFunction): Boolean =
    ScalaMetaApi.instance.isMetaQQ(fun)
  override def getMetaQQExpectedTypes(srr: ScalaResolveResult, stringContextApplicationRef: ScReferenceExpression): Seq[Parameter] =
    ScalaMetaApi.instance.getMetaQQExpectedTypes(srr, stringContextApplicationRef)
  override def getMetaQQExprType(pat: ScInterpolatedStringLiteral): TypeResult =
    ScalaMetaApi.instance.getMetaQQExprType(pat)
  override def getMetaQQPatternTypes(pat: ScInterpolationPatternImpl): Seq[String] =
    ScalaMetaApi.instance.getMetaQQPatternTypes(pat)
}
