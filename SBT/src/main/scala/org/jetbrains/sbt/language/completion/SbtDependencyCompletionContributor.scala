package org.jetbrains.sbt
package language.completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.module.ModuleManager
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScLiteralImpl
import org.jetbrains.sbt.project.module.SbtModule
import org.jetbrains.sbt.resolvers.SbtResolverIndexesManager

/**
 * @author Nikolay Obedin
 * @since 7/31/14.
 */
class SbtDependencyCompletionContributor extends CompletionContributor {

  val insideInfixExpr = PlatformPatterns.psiElement().withSuperParent(2, classOf[ScInfixExpr])

  extend(CompletionType.BASIC, insideInfixExpr, new CompletionProvider[CompletionParameters] {
    override def addCompletions(parameters: CompletionParameters, context: ProcessingContext, results: CompletionResultSet) {
      if (parameters.getOriginalFile.getFileType.getName != Sbt.Name) return

      val place  = parameters.getPosition
      val infixExpr = place.getParent.getParent.asInstanceOf[ScInfixExpr]

      val moduleManager = Option(ScalaPsiUtil.fileContext(place)).map { f => ModuleManager.getInstance(f.getProject) }
      val resolversToUse = moduleManager.map { manager =>
        manager.getModules.toSeq.flatMap(SbtModule.getResolversFrom)
      }.getOrElse(Seq.empty)
      if (resolversToUse.isEmpty) return

      val indexManager = SbtResolverIndexesManager()
      val indexes = resolversToUse.flatMap(indexManager.find).toSet

      def addResult(result: String) = results.addElement(LookupElementBuilder.create(result))

      def completeGroup(artifact: Option[String] = None) = {
        indexes foreach { index =>
          artifact match {
            case Some(artifactId) if artifactId.nonEmpty => index.groups(artifactId) foreach addResult
            case _ => index.groups foreach addResult
          }
        }
        results.stopHere()
      }

      def completeArtifact(group: Option[String] = None) = {
        indexes foreach { index =>
          group match {
            case Some(groupId) if groupId.nonEmpty => index.artifacts(groupId) foreach addResult
            case _ => index.groups flatMap index.artifacts foreach addResult
          }
        }
        results.stopHere()
      }

      def completeVersion(group: Option[String], artifact: Option[String]) = (group, artifact) match {
        case (Some(groupId), Some(artifactId)) =>
          indexes foreach { index =>
            index.versions(groupId, artifactId) foreach addResult
          }
          results.stopHere()
        case _ => // do nothing
      }

      def isValidOp(operation: String) = operation == "%" || operation == "%%"

      (infixExpr.lOp, infixExpr.operation, infixExpr.rOp) match {
        case (lop, oper, rop: ScLiteralImpl) if lop == place.getParent && isValidOp(oper.getText) =>
          completeGroup(rop.stringValue)
        case (lop: ScLiteralImpl, oper, rop) if rop == place.getParent && isValidOp(oper.getText) =>
          completeArtifact(lop.stringValue)
        case (lop: ScInfixExpr, oper, rop) if rop == place.getParent && oper.getText == "%" && isValidOp(lop.operation.getText) =>
          (lop.lOp, lop.rOp) match {
            case (llop: ScLiteralImpl, lrop: ScLiteralImpl) => completeVersion(llop.stringValue, lrop.stringValue)
            case _ => // do nothing
          }
        case _ => // do nothing
      }
    }
  })
}

