package org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.specialSupport

import com.intellij.codeInspection.dataFlow.java.inst.EnsureIndexInBoundsInstruction
import com.intellij.codeInspection.dataFlow.jvm.SpecialField
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.ScalaCollectionAccessProblem
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilder
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations.{ExpressionTransformer, Transformable}
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.InvocationInfo
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaConstants.Packages._
import org.jetbrains.plugins.scala.lang.dfa.utils.SyntheticExpressionFactory.createIntegerLiteralExpression
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

object CollectionAccessAssertions {

  final case class CollectionAccessAssertion(index: Transformable, exceptionName: String)

  def addCollectionAccessAssertions(invocationExpression: ScExpression,
                                    invocationInfo: InvocationInfo, builder: ScalaDfaControlFlowBuilder): Unit = {
    for (CollectionAccessAssertion(accessedIndex, exceptionName) <- findAccessAssertion(invocationInfo)) {
      for (thisArgument <- invocationInfo.thisArgument) {
        thisArgument.content.transform(builder)
        accessedIndex.transform(builder)

        val problem = ScalaCollectionAccessProblem(SpecialField.COLLECTION_SIZE, invocationExpression, exceptionName)
        val transfer = builder.maybeTransferValue(exceptionName)
        builder.addInstruction(new EnsureIndexInBoundsInstruction(problem, transfer.orNull))
      }
    }
  }

  private def findAccessAssertion(invocationInfo: InvocationInfo): Option[CollectionAccessAssertion] = {
    val accessInfo = for {
      invokedElement <- invocationInfo.invokedElement
      invokedName <- invokedElement.qualifiedName
    } yield {
      implicit val context: Project = invokedElement.psiElement.getProject
      val properArgs = invocationInfo.properArguments.flatten

      invokedName match {
        case name if !name.startsWith(ScalaCollection) => None
        case name if name.endsWith("head") => Some(CollectionAccessAssertion(indexZero, NoSuchElementExceptionName))
        case name if name.endsWith("LinearSeqOptimized.apply") || name.endsWith("LinearSeqOps.apply") || name.endsWith("IterableOps.apply") =>
          singleProperArgument(properArgs).map(arg => CollectionAccessAssertion(arg.content, IndexOutOfBoundsExceptionName))
        case _ => None
      }
    }

    accessInfo.flatten
  }

  private def indexZero(implicit context: Project): Transformable = {
    new ExpressionTransformer(createIntegerLiteralExpression(0))
  }

  private def singleProperArgument(properArgs: List[Argument]): Option[Argument] = properArgs match {
    case List(singleArg) => Some(singleArg)
    case _ => None
  }
}
