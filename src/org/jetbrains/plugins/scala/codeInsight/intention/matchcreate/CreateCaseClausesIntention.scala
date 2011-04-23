package org.jetbrains.plugins.scala
package codeInsight
package intention
package matchcreate

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import lang.psi.types.result.TypingContext
import lang.psi.impl.search.ScalaDirectClassInheritorsSearcher
import collection.Seq
import java.lang.String
import lang.psi.types.{ScSubstitutor, ScType, ScDesignatorType}
import lang.psi.ScalaPsiUtil
import com.intellij.psi.{PsiDocumentManager, PsiClass, PsiElement}
import com.intellij.codeInsight.CodeInsightUtilBase
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import lang.psi.api.toplevel.ScTypedDefinition
import lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScObject, ScClass}
import lang.lexer.{ScalaElementType, ScalaTokenTypes}
import lang.parser.ScalaElementTypes
import lang.psi.api.base.patterns.{ScPattern, ScCaseClause}
import lang.psi.api.expr.{ScReferenceExpression, ScExpression, ScMatchStmt}
import lang.psi.api.base.ScStableCodeReferenceElement
import com.intellij.psi.util.PsiTreeUtil
import extensions._
import com.intellij.openapi.util.TextRange
import lang.psi.impl.{ScalaFileImpl, ScalaPsiElementFactory}
import com.intellij.psi.search.searches.{ClassInheritorsSearch, DirectClassInheritorsSearch}

final class CreateCaseClausesIntention extends PsiElementBaseIntentionAction {
  def getFamilyName: String = ""

  override def getText: String = "Add case clauses"

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    findSurroundingMatch(element).isDefined
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    findSurroundingMatch(element) match {
      case Some((matchStmt, expr, cls)) =>
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        if (!CodeInsightUtilBase.prepareFileForWrite(element.getContainingFile)) return
        IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace()
        val inheritors = inheritorsOf(cls)
        val (caseClauseTexts, bindTos) = inheritors.map(caseClauseText).unzip
        val newMatchStmt = ScalaPsiElementFactory.createMatch(expr.getText, caseClauseTexts, element.getManager)
        matchStmt.replace(newMatchStmt)
        bindReferences(newMatchStmt, bindTos)
      case None =>
    }
  }

  private def bindReferences(newMatchStmt: ScMatchStmt, bindTargets: Seq[PsiElement]) {
    for {
      (caseClause, i) <- newMatchStmt.caseClauses.zipWithIndex
    } {
      val bindTo = bindTargets(i)
      bindReference(caseClause, bindTo)
    }
  }

  private def bindReference(caseClause: ScCaseClause, bindTo: PsiElement) {
    val pattern: ScPattern = caseClause.pattern.get
    val refExprOpt: Option[PsiElement] = pattern.findChildrenByType(ScalaElementTypes.REFERENCE_EXPRESSION).headOption
    val codeRefElemOpt: Option[PsiElement] = pattern.findChildrenByType(ScalaElementTypes.REFERENCE).headOption
    refExprOpt match {
      case Some(ref: ScReferenceExpression) => ref.bindToElement(bindTo)
      case _ =>
    }
    codeRefElemOpt.headOption match {
      case Some(ref: ScStableCodeReferenceElement) => ref.bindToElement(bindTo)
      case _ =>
    }
  }

  private def inheritorsOf(cls: ScClass): Seq[ScTypeDefinition] = {
    val found: Array[ScTypeDefinition] = ClassInheritorsSearch.search(cls, cls.getResolveScope, false).toArray(PsiClass.EMPTY_ARRAY).collect {
      case x: ScTypeDefinition => x
    }
    found.sortBy(_.getNavigationElement.getTextRange.getStartOffset)
  }

  /**
   * @return (caseClauseText, elementToBind)
   */
  private def caseClauseText(td: ScTypeDefinition): (String, PsiElement) = {
    val refText = td.getName
    val (pattern, bindTo) = td match {
      case obj: ScObject => (refText, obj)
      case cls: ScClass if cls.isCase =>
        val companionObj = ScalaPsiUtil.getCompanionModule(cls).get
        val text = cls.constructor match {
          case Some(primaryConstructor) =>
            val parameters = primaryConstructor.effectiveFirstParameterSection
            val bindings = parameters.map(_.getName).mkString("( ", ", ", ")")
            refText + bindings
          case None =>
            refText + "()"
        }
        (text, companionObj)
      case _ =>
        val text = "_ : " + refText
        (text, td)
    }
    val text = "case %s =>".format(pattern)
    (text, bindTo)
  }


  /**
   * @return (matchStmt, matchExpression, matchExpressionClass)
   */
  private def findSurroundingMatch(element: PsiElement): Option[(ScMatchStmt, ScExpression, ScClass)] = {
    element.getParent match {
      case x: ScMatchStmt if x.caseClauses.isEmpty =>
        val classType: Option[(PsiClass, ScSubstitutor)] = x.expr.flatMap(_.getType(TypingContext.empty).toOption).
                flatMap(t => ScType.extractClassType(t, Some(element.getProject)))

        classType match {
          case Some((cls: ScClass, subst)) if cls.hasModifierProperty("sealed") => Some((x, x.expr.get, cls))
          case _ => None
        }
      case _ => None
    }
  }
}