package org.jetbrains.plugins.scala
package codeInsight
package template
package macros

import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder}
import com.intellij.codeInsight.template._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedMembersSearch
import com.intellij.psi.{PsiClass, PsiMember}
import com.intellij.util.{EmptyQuery, Query}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager

import scala.jdk.CollectionConverters._

final class ScalaAnnotatedMacro extends ScalaMacro {

  protected def getAnnotatedMembers(params: Array[Expression], context: ExpressionContext): Query[PsiMember] = {
    (params, context) match {
      case (null, _) |
           (_, null) => EmptyQuery.getEmptyQuery[PsiMember]
      case _ if params.length > 0 => //TODO should params.length always equal 1?
        val project = context.getProject
        val scope = GlobalSearchScope.allScope(project)
        Option(params.head.calculateResult(context)).flatMap(res => ScalaPsiManager.instance(project).
                getCachedClass(scope, res.toString)).map(AnnotatedMembersSearch.search(_, scope)).
                getOrElse(EmptyQuery.getEmptyQuery[PsiMember])
    }
  }

  override def getPresentableName: String = ScalaCodeInsightBundle.message("macro.annotated")

  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = {
    Option(getAnnotatedMembers(params, context).findFirst()).map(member => new TextResult(member match {
      case psiClass: PsiClass => psiClass.getQualifiedName
      case _ => member.getName
    })).orNull
  }

  override def calculateQuickResult(params: Array[Expression], context: ExpressionContext): Result = calculateResult(params, context)

  override def calculateLookupItems(params: Array[Expression], context: ExpressionContext): Array[LookupElement] = {
    val secondParamName = if (params.length > 1) params(1).calculateResult(context).toString else null
    val isShortName = secondParamName != null && !secondParamName.toBoolean
    val project = context.getProject
    val outerClass: Option[PsiClass] = Option(secondParamName).flatMap { secondParamName =>
      ScalaPsiManager.instance(project).getCachedClass(GlobalSearchScope.allScope(project), secondParamName)
    }
    getAnnotatedMembers(params, context).findAll()
      .asScala
      .filter(outerClass.isDefined && outerClass.contains(_))
      .map {
        case psiClass: PsiClass if !isShortName => psiClass.getQualifiedName
        case notClass => notClass.getName
      }
      .toSet[String]
      .map(LookupElementBuilder.create)
      .toArray
  }
}
