package org.jetbrains.plugins.scala
package codeInsight
package intention
package matcher

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.search.searches.ClassInheritorsSearch
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScPattern, ScWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScMatchStmt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types.{ScSubstitutor, ScTypeExt}
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.collection.Seq

final class CreateCaseClausesIntention extends PsiElementBaseIntentionAction {
  def getFamilyName: String = "Generate case clauses"

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    findSurroundingMatch(element) match {
      case Some((_, scrutineeType)) =>
        setText(getFamilyName + scrutineeType)
        true
      case None =>
        false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    findSurroundingMatch(element) match {
      case Some((action, _)) =>
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        if (!FileModificationService.getInstance.prepareFileForWrite(element.getContainingFile)) return
        IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace()
        action(project, editor, element)
      case None =>
    }
  }


  private def addMatchClausesForSealedClass(matchStmt: ScMatchStmt, expr: ScExpression, cls: ScTypeDefinition)
                                           (project: Project, editor: Editor, element: PsiElement) {
    val inheritors = inheritorsOf(cls)
    val (caseClauseTexts, bindTos) = inheritors.map(caseClauseText).unzip
    val newMatchStmt = ScalaPsiElementFactory.createMatch(expr.getText, caseClauseTexts, element.getManager)
    val replaced = matchStmt.replace(newMatchStmt).asInstanceOf[ScMatchStmt]
    bindReferences(replaced, bindTos)
  }

  private def addMatchClausesForEnum(matchStmt: ScMatchStmt, expr: ScExpression, cls: PsiClass)
                                    (project: Project, editor: Editor, element: PsiElement) {
    val enumConsts: Array[PsiEnumConstant] = cls.getFields.collect {
      case enumConstant: PsiEnumConstant => enumConstant
    }
    val caseClauseTexts = enumConsts.map(ec => "case %s.%s =>".format(cls.name, ec.name))
    val newMatchStmt = ScalaPsiElementFactory.createMatch(expr.getText, caseClauseTexts, element.getManager)
    val replaced = matchStmt.replace(newMatchStmt).asInstanceOf[ScMatchStmt]
    bindReferences(replaced, _ => cls)
  }

  private def addMatchClausesForCaseClassesAndObjects(matchStmt: ScMatchStmt, expr: ScExpression, cls: PsiClass)
                                           (project: Project, editor: Editor, element: PsiElement) {
    val inheritors = inheritorsOf(cls).filter(inh => inh.isCase || inh.isObject)
    val (withoutDefault, bindTos) = inheritors.map(caseClauseText).unzip
    val defaultCaseClauseText = "case _ =>"
    val caseClauseTexts =
      if (withoutDefault.nonEmpty) withoutDefault :+ defaultCaseClauseText
      else Seq(s"\n$defaultCaseClauseText //could not find inherited objects or case classes\n")
    val newMatchStmt = ScalaPsiElementFactory.createMatch(expr.getText, caseClauseTexts, element.getManager)
    val replaced = matchStmt.replace(newMatchStmt).asInstanceOf[ScMatchStmt]
    bindReferences(replaced, bindTos)
  }

  private def bindReferences(newMatchStmt: ScMatchStmt, bindTargets: Int => PsiNamedElement) {
    for {
      (caseClause, i) <- newMatchStmt.caseClauses.zipWithIndex
      if !caseClause.pattern.exists(_.isInstanceOf[ScWildcardPattern])
    } {
      val bindTo = bindTargets(i)
      bindReference(caseClause, bindTo)
    }
  }

  private def bindReference(caseClause: ScCaseClause, bindTo: PsiNamedElement) {
    val pattern: ScPattern = caseClause.pattern.get
    val ref = pattern.depthFirst.collectFirst {
      case x: ScReferenceElement if x.refName == bindTo.name => x
    }
    ref.foreach(_.bindToElement(bindTo))
  }

  private def inheritorsOf(cls: PsiClass): Seq[ScTypeDefinition] = {
    val found: Array[ScTypeDefinition] = ClassInheritorsSearch.search(cls, cls.getResolveScope, false).toArray(PsiClass.EMPTY_ARRAY).collect {
      case x: ScTypeDefinition => x
    }
    found.sortBy(_.getNavigationElement.getTextRange.getStartOffset)
  }

  /**
   * @return (caseClauseText, elementToBind)
   */
  private def caseClauseText(td: ScTypeDefinition): (String, PsiNamedElement) = {
    val refText = td.name
    val (pattern, bindTo) = td match {
      case obj: ScObject => (refText, obj)
      case cls: ScClass if cls.isCase =>
        val companionObj = ScalaPsiUtil.getCompanionModule(cls).get
        val text = cls.constructor match {
          case Some(primaryConstructor) =>
            val parameters = primaryConstructor.effectiveFirstParameterSection
            val bindings = parameters.map(_.name).mkString("( ", ", ", ")")
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
  private def findSurroundingMatch(element: PsiElement): Option[((Project, Editor, PsiElement) => Unit, String)] = {
    element.getParent match {
      case x: ScMatchStmt if x.caseClauses.isEmpty =>
        val project = element.getProject
        implicit val typeSystem = project.typeSystem
        val classType: Option[(PsiClass, ScSubstitutor)] = x.expr.flatMap(_.getType(TypingContext.empty).toOption).
          flatMap(_.extractClassType(project))

        classType match {
          case Some((cls: ScTypeDefinition, _)) if cls.hasModifierProperty("sealed") =>
            Some(addMatchClausesForSealedClass(x, x.expr.get, cls), " for variants of sealed type")
          case Some((cls: PsiClass, _)) if cls.isEnum =>
            Some(addMatchClausesForEnum(x, x.expr.get, cls), " for variants of java enum")
          case Some((cls: PsiClass, _)) if !cls.hasFinalModifier =>
            Some(addMatchClausesForCaseClassesAndObjects(x, x.expr.get, cls), " for inherited objects and case classes")
          case _ => None
        }
      case _ => None
    }
  }
}