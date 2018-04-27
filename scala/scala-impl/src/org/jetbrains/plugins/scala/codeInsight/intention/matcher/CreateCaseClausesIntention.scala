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
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMatchStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createMatch
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.JavaConverters

final class CreateCaseClausesIntention extends PsiElementBaseIntentionAction {

  import CreateCaseClausesIntention._

  def getFamilyName: String = FamilyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
    findSurroundingMatch(element) match {
      case Some((_, scrutineeType)) =>
        setText(getFamilyName + scrutineeType)
        true
      case _ => false
    }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    findSurroundingMatch(element) match {
      case Some((action, _)) =>
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        if (!FileModificationService.getInstance.prepareFileForWrite(element.getContainingFile)) return
        IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace()
        action(element)
      case None =>
    }
  }

  private def addMatchClausesForSealedClass(statement: ScMatchStmt, clazz: ScTypeDefinition)
                                           (implicit context: ProjectContext): Unit = {
    val inheritors = findInheritors(clazz)

    replaceStatement(statement, inheritors.map(patternName), inheritors.map(target))
  }

  private def addMatchClausesForEnum(statement: ScMatchStmt, clazz: PsiClass)
                                    (implicit context: ProjectContext): Unit = {
    val className = clazz.name
    val patternNames = clazz.getFields.collect {
      case constant: PsiEnumConstant => s"$className.${constant.name}"
    }

    replaceStatement(statement, patternNames, Seq.fill(patternNames.length)(clazz))
  }

  private def addMatchClausesForCaseClassesAndObjects(statement: ScMatchStmt, clazz: PsiClass)
                                                     (implicit context: ProjectContext): Unit = {
    val inheritors = findInheritors(clazz).filter { definition =>
      definition.isCase || definition.isObject
    }

    replaceStatement(statement, inheritors.map(patternName) :+ "_", inheritors.map(target))
  }

  private def findSurroundingMatch(element: PsiElement): Option[(ProjectContext => Unit, String)] =
    element.getParent match {
      case statement@ScMatchStmt(expression, Seq()) =>
        expression.`type`().toOption
          .flatMap(_.extractClass)
          .collect {
            case clazz: ScTypeDefinition if clazz.hasModifierProperty("sealed") =>
              (addMatchClausesForSealedClass(statement, clazz)(_), " for variants of sealed type")
            case clazz if clazz.isEnum =>
              (addMatchClausesForEnum(statement, clazz)(_), " for variants of java enum")
            case clazz if !clazz.hasFinalModifier =>
              (addMatchClausesForCaseClassesAndObjects(statement, clazz)(_), " for inherited objects and case classes")
          }
      case _ => None
    }
}

object CreateCaseClausesIntention {

  private[matcher] val FamilyName = "Generate case clauses"

  private def findInheritors(clazz: PsiClass): Seq[ScTypeDefinition] = {
    import JavaConverters._
    val result = ClassInheritorsSearch.search(clazz, clazz.resolveScope, false).asScala.collect {
      case definition: ScTypeDefinition => definition
    }

    result.toSeq.sortBy {
      _.getNavigationElement.getTextRange.getStartOffset
    }
  }

  private def patternName(definition: ScTypeDefinition): String = definition match {
    case scObject: ScObject => scObject.name
    case clazz: ScClass if clazz.isCase =>
      val text = clazz.constructor match {
        case Some(primaryConstructor) =>
          primaryConstructor.effectiveFirstParameterSection
            .map(_.name)
            .mkString("( ", ", ", ")")
        case None => "()"
      }
      clazz.name + text
    case _ => "_ : " + definition.name
  }

  private def target(definition: ScTypeDefinition) = definition match {
    case clazz: ScClass if clazz.isCase => ScalaPsiUtil.getCompanionModule(clazz).get
    case _ => definition
  }

  private def replaceStatement(statement: ScMatchStmt, patterns: Seq[String], targets: Seq[PsiNamedElement])
                              (implicit context: ProjectContext): Unit = {
    val expressionText = statement.expr.get.getText

    val newStatement = createMatch(expressionText, patterns.map(name => s"case $name =>"))
    val replaced = statement.replace(newStatement).asInstanceOf[ScMatchStmt]
    bindReferences(replaced.caseClauses, targets)
  }

  private[this] def bindReferences(caseClauses: Seq[ScCaseClause], targets: Seq[PsiNamedElement]): Unit = {
    for {
      (ScCaseClause(Some(pattern), _, _), target) <- caseClauses.zip(targets)
      if !pattern.isInstanceOf[ScWildcardPattern]
      reference <- findReference(pattern, target.name)
    } reference.bindToElement(target)
  }

  private[this] def findReference(pattern: ScPattern, name: String) =
    pattern.depthFirst().collectFirst {
      case reference: ScReferenceElement if reference.refName == name => reference
    }
}