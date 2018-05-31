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
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.clauses.CaseClauseCompletionContributor
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScPattern, ScWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMatchStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createMatch

final class CreateCaseClausesIntention extends PsiElementBaseIntentionAction {

  import CreateCaseClausesIntention._

  def getFamilyName: String = FamilyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val maybeMatch = findSurroundingMatch(element)
    maybeMatch.map {
      case (_, _, scrutineeType) => s"$getFamilyName for $scrutineeType"
    }.foreach(setText)

    maybeMatch.isDefined
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    findSurroundingMatch(element) match {
      case Some(((clazz, statement), action, _)) =>
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        if (!FileModificationService.getInstance.prepareFileForWrite(element.getContainingFile)) return
        IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace()

        val (patternNames, targets) = action(clazz, statement)

        val newStatement = createMatch(statement.expr.get.getText, patternNames.map(name => s"case $name =>"))(statement)
        val replaced = statement.replace(newStatement).asInstanceOf[ScMatchStmt]

        bindReferences(replaced.caseClauses, targets)
      case None =>
    }
  }
}

object CreateCaseClausesIntention {

  import CaseClauseCompletionContributor._

  private[matcher] val FamilyName = "Generate case clauses"

  private def findSurroundingMatch(element: PsiElement): Option[((PsiClass, ScMatchStmt), (PsiClass, ScMatchStmt) => (Seq[String], Seq[PsiClass]), String)] =
    extractClass(element, classOf[PsiClass], regardlessClauses = false).collect {
      case pair@(clazz: ScTypeDefinition, _) if clazz.isSealed =>
        (pair, patternsAndTargets(_)(_)(), "variants of sealed type")
      case pair if pair._1.isEnum =>
        (pair, enumClauses, "variants of java enum")
      case pair if !pair._1.hasFinalModifier =>
        (pair, classesAndObjectsClauses, "inherited objects and case classes")
    }

  private def bindReferences(caseClauses: Seq[ScCaseClause], targets: Seq[PsiNamedElement]): Unit = {
    def findReference(pattern: ScPattern, name: String) =
      pattern.depthFirst().collectFirst {
        case reference: ScReferenceElement if reference.refName == name => reference
      }

    for {
      (ScCaseClause(Some(pattern), _, _), target) <- caseClauses.zip(targets)
      if !pattern.isInstanceOf[ScWildcardPattern]
      reference <- findReference(pattern, target.name)
    } reference.bindToElement(target)
  }

  private[this] def enumClauses(clazz: PsiClass, statement: ScMatchStmt) = {
    val className = clazz.name
    val patternNames = clazz.getFields.collect {
      case constant: PsiEnumConstant => s"$className.${constant.name}"
    }

    (patternNames.toSeq, Seq.fill(patternNames.length)(clazz))
  }

  private[this] def classesAndObjectsClauses(clazz: PsiClass, statement: ScMatchStmt) = {
    val (patternNames, targets) = patternsAndTargets(clazz)(statement) { definition =>
      definition.isCase || definition.isObject
    }

    (patternNames :+ "_", targets)
  }

  private[this] def patternsAndTargets(clazz: PsiClass)
                                      (statement: ScMatchStmt)
                                      (predicate: ScTypeDefinition => Boolean = _ => true) = {
    val inheritors = findInheritors(clazz).filter(predicate)

    (
      inheritors.map(patternText(_, statement)),
      inheritors.map {
        case clazz: ScClass if clazz.isCase => ScalaPsiUtil.getCompanionModule(clazz).get
        case definition => definition
      }
    )
  }
}