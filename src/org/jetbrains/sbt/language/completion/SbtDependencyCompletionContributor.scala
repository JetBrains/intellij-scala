package org.jetbrains.sbt
package language.completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionContributor
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScLiteralImpl
import org.jetbrains.sbt.resolvers.{SbtResolverIndexesManager, SbtResolverUtils}

/**
 * @author Nikolay Obedin
 * @since 7/31/14.
 */
class SbtDependencyCompletionContributor extends ScalaCompletionContributor {

  val insideInfixExpr = PlatformPatterns.psiElement().withSuperParent(2, classOf[ScInfixExpr])

  extend(CompletionType.BASIC, insideInfixExpr, new CompletionProvider[CompletionParameters] {
    override def addCompletions(parameters: CompletionParameters, context: ProcessingContext, results: CompletionResultSet) {
      if (parameters.getOriginalFile.getFileType.getName != Sbt.Name) return

      val place = positionFromParameters(parameters)
      val infixExpr = place.getContext.getContext.asInstanceOf[ScInfixExpr]

      val resolversToUse = SbtResolverUtils.getProjectResolvers(Option(ScalaPsiUtil.fileContext(place)))
      val indexManager = SbtResolverIndexesManager()
      val indexes = resolversToUse.flatMap(indexManager.find).toSet
      if (indexes.isEmpty) return

      def addResult(result: String) = results.addElement(LookupElementBuilder.create(result))

      def completeGroup(artifact: String) = {
        indexes foreach { index =>
          if (artifact.nonEmpty)
            index.groups(artifact) foreach addResult
          else
            index.groups foreach addResult
        }
        results.stopHere()
      }

      def completeArtifact(group: String) = {
        indexes foreach { index =>
          if (group.nonEmpty)
            index.artifacts(group) foreach addResult
          else
            index.groups flatMap index.artifacts foreach addResult
        }
        results.stopHere()
      }

      def completeVersion(group: String, artifact: String): Unit = {
        if (group.isEmpty || artifact.isEmpty) return
        indexes foreach (_.versions(group, artifact) foreach addResult)
        results.stopHere()
      }

      def isValidOperation(operation: String) = operation == "%" || operation == "%%"

      (infixExpr.lOp, infixExpr.operation, infixExpr.rOp) match {
        case (lop, oper, ScLiteralImpl.string(artifact))
          if lop == place.getContext && isValidOperation(oper.getText) =>
            completeGroup(artifact)
        case (ScLiteralImpl.string(group), oper, rop)
          if rop == place.getContext && isValidOperation(oper.getText) =>
            completeArtifact(group)
        case (ScInfixExpr(llop, loper, lrop), oper, rop)
          if rop == place.getContext && oper.getText == "%" && isValidOperation(loper.getText) =>
            for {
              ScLiteralImpl.string(group) <- Option(llop)
              ScLiteralImpl.string(artifact) <- Option(lrop)
            } yield completeVersion(group, artifact)
        case _ => // do nothing
      }
    }
  })
}

