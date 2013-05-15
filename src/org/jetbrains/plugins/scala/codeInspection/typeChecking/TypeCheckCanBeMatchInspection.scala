package org.jetbrains.plugins.scala
package codeInspection.typeChecking

import org.jetbrains.plugins.scala.codeInspection.{AbstractFix, AbstractInspection}
import TypeCheckCanBeMatchInspection.inspectionId
import TypeCheckCanBeMatchInspection.inspectionName
import TypeCheckCanBeMatchInspection.isIsInstOfCall
import TypeCheckCanBeMatchInspection.separateConditions
import com.intellij.codeInspection.{ProblemDescriptor, ProblemHighlightType, ProblemsHolder}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.ElementText
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester
import scala.Some
import scala.collection.mutable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScPattern, ScBindingPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import java.util.Comparator
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import com.intellij.codeInsight.PsiEquivalenceUtil
import scala.annotation.tailrec
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.plugins.scala.lang.refactoring.rename.GroupInplaceRenamer
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaVariableValidator

/**
 * Nikolay.Tropin
 * 5/6/13
 */
object TypeCheckCanBeMatchInspection {
  val inspectionId = "TypeCheckCanBeMatch"
  val inspectionName = "Type check can be replaced by pattern matching"

  @tailrec
  final def isIsInstOfCall(expression: ScExpression): Boolean = {
    expression match {
      case parenthesized: ScParenthesisedExpr => isIsInstOfCall(parenthesized.expr.getOrElse(null))
      case call: ScGenericCall =>
        call.referencedExpr match {
          case ref: ScReferenceExpression if ref.refName == "isInstanceOf" =>
            ref.resolve() match {
              case synth: ScSyntheticFunction => true
              case _ => false
            }
          case _ => false
        }
      case _ => false
    }
  }

  def separateConditions(expr: ScExpression): List[ScExpression] = {
    expr match {
      case parenth: ScParenthesisedExpr => parenth.expr match {
        case Some(infixExpr: ScInfixExpr) if infixExpr.operation.refName == "&&" =>
          separateConditions(infixExpr.lOp) ::: separateConditions(infixExpr.rOp) ::: Nil
        case genCall: ScGenericCall => genCall :: Nil
        case _ => parenth :: Nil
      }
      case infixExpr: ScInfixExpr if infixExpr.operation.refName == "&&" =>
        separateConditions(infixExpr.lOp) ::: separateConditions(infixExpr.rOp) ::: Nil
      case _ => expr :: Nil
    }
  }
}

class TypeCheckCanBeMatchInspection extends AbstractInspection(inspectionId, inspectionName){

  def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case call: ScGenericCall if isIsInstOfCall(call) =>
      for {
        ifStmt <- Option(PsiTreeUtil.getParentOfType(call, classOf[ScIfStmt]))
        condition <- ifStmt.condition
        iioCall <- separateConditions(condition).filter(isIsInstOfCall(_))
        if iioCall == call
      } {
          val fix = new TypeCheckCanBeMatchQuickFix(call, ifStmt)
          holder.registerProblem(call, inspectionId, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, fix)
        }
      }
  }

class TypeCheckCanBeMatchQuickFix(isInstOfUnderFix: ScGenericCall, ifStmt: ScIfStmt) extends AbstractFix(inspectionName, isInstOfUnderFix) {
  val needInplaceRename = collection.mutable.ArrayBuffer[(Int, Seq[String])]()

  def doApplyFix(project: Project, descriptor: ProblemDescriptor) {
    if (!ifStmt.isValid || !isInstOfUnderFix.isValid) return
    for (matchStmt <- buildMatchStmt(ifStmt)) {
      val newMatch = ifStmt.replaceExpression(matchStmt, removeParenthesis = true).asInstanceOf[ScMatchStmt]
      if (!ApplicationManager.getApplication.isUnitTestMode) {
        val renamer = new GroupInplaceRenamer(newMatch)
        setElementsForRename(newMatch, renamer)
        renamer.startRenaming()
      }
    }
  }

  def buildMatchStmt(ifStmt: ScIfStmt): Option[ScMatchStmt] = {
    val matchedExprText = isInstOfUnderFix.referencedExpr.getFirstChild.getText
    val matchStmtText = s"$matchedExprText match { \n " + buildCaseClausesText(ifStmt) + "}"
    val matchStmt = ScalaPsiElementFactory.createExpressionFromText(matchStmtText, ifStmt.getManager).asInstanceOf[ScMatchStmt]
    Some(matchStmt)
  }

