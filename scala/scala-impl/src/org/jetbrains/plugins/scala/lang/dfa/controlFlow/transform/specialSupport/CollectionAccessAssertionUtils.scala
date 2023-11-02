package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.specialSupport

import com.intellij.codeInspection.dataFlow.jvm.SpecialField
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.{ScalaCollectionAccessProblem, ScalaDfaProblemKind}
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilder
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.ResultReq
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.specialSupport.CollectionAccessAssertionUtils.CollectionAccessAssertion
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.InvocationInfo
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaConstants.Exceptions.{IndexOutOfBoundsExceptionName, NoSuchElementExceptionName}
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaConstants.Packages.ScalaCollection
import org.jetbrains.plugins.scala.lang.dfa.utils.SyntheticExpressionFactory.createIntegerLiteralExpression
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.project.ProjectContext

trait CollectionAccessAssertionUtils { this: ScalaDfaControlFlowBuilder =>
  def buildCollectionAccessAssertions(invocationInfo: InvocationInfo): Unit = {
    for (CollectionAccessAssertion(accessedIndex, exceptionName, problemKind) <- findAccessAssertion(invocationInfo)) {
      for (thisArgument <- invocationInfo.thisArgument) {
        val container = transformExpression(thisArgument.content, ResultReq.Required)
        val index = transformExpression(accessedIndex, ResultReq.Required)

        val problem = ScalaCollectionAccessProblem(SpecialField.COLLECTION_SIZE, invocationInfo.place, problemKind)
        ensureInBounds(container, index, exceptionName, problem)
      }
    }
  }

  private def findAccessAssertion(invocationInfo: InvocationInfo): Option[CollectionAccessAssertion] = {
    val accessInfo = for {
      invokedElement <- invocationInfo.invokedElement
      invokedName <- invokedElement.qualifiedName
    } yield {
      implicit val context: ProjectContext = invokedElement.psiElement.getProject
      val properArgs = invocationInfo.properArguments.flatten

      invokedName match {
        case name if !name.startsWith(ScalaCollection) => None
        case name if name.endsWith("head") =>
          Some(CollectionAccessAssertion(Some(indexZero), NoSuchElementExceptionName, ScalaCollectionAccessProblem.noSuchElementProblem))
        case name if name.endsWith("LinearSeqOptimized.apply") || name.endsWith("LinearSeqOps.apply") || name.endsWith("IterableOps.apply") =>
          singleProperArgument(properArgs)
            .map(arg => CollectionAccessAssertion(arg.content, IndexOutOfBoundsExceptionName, ScalaCollectionAccessProblem.indexOutOfBoundsProblem))
        case _ => None
      }
    }

    accessInfo.flatten
  }

  private def indexZero(implicit context: ProjectContext): ScExpression = {
    createIntegerLiteralExpression(0)
  }

  private def singleProperArgument(properArgs: List[Argument]): Option[Argument] = properArgs match {
    case List(singleArg) => Some(singleArg)
    case _ => None
  }
}

object CollectionAccessAssertionUtils {
  final case class CollectionAccessAssertion(index: Option[ScExpression],
                                             exceptionName: String,
                                             problemKind: ScalaDfaProblemKind[ScalaCollectionAccessProblem])
}