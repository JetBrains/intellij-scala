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
import org.jetbrains.plugins.scala.lang.completion.clauses
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScPattern, ScWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMatchStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.api.ExtractClass
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable

final class CreateCaseClausesIntention extends PsiElementBaseIntentionAction {

  import CreateCaseClausesIntention._

  def getFamilyName: String = FamilyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val maybeMatch = createStrategyForMatch(element)
    maybeMatch.map(_.familyName).foreach(setText)

    maybeMatch.isDefined
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    createStrategyForMatch(element).foreach { strategy =>
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      if (!FileModificationService.getInstance.prepareFileForWrite(element.getContainingFile)) return
      IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace()

      bindReferences(strategy.replacedClauses)
    }
  }
}

object CreateCaseClausesIntention {

  private[matcher] val FamilyName = "Generate case clauses"

  private def createStrategyForMatch(element: PsiElement): Option[PatternGenerationStrategy] =
    element.getParent match {
      case statement: ScMatchStmt if statement.caseClauses.isEmpty =>
        statement.expr.collect {
          case Typeable(ExtractClass(clazz)) => clazz
        }.collect {
          case clazz: ScTypeDefinition if clazz.isSealed => new SealedClassGenerationStrategy(clazz, statement)
          case clazz if clazz.isEnum => new EnumGenerationStrategy(clazz, statement)
          case clazz if !clazz.hasFinalModifier => new NonFinalClassGenerationStrategy(clazz, statement)
        }
      case _ => None
    }

  private def bindReferences(caseClauses: Seq[(ScCaseClause, PsiClass)]): Unit = {
    def findReference(pattern: ScPattern, name: String) =
      pattern.depthFirst().collectFirst {
        case reference: ScReferenceElement if reference.refName == name => reference
      }

    for {
      (ScCaseClause(Some(pattern), _, _), targetClass) <- caseClauses
      if !pattern.isInstanceOf[ScWildcardPattern]
      reference <- findReference(pattern, targetClass.name)
    } reference.bindToElement(targetClass)
  }

  private sealed abstract class PatternGenerationStrategy protected(protected val clazz: PsiClass,
                                                                    protected val statement: ScMatchStmt,
                                                                    protected val familyNameSuffix: String) {

    final def familyName = s"$FamilyName for $familyNameSuffix"

    final def replacedClauses: Seq[(ScCaseClause, PsiClass)] = {
      val (patterns, targets) = targetPatterns
      replacedClauses(patterns).zip(targets)
    }

    protected def targetPatterns: (Seq[String], Seq[PsiClass]) = {
      val inheritors = findInheritors

      val patterns = inheritors.map {
        clauses.patternText(_)(statement)
      }

      val targets = inheritors.map {
        case clazz: ScClass if clazz.isCase => ScalaPsiUtil.getCompanionModule(clazz).get
        case definition => definition
      }

      (patterns, targets)
    }

    protected def findInheritors: Seq[ScTypeDefinition] =
      clauses.findInheritors(clazz)

    private def replacedClauses(patterns: Seq[String]): Seq[ScCaseClause] = {
      val caseClauses = patterns.map { pattern =>
        import ScalaTokenTypes.{kCASE, tFUNTYPE}
        s"$kCASE $pattern $tFUNTYPE"
      }

      val ScMatchStmt(ElementText(expressionText), _) = statement

      import statement.projectContext
      val newStatement = ScalaPsiElementFactory.createMatch(expressionText, caseClauses)

      val ScMatchStmt(_, result) = statement.replace(newStatement)
      result
    }
  }

  private class SealedClassGenerationStrategy(clazz: ScTypeDefinition, statement: ScMatchStmt)
    extends PatternGenerationStrategy(clazz, statement, familyNameSuffix = "variants of sealed type")

  private class EnumGenerationStrategy(clazz: PsiClass, statement: ScMatchStmt)
    extends PatternGenerationStrategy(clazz, statement, familyNameSuffix = "variants of java enum") {

    override def targetPatterns: (Seq[String], Seq[PsiClass]) = {
      val className = clazz.name
      val patternNames = clazz.getFields.collect {
        case constant: PsiEnumConstant => s"$className.${constant.name}"
      }

      (patternNames.toSeq, Seq.fill(patternNames.length)(clazz))
    }
  }

  private class NonFinalClassGenerationStrategy(clazz: PsiClass, statement: ScMatchStmt)
    extends PatternGenerationStrategy(clazz, statement, familyNameSuffix = "inherited objects and case classes") {

    override def targetPatterns: (Seq[String], Seq[PsiClass]) = {
      val (patternNames, targets) = super.targetPatterns
      (patternNames :+ ScalaTokenTypes.tUNDER.toString, targets)
    }

    override protected def findInheritors: Seq[ScTypeDefinition] =
      super.findInheritors.filter { definition =>
        definition.isCase || definition.isObject
      }
  }

}