package org.jetbrains.plugins.scala
package codeInspection
package scaladoc

import java.lang.String
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocComment, ScDocTag}
import collection.mutable.{HashSet, HashMap}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScTypeParamClause, ScTypeParam, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScFunction}
import com.intellij.openapi.project.Project
import com.intellij.codeInspection._

/**
 * User: Dmitry Naidanov
 * Date: 11/21/11
 */

class ScalaDocUnknownParameterInspection extends LocalInspectionTool {
  def getGroupDisplayName: String = InspectionsUtil.SCALADOC

  def getDisplayName: String = "Unknown Parameter"

  def getShortName: String = "UnknownParameter"

  override def isEnabledByDefault: Boolean = true

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new ScalaElementVisitor {
      override def visitDocComment(s: ScDocComment) {
        val tagParams = HashMap[String,ScDocTag]()
        val tagTypeParams = HashMap[String,ScDocTag]()
        val duplicatingParams = HashSet[ScDocTag]()
        
        def insertDuplicating(element: Option[ScDocTag], duplicateElement: ScDocTag) {
          element.foreach(duplicatingParams += (_, duplicateElement))
        }
        
        def paramsDif(paramList: scala.Seq[ScParameter], tagParamList: scala.Seq[ScTypeParam]) {
          if (paramList != null) {
            for (funcParam <- paramList) {
              tagParams -= funcParam.getName
            }
          }
          if (tagParamList != null) {
            for (typeParam <- tagParamList) {
              tagTypeParams -= typeParam.getName
            }
          }
        }
        
        def collectDocParams() {
          for (tagParam <- s.findTagsByName(Set("@param", "@tparam").contains(_))) {
            if (tagParam.getValueElement != null) {
              tagParam.getName match {
                case "@param" =>
                  insertDuplicating(tagParams.put(tagParam.getValueElement.getText, tagParam.asInstanceOf[ScDocTag]),
                    tagParam.asInstanceOf[ScDocTag])
                case "@tparam" =>
                  insertDuplicating(tagTypeParams.put(tagParam.getValueElement.getText, tagParam.asInstanceOf[ScDocTag]),
                    tagParam.asInstanceOf[ScDocTag])
              }
            }
          }
        }
        
        def registerBadParams() {
          for ((_, badParameter) <- tagParams) {
            holder.registerProblem(holder.getManager.createProblemDescriptor(
              badParameter.getValueElement, "Unknown Tag Parameter", true,
              ProblemHighlightType.ERROR, isOnTheFly))
          }
          for ((_, badTypeParameter) <- tagTypeParams) {
            holder.registerProblem(holder.getManager.createProblemDescriptor(
              badTypeParameter.getValueElement, "Unknown Tag Type Parameter", true,
              ProblemHighlightType.ERROR, isOnTheFly))
          }
          for (duplicatingParam <- duplicatingParams) {
            holder.registerProblem(holder.getManager.createProblemDescriptor(
              duplicatingParam.getValueElement, "One param/tparam Tag for one param/type param allowed",
              true, ProblemHighlightType.GENERIC_ERROR, isOnTheFly,
              new ScalaDocDeleteDuplicatingParamQuickFix(duplicatingParam, true)))
          }  
        }
        
        def doInspection(paramList: scala.Seq[ScParameter], tagParamList: scala.Seq[ScTypeParam]) {
          collectDocParams()
          paramsDif(paramList, tagParamList)
          registerBadParams()
        }
        
        s.getNextSiblingNotWhitespace match {
          case func: ScFunction =>
            doInspection(func.parameters, func.typeParameters)
          case constr: ScPrimaryConstructor =>
            constr.getClassTypeParameters match {
              case a: Some[ScTypeParamClause] => 
                doInspection(constr.parameters, a.get.typeParameters)
              case None =>
                doInspection(constr.parameters, null)
            }
          case typeAlias: ScTypeAlias => //scaladoc can't process tparams for type alias now
            for (tag <- s.findTagsByName("@tparam")) {
              holder.registerProblem(holder.getManager.createProblemDescriptor(
                tag.getFirstChild, "Scaladoc can't process tparams for type alias now",
                true, ProblemHighlightType.WEAK_WARNING, isOnTheFly))
            }
          case _ => //we can't have params/tparams here
            for (tag <- s.findTagsByName(Set("@param", "@tparam").contains(_)) if tag.isInstanceOf[ScDocTag]) {
              holder.registerProblem(holder.getManager.createProblemDescriptor(
                tag.getFirstChild, "@param and @tparams tags arn't allowed there",
                true, ProblemHighlightType.GENERIC_ERROR, isOnTheFly, 
                new ScalaDocDeleteDuplicatingParamQuickFix(tag.asInstanceOf[ScDocTag], false)))
            }
        }
      }
    }
  }
}


class ScalaDocDeleteDuplicatingParamQuickFix(paramTag: ScDocTag, isDuplicating: Boolean) extends LocalQuickFix {
  def getName: String = if (isDuplicating) "Delete duplicating param" else "Delete tag"

  def getFamilyName: String = InspectionsUtil.SCALADOC

  def applyFix(project: Project, descriptor: ProblemDescriptor) {
    if (!paramTag.isValid) return

    paramTag.delete()
  }
}