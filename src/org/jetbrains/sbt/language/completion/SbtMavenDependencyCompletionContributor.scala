package org.jetbrains.sbt.language.completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder}
import com.intellij.patterns.PlatformPatterns._
import com.intellij.patterns.StandardPatterns._
import com.intellij.util.ProcessingContext
import org.jetbrains.idea.maven.indices.MavenArtifactSearcher
import org.jetbrains.idea.maven.model.MavenArtifactInfo
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionContributor
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScLiteralImpl
import org.jetbrains.sbt.language.SbtFileType

/**
  * @author Mikhail Mutcianko
  * @since 24.07.16
  */

class SbtMavenDependencyCompletionContributor extends ScalaCompletionContributor {
  override def fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet): Unit = {
    super.fillCompletionVariants(parameters, result)
  }

  val MAX_ITEMS = 600

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

      def addResult(result: String, addPercent: Boolean = false) = {
        if (addPercent)
          results.addElement(new LookupElement {
            override def getLookupString: String = result
            override def handleInsert(context: InsertionContext) = {
              //gropus containig "scala" are more likely to undergo sbt's scalaVersion artifact substitution
              val postfix = if (result.contains("scala")) " %% \"\"" else " % \"\""
              context.getDocument.insertString(context.getTailOffset+1, postfix)
              context.getEditor.getCaretModel.moveToOffset(context.getTailOffset + postfix.length)
            }
          })
        else
          results.addElement(LookupElementBuilder.create(result))
      }

      val place = positionFromParameters(params)

      def completeMaven(query: String, field: MavenArtifactInfo => String, addPercent: Boolean = false) = {
        import scala.collection.JavaConversions._
        val results = for {
          l <- (new MavenArtifactSearcher).search(place.getProject, query, MAX_ITEMS)
          i <- l.versions
        } yield field(i)
        for { result <- results.toSet[String] } addResult(result, addPercent)
      }

      val expr = ScalaPsiUtil.getParentOfType(place, classOf[ScInfixExpr]).asInstanceOf[ScInfixExpr]

      if (place.getText == CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED)
        return

      val cleanText = place.getText.replaceAll(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED, "").replaceAll("\"", "")

      def isValidOp(operation: String) = operation == "%" || operation == "%%"

      (expr.lOp, expr.operation.text, expr.rOp) match {
        case (_, oper, _) if oper == "+=" || oper == "++=" => // empty completion from scratch
          completeMaven(cleanText, _.getGroupId, addPercent = true)
        case (lop, oper, ScLiteralImpl.string(artifact)) if lop == place.getContext && isValidOp(oper) =>
          completeMaven(s"$cleanText:$artifact", _.getGroupId)
        case (ScLiteralImpl.string(group), oper, rop) if rop == place.getContext && isValidOp(oper) =>
          if (oper == "%%")
            completeMaven(s"$group:$cleanText", _.getArtifactId.replaceAll("_\\d\\.\\d+.*$", ""))
          else
            completeMaven(s"$group:$cleanText", _.getArtifactId)
        case (ScInfixExpr(llop, loper, lrop), oper, rop)
          if rop == place.getContext && oper == "%" && isValidOp(loper.getText) =>
          for {
            ScLiteralImpl.string(group) <- Option(llop)
            ScLiteralImpl.string(artifact) <- Option(lrop)
          } yield completeMaven(s"$group:$artifact:$cleanText", _.getVersion)
        case _ => // do nothing
      }
    }
  })
}
