package org.jetbrains.plugins.scala.codeInspection.scaladoc

import com.intellij.codeInspection._
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.codeInspection.scaladoc.ScalaDocUnknownParameterInspection._
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScParameterOwner, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocComment, ScDocTag, ScDocTagValue}

import scala.collection.mutable

class ScalaDocUnknownParameterInspection extends LocalInspectionTool {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new ScalaElementVisitor {
      override def visitDocComment(docComment: ScDocComment): Unit = {
        ProgressIndicatorProvider.checkCanceled()
        checkDocComment(docComment, holder, isOnTheFly)
      }
    }
  }

  private def checkDocComment(docComment: ScDocComment, holder: ProblemsHolder, isOnTheFly: Boolean): Unit = {
    def registerProblemForTags(
      tags: Seq[ScDocTag],
      @Nls message: ScDocTag => String,
      anchor: ScDocTag => PsiElement = _.getNameElement,
      level: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
    ): Unit = {
      for (tag <- tags) {
        val removeTagFix = new ScalaDocRemoveElementQuickFix(tag)
        val problem = holder.getManager.createProblemDescriptor(anchor(tag), message(tag), true, level, isOnTheFly, removeTagFix)
        holder.registerProblem(problem)
      }
    }
    import ScalaInspectionBundle.message

    val commentOwner = docComment.getOwner
    commentOwner match {
      case _: ScTypeParametersOwner with ScParameterOwner =>
        val (paramTags, tparamTags) = findParamAndTypeParamTags(docComment)

        val tagsWithUnresolvedParams = (paramTags ++ tparamTags).filter(_.getValueElement match {
          case ref: ScDocTagValue =>
            ref.resolve() == null
          case _ => false
        })

        registerProblemForTags(
          tagsWithUnresolvedParams,
          p => ScalaBundle.message("cannot.resolve", p.getValueElement.getText),
          _.getValueElement,
        )

        val paramDuplicates = findDuplicatedParams(paramTags)
        val tparamDuplicates = findDuplicatedParams(tparamTags)

        registerProblemForTags(paramDuplicates, p => message("inspection.scaladoc.problem.duplicate.param", p.getValueElement.getText))
        registerProblemForTags(tparamDuplicates, p => message("inspection.scaladoc.problem.duplicate.tparam", p.getValueElement.getText))
      case _: ScTypeAlias => //scaladoc can't process type params for type alias now
        val typeParamsTags = findTypeParamTags(docComment)
        registerProblemForTags(
          typeParamsTags,
          _ => message("inspection.scaladoc.problem.tparam.not.supported.by.scaladoc.in.type.alias"),
          level = ProblemHighlightType.WEAK_WARNING,
        )
      case _ => //we can't have params/tparams here
        val (params, tparams) = findParamAndTypeParamTags(docComment)
        registerProblemForTags(params, _ => message("inspection.scaladoc.problem.param.not.allowed"))
        registerProblemForTags(tparams, _ => message("inspection.scaladoc.problem.tparam.not.allowed"))
    }
  }
}

object ScalaDocUnknownParameterInspection {

  private def findDuplicatedParams(tas: Seq[ScDocTag]): Seq[ScDocTag] = {
    val duplicates = new mutable.ArrayBuffer[ScDocTag]
    val visitedNames = new mutable.HashSet[String]

    for (tag <- tas) {
      val tagValue = tag.getValueElement
      if (tagValue != null) {
        val paramName = ScalaNamesUtil.clean(tagValue.getText)
        if (!visitedNames.add(paramName)) {
          duplicates += tag
        }
      }
    }
    duplicates.toSeq
  }

  private def findParamAndTypeParamTags(docComment: ScDocComment): (Seq[ScDocTag], Seq[ScDocTag]) = {
    val tagsAll = docComment.findTagsByName(MyScaladocParsing.ParamOrTParamTags.contains _).toSeq.filterByType[ScDocTag]
    tagsAll.partition(_.name == MyScaladocParsing.PARAM_TAG)
  }

  private def findTypeParamTags(docComment: ScDocComment): Seq[ScDocTag] =
    docComment.findTagsByName(MyScaladocParsing.TYPE_PARAM_TAG).toSeq.filterByType[ScDocTag]
}
