package org.jetbrains.plugins.scala
package lang.psi.api

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt, TraversableExt}
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner.IMPLICIT_ARGS_KEY
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
 * Nikolay.Tropin
 * 2014-10-17
 */
// TODO Implement selectively, not by ScExpression
trait ImplicitArgumentsOwner extends ScalaPsiElement {

  private[psi] final def setImplicitArguments(results: Option[collection.Seq[ScalaResolveResult]]): Unit = {
    putUserData(IMPLICIT_ARGS_KEY, results.orNull)
  }

  //todo: get rid of side-effect-driven logic
  def findImplicitArguments: Option[collection.Seq[ScalaResolveResult]] = {
    ProgressManager.checkCanceled()

    updateImplicitArguments()

    getUserData(IMPLICIT_ARGS_KEY).toOption
  }

  //calculation which may set implicit arguments as a side effect, typically computation of a type
  protected def updateImplicitArguments(): Unit

  def matchedParameters: collection.Seq[(ScExpression, Parameter)] = Seq.empty

  def explicitImplicitArgList: Option[ScArgumentExprList] = {
    val implicitArg = matchedParameters.collectFirst {
      case (arg, param) if param.isImplicit => arg
    }
    implicitArg.toSeq
      .flatMap(_.parentsInFile.take(2)) //argument or rhs of a named argument
      .filterByType[ScArgumentExprList]
      .headOption
  }
}

object ImplicitArgumentsOwner {
  private val IMPLICIT_ARGS_KEY: Key[collection.Seq[ScalaResolveResult]] = Key.create[collection.Seq[ScalaResolveResult]]("scala.implicit.arguments")

  def unapply(e: ImplicitArgumentsOwner): Option[collection.Seq[ScalaResolveResult]] = e.findImplicitArguments
}
