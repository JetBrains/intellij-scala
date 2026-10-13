package org.jetbrains.plugins.scala.lang.psi.api

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner.{IMPLICIT_ARGS_KEY, ImplicitArguments}
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.ImplicitArgumentsClause
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter

// TODO Implement selectively, not by ScExpression
trait ImplicitArgumentsOwner extends ScalaPsiElement {

  /**
   * @param expectedType the expected type of the computation the implicit arguments were found by.
   *                     An expression may be typed against several expected types (e.g. as an argument of an
   *                     overloaded method, against the parameter types of each of the alternatives), while only the
   *                     implicit arguments found for the expected type of the expression itself are reported by
   *                     [[findImplicitArguments]], see [[updateImplicitArguments]].
   */
  private[psi] final def setImplicitArguments(
    results:      Seq[ImplicitArgumentsClause],
    expectedType: Option[ScType] = None
  ): Unit = {
    val modificationCount = BlockModificationTracker(this).getModificationCount

    val byExpectedType = getUserData(IMPLICIT_ARGS_KEY) match {
      case ImplicitArguments(`modificationCount`, previous, _) => previous
      case _                                                   => Map.empty[Option[ScType], Seq[ImplicitArgumentsClause]]
    }

    putUserData(IMPLICIT_ARGS_KEY, ImplicitArguments(modificationCount, byExpectedType.updated(expectedType, results), results))
  }

  //todo: get rid of side-effect-driven logic
  def findImplicitArguments: Seq[ImplicitArgumentsClause] = {
    ProgressManager.checkCanceled()
    val expectedType = updateImplicitArguments()
    val fromUserData = getUserData(IMPLICIT_ARGS_KEY)

    if (fromUserData eq null) Seq.empty
    else                      fromUserData.byExpectedType.getOrElse(expectedType, fromUserData.last)
  }

  /**
   * Calculation which may set implicit arguments as a side effect, typically computation of a type.
   *
   * @return the expected type the calculation was performed with, see [[setImplicitArguments]]
   */
  protected def updateImplicitArguments(): Option[ScType]

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

  /**
   * @param modificationCount the modification count, the implicit arguments were found at
   * @param byExpectedType    the implicit arguments found for different expected types, see [[ImplicitArgumentsOwner.setImplicitArguments]]
   * @param last              the implicit arguments found last
   */
  private final case class ImplicitArguments(
    modificationCount: Long,
    byExpectedType:    Map[Option[ScType], Seq[ImplicitArgumentsClause]],
    last:              Seq[ImplicitArgumentsClause]
  )

  private val IMPLICIT_ARGS_KEY: Key[ImplicitArguments] =
    Key.create[ImplicitArguments]("scala.implicit.arguments")

  def unapply(e: ImplicitArgumentsOwner): Option[Seq[ImplicitArgumentsClause]] =
    Option(e.findImplicitArguments)
}
