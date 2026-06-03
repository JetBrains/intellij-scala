package org.jetbrains.plugins.scala.lang.psi.api

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner.IMPLICIT_ARGS_KEY
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.ImplicitArgumentsClause
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter

// TODO Implement selectively, not by ScExpression
trait ImplicitArgumentsOwner extends ScalaPsiElement {

  private[psi] final def setImplicitArguments(results: Seq[ImplicitArgumentsClause]): Unit =
    putUserData(IMPLICIT_ARGS_KEY, results)

  //todo: get rid of side-effect-driven logic
  def findImplicitArguments: Seq[ImplicitArgumentsClause] = {
    ProgressManager.checkCanceled()
    updateImplicitArguments()
    val fromUserData = getUserData(IMPLICIT_ARGS_KEY)

    if (fromUserData eq null) Seq.empty
    else                      fromUserData
  }

  //calculation which may set implicit arguments as a side effect, typically computation of a type
  protected def updateImplicitArguments(): Unit

  /**
   * @return Sequence of mappings between arguments and parameters in the order of the arguments' appearance in code.
   */
  def matchedParameters: Seq[(ScExpression, Parameter)] = Seq.empty

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
  private val IMPLICIT_ARGS_KEY: Key[Seq[ImplicitArgumentsClause]] =
    Key.create[Seq[ImplicitArgumentsClause]]("scala.implicit.arguments")

  def unapply(e: ImplicitArgumentsOwner): Option[Seq[ImplicitArgumentsClause]] =
    Option(e.findImplicitArguments)
}
