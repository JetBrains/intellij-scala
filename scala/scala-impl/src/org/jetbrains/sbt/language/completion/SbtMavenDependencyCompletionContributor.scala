package org.jetbrains.sbt.language.completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns._
import com.intellij.patterns.StandardPatterns._
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScLiteralImpl
import org.jetbrains.plugins.scala.project._
import org.jetbrains.sbt.language.SbtFileType
import org.jetbrains.sbt.project.module.SbtModuleType
import org.jetbrains.sbt.resolvers.SbtResolverUtils

/**
  * @author Mikhail Mutcianko
  * @since 24.07.16
  */

class SbtMavenDependencyCompletionContributor extends CompletionContributor {

  val MAX_ITEMS = 6000

  private val pattern = and(
      psiElement().inFile(psiFile().withFileType(instanceOf(SbtFileType.getClass))),
      or(
        and(
          psiElement().withSuperParent(2, classOf[ScInfixExpr]),
          psiElement().withChild(psiElement().withText(string().oneOf("%", "%%")))
        ),
        psiElement().inside(
          and(
            instanceOf(classOf[ScInfixExpr]),
            psiElement().withChild(psiElement().withText("libraryDependencies"))
          )
        )
      )
    )
  extend(CompletionType.BASIC, pattern, new CompletionProvider[CompletionParameters] {
    override def addCompletions(params: CompletionParameters, context: ProcessingContext, results: CompletionResultSet): Unit = {

      def addResult(result: String, addPercent: Boolean = false): Unit = {
        if (addPercent)
          results.addElement(new LookupElement {
            override def getLookupString: String = result
            override def handleInsert(context: InsertionContext): Unit = {
              //gropus containig "scala" are more likely to undergo sbt's scalaVersion artifact substitution
              val postfix = if (result.contains("scala")) " %% \"\"" else " % \"\""
              context.getDocument.insertString(context.getTailOffset+1, postfix)
              context.getEditor.getCaretModel.moveToOffset(context.getTailOffset + postfix.length)
            }
          })
        else
          results.addElement(LookupElementBuilder.create(result))
      }

      val place = ScalaCompletionUtil.positionFromParameters(params)
      implicit val p: Project = place.getProject

      if (place.getText == CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED)
        return

      val file = Option(ScalaPsiUtil.fileContext(place))
      val resolvers = SbtResolverUtils.getProjectResolversForFile(file)

      def completeGroup(artifactId: String): Unit = {
        for {
          resolver <- resolvers
          index <- resolver.getIndex(p)
        } index.searchGroup(artifactId).foreach(i=>addResult(i))
        results.stopHere()
      }

      def completeArtifact(groupId: String, stripVersion: Boolean): Unit = {
        for {
          resolver <- resolvers
          index <- resolver.getIndex(p)
          i <- index.searchArtifact(groupId)
        } {
          if (stripVersion)
            addResult(i.replaceAll("_\\d\\.\\d+.*$", ""))
          else
            addResult(i)
        }
        results.stopHere()
      }

      def completeVersion(groupId: String, artifactId: String): Unit = {
        for {
          resolver <- resolvers
          index <- resolver.getIndex(p)
          i <- index.searchVersion(groupId, artifactId)
        } addResult(i)

        results.stopHere()
      }

      val cleanText = place.getText.replaceAll(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED, "").replaceAll("\"", "")

      def isValidOp(expression: ScReferenceExpression) =
        expression.getText match {
          case "%" | "%%" => true
          case _ => false
        }

      place.parentOfType(classOf[ScInfixExpr], strict = false).foreach {
        case ScInfixExpr(_, oper, _) if oper.getText == "+=" || oper.getText == "++=" => // empty completion from scratch
          completeGroup(cleanText)
        case ScInfixExpr(lop, oper, ScLiteralImpl.string(artifact)) if lop == place.getContext && isValidOp(oper) =>
          val versionSuffix = if (oper.getText == "%%") s"_${place.scalaLanguageLevelOrDefault.version}" else ""
          completeGroup(artifact + versionSuffix)
        case ScInfixExpr(ScLiteralImpl.string(group), oper, rop) if rop == place.getContext && isValidOp(oper) =>
          completeArtifact(group, stripVersion = oper.getText == "%%")
        case ScInfixExpr(ScInfixExpr(llop, loper, lrop), oper, rop)
          if rop == place.getContext && oper.getText == "%" && isValidOp(loper) =>
          val versionSuffix = if (loper.getText == "%%") s"_${place.scalaLanguageLevelOrDefault.version}" else ""
          for {
            ScLiteralImpl.string(group) <- Option(llop)
            ScLiteralImpl.string(artifact) <- Option(lrop)
          } yield completeVersion(group, artifact + versionSuffix)
        case _ => // do nothing
      }
    }
  })
}