  private def buildCaseClauseText(ifStmt: ScIfStmt, isInstOf: ScGenericCall, caseClauseIndex: Int): Option[String] = {
    var definedName: Option[String] = None
    var definition: Option[ScPatternDefinition] = None
    def checkAndStoreNameAndDef(asInstOfCall: ScGenericCall): Boolean = {
      ScalaPsiUtil.getContextOfType(asInstOfCall, strict = true, classOf[ScPatternDefinition]) match {
        case patternDef: ScPatternDefinition =>
          val bindings = patternDef.bindings
          //pattern consist of one assignment of asInstanceOf call
          if (bindings.size == 1 && patternDef.expr.get == asInstOfCall) {
            definition match {
              //store first occurence of pattern definition and name
              case Some(oldDef) if oldDef.getTextOffset < patternDef.getTextOffset => true
              case _ =>
                definedName = Some(bindings(0).findFirstChildByType(ScalaTokenTypes.tIDENTIFIER).getText)
                definition = Some(patternDef)
                true
            }
          } else false
        case null => false
      }
    }

    for {
      args <- isInstOf.typeArgs
      condition <- ifStmt.condition

    } yield {
      val scType = args.typeArgs(0).calcType
      val typeName = scType.presentableText
      val asInstOfInBody = findAsInstOfCalls(ifStmt.thenBranch, isInstOf)
      val guardCond = guardCondition(condition, isInstOf)
      val asInstOfInGuard = findAsInstOfCalls(guardCond, isInstOf)
      val asInstOfEverywhere = asInstOfInBody ++ asInstOfInGuard

      if (asInstOfInBody.count(checkAndStoreNameAndDef(_)) == 0) {
        //no usage of asInstanceOf
        if (asInstOfEverywhere.size == 0) {
          buildCaseClauseText("_ : " + typeName, guardCond, ifStmt.thenBranch)
        }
        //no named usage
        else {
          val suggestedNames: Array[String] = NameSuggester.suggestNames(asInstOfEverywhere(0),
            new ScalaVariableValidator(null, ifStmt.getProject, ifStmt, false, ifStmt.getParent, ifStmt.getParent))
          val name = suggestedNames(0)
          asInstOfEverywhere.foreach { c =>
            val newExpr = ScalaPsiElementFactory.createExpressionFromText(name, ifStmt.getManager)
            c.replaceExpression(newExpr, removeParenthesis = true)
          }

          needInplaceRename += ((caseClauseIndex, suggestedNames.toSeq))
          buildCaseClauseText(s"$name : $typeName", guardCond, ifStmt.thenBranch)
        }
      }
      //have named usage, use this name in case clause pattern definition
      else {
        //deleting unnecessary val declaration
        val patternDef = definition.get
        patternDef.delete()

        val name = definedName.get
        val newExpr = ScalaPsiElementFactory.createExpressionFromText(name, ifStmt.getManager)
        asInstOfEverywhere.foreach(_.replaceExpression(newExpr, removeParenthesis = true))
        buildCaseClauseText(s"$name : $typeName", guardCond, ifStmt.thenBranch)
      }
    }
  }

  private def buildDefaultCaseClauseText(body: Option[ScExpression]): Option[String] =  {
    Some(buildCaseClauseText("_ ", None, body))
  }

  private def buildCaseClauseText(patternText: String, guardCondition: Option[ScExpression], body: Option[ScExpression]): String = {
    val builder = new StringBuilder
    builder.append("case ").append(patternText)
    guardCondition.map(cond => builder.append(" if " + cond.getText))
    builder.append(" =>")
    body match {
      case Some(block: ScBlock) =>
        for (elem <- block.children) {
          val elementType: IElementType = elem.getNode.getElementType
          if (elementType != ScalaTokenTypes.tLBRACE && elementType != ScalaTokenTypes.tRBRACE)
            builder.append(elem.getText)
        }
      case Some(expr: ScExpression) => builder.append(expr.getText)
      case None =>
    }
    builder.toString()
  }

  private def buildCaseClausesText(ifStmt: ScIfStmt): String = {

    def listOfIfAndIsInstOf(currentIfStmt: ScIfStmt, currentCall: ScGenericCall): List[(ScIfStmt, ScGenericCall)] = {
      for (currentBase <- baseExpr(currentCall)) {
        currentIfStmt.elseBranch match {
          case Some(nextIfStmt: ScIfStmt) =>
            for {
              nextCond <- nextIfStmt.condition
              nextCall <- separateConditions(nextCond).filter(isIsInstOfCall(_)).map(_.asInstanceOf[ScGenericCall])
              nextBase <- baseExpr(nextCall)
            } {
              if (PsiEquivalenceUtil.areElementsEquivalent(currentBase, nextBase, comparator, false))
                return (currentIfStmt, currentCall) :: listOfIfAndIsInstOf(nextIfStmt, nextCall)
            }
            return (currentIfStmt, currentCall) :: Nil
          case _ => return (currentIfStmt, currentCall) :: Nil
        }
      }
      Nil
    }

    val builder = new StringBuilder
    val (ifStmts, isInstOf) = listOfIfAndIsInstOf(ifStmt, isInstOfUnderFix).unzip

    for {
      index <- 0 until ifStmts.size
      text <- buildCaseClauseText(ifStmts(index), isInstOf(index), index)
    } {
      builder.append(text)
    }

    if (ifStmts != Nil) {
      val lastElse = ifStmts.last.elseBranch
      val defaultText: Option[String] = buildDefaultCaseClauseText(lastElse)
      defaultText.foreach(builder.append(_))
    }

    builder.toString()
  }
  
  def findAsInstOfCalls(body: Option[ScExpression], isInstOfCall: ScGenericCall): Seq[ScGenericCall] = {
    def isAsInstOfCall(genCall: ScGenericCall) = {
      genCall.referencedExpr match {
        case ref: ScReferenceExpression if ref.refName == "asInstanceOf" => true
        case _ => false
      }
    }

    def equalTypes(firstCall: ScGenericCall, secondCall: ScGenericCall): Boolean = {
      val option = for {
        firstArgs <- firstCall.typeArgs
        secondArgs <- secondCall.typeArgs
      } yield {
        val firstType = firstArgs.typeArgs(0).calcType
        val secondType = secondArgs.typeArgs(0).calcType
        firstType.equiv(secondType)
      }
      option.getOrElse(false)
    }

    val result = collection.mutable.ArrayBuffer[ScGenericCall]()
    val visitor = new ScalaRecursiveElementVisitor() {
      override def visitGenericCallExpression(call: ScGenericCall) {
        for {
          base1 <- baseExpr(isInstOfCall)
          base2 <- baseExpr(call)
          if isAsInstOfCall(call)
          if equalTypes(call, isInstOfCall)
          if PsiEquivalenceUtil.areElementsEquivalent(base1, base2, comparator, false)
        } {
          result += call
        }
        super.visitGenericCallExpression(call)
      }
    }

    for (expr <- body) expr.accept(visitor)
    result
  }

  def setElementsForRename(matchStmt: ScMatchStmt, renamer: GroupInplaceRenamer) {
    val caseClauses = matchStmt.caseClauses.toList

    for {
      (index, suggestedNames) <- needInplaceRename
      caseClause = caseClauses(index)
      name = suggestedNames(0)
    } {
      val primary = mutable.ArrayBuffer[ScNamedElement]()
      val dependents = mutable.SortedSet()(Ordering.by[ScalaPsiElement, Int](_.getTextOffset))
      val visitor = new ScalaRecursiveElementVisitor() {
        override def visitPattern(pat: ScPattern) {
          pat match {
            case bp: ScBindingPattern if bp.name == name =>
              primary += bp
            case _ =>
          }
          super.visitPattern(pat)
        }

        //Override also visitReferenceExpression! and visitTypeProjection!
        override def visitReference(ref: ScReferenceElement) {
          if (ref.refName == name)
            dependents += ref
          super.visitReference(ref)
        }
      }
      caseClause.accept(visitor)
      for (prim <- primary) renamer.addGroup(prim, dependents.toList, suggestedNames)
    }
  }

  def elementsForRename(matchStmt: ScMatchStmt): List[ScNamedElement] = {
    val caseClauses = matchStmt.caseClauses.toList
    val result = mutable.ListBuffer[ScNamedElement]()
    for {
      (index, name) <- needInplaceRename
      caseClause = caseClauses(index)
    } {
      val visitor = new ScalaRecursiveElementVisitor() {
        override def visitPattern(pat: ScPattern) {
          pat match {
            case bp: ScBindingPattern if bp.name == name =>
              result += bp
            case _ =>
          }
          super.visitPattern(pat)
        }
      }
      caseClause.accept(visitor)
    }
    result.toList
  }

  def baseExpr(gCall: ScGenericCall): Option[ScExpression] = gCall.referencedExpr.children.toList match {
    case List(expr: ScExpression, ElementText("."), ElementText(_)) => Some(expr)
    case _ => None
  }

  private def guardCondition(condition: ScExpression, isInstOfCall: ScGenericCall): Option[ScExpression] =  {
    val conditions = separateConditions(condition)
    conditions match {
      case Nil => None
      case _ =>
        val equiv: (PsiElement, PsiElement) => Boolean = PsiEquivalenceUtil.areElementsEquivalent(_, _, comparator, false)
        val guardConditions: List[ScExpression] = conditions.filterNot(equiv(_, isInstOfCall))
        val guardConditionsText: String = guardConditions.map(_.getText).mkString(" && ")
        val guard = ScalaPsiElementFactory.createExpressionFromText(guardConditionsText, condition).asInstanceOf[ScExpression]

        if (guard == null) None
        else Some(guard)
    }
  }

  val comparator = new Comparator[PsiElement]() {
    def compare(element1: PsiElement, element2: PsiElement): Int = {
      if (element1 == element2) return 0
      if (element1.isInstanceOf[ScParameter] && element2.isInstanceOf[ScParameter]) {
        val name1 = element1.asInstanceOf[ScParameter].name
        val name2 = element2.asInstanceOf[ScParameter].name
        if (name1 != null && name2 != null) {
          return name1.compareTo(name2)
        }
      }
      1
    }
  }
}
