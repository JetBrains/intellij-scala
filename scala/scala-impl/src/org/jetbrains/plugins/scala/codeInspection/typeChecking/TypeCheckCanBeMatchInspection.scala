package org.jetbrains.plugins.scala
package codeInspection.typeChecking

import java.util.Comparator

import com.intellij.codeInsight.PsiEquivalenceUtil
import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.codeInspection.typeChecking.TypeCheckCanBeMatchInspection.{inspectionId, inspectionName}
import org.jetbrains.plugins.scala.codeInspection.typeChecking.TypeCheckToMatchUtil._
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnTwoPsiElements, AbstractInspection}
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, inWriteAction}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScExistentialClause, ScTypeElement, ScTypeElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticNamedElement
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester
import org.jetbrains.plugins.scala.lang.refactoring.util.{InplaceRenameHelper, ScalaVariableValidator}

import scala.annotation.tailrec
import scala.collection.mutable

/**
 * Nikolay.Tropin
 * 5/6/13
 */
object TypeCheckCanBeMatchInspection {
  val inspectionId = "TypeCheckCanBeMatch"
  val inspectionName = "Type check can be replaced by pattern matching"
}

class TypeCheckCanBeMatchInspection extends AbstractInspection(inspectionId, inspectionName) {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case IsInstanceOfCall(call) =>
      for {
        ifStmt <- Option(PsiTreeUtil.getParentOfType(call, classOf[ScIfStmt]))
        condition <- ifStmt.condition
        iioCall <- findIsInstanceOfCalls(condition, onlyFirst = true)
        if iioCall == call
        if typeCheckIsUsedEnough(ifStmt, call)
      } {
        val fix = new TypeCheckCanBeMatchQuickFix(call, ifStmt)
        holder.registerProblem(call, inspectionName, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, fix)
      }
  }

  private def typeCheckIsUsedEnough(ifStmt: ScIfStmt, isInstOf: ScGenericCall): Boolean = {
    val chainSize = listOfIfAndIsInstOf(ifStmt, isInstOf, onlyFirst = true).size
    val typeCastsNumber = findAsInstOfCalls(ifStmt.condition, isInstOf).size + findAsInstOfCalls(ifStmt.thenBranch, isInstOf).size
    chainSize > 1 || typeCastsNumber > 0
  }
}

class TypeCheckCanBeMatchQuickFix(isInstOfUnderFix: ScGenericCall, ifStmt: ScIfStmt)
        extends AbstractFixOnTwoPsiElements(inspectionName, isInstOfUnderFix, ifStmt) {


  override protected def doApplyFix(isInstOf: ScGenericCall, ifSt: ScIfStmt)
                                   (implicit project: Project): Unit = {
    val (matchStmtOption, renameData) = buildMatchStmt(ifSt, isInstOf, onlyFirst = true)
    for (matchStmt <- matchStmtOption) {
      val newMatch = inWriteAction {
        ifSt.replaceExpression(matchStmt, removeParenthesis = true).asInstanceOf[ScMatchStmt]
      }
      if (!ApplicationManager.getApplication.isUnitTestMode) {
        val renameHelper = new InplaceRenameHelper(newMatch)
        setElementsForRename(newMatch, renameHelper, renameData)
        renameHelper.startRenaming()
      }
    }
  }
}

object TypeCheckToMatchUtil {
  type RenameData = collection.mutable.ArrayBuffer[(Int, Seq[String])]

  def buildMatchStmt(ifStmt: ScIfStmt, isInstOfUnderFix: ScGenericCall, onlyFirst: Boolean)
                    (implicit project: Project): (Option[ScMatchStmt], RenameData) =
    baseExpr(isInstOfUnderFix) match {
      case Some(expr: ScExpression) =>
        val matchedExprText = expr.getText
        val (caseClausesText, renameData) = buildCaseClausesText(ifStmt, isInstOfUnderFix, onlyFirst)
        val matchStmtText = s"$matchedExprText match { \n " + caseClausesText + "}"
        val matchStmt = createExpressionFromText(matchStmtText).asInstanceOf[ScMatchStmt]
        (Some(matchStmt), renameData)
      case _ => (None, null)
    }

  private def buildCaseClauseText(ifStmt: ScIfStmt, isInstOf: ScGenericCall, caseClauseIndex: Int, renameData: RenameData): Option[String] = {
    var definedName: Option[String] = None
    var definition: Option[ScPatternDefinition] = None

    //method for finding and saving named type cast
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
                definedName = Some(bindings.head.getName)
                definition = Some(patternDef)
                true
            }
          } else false
        case null => false
      }
    }

    def typeNeedParentheses(typeElem: ScTypeElement): Boolean = {
      PsiTreeUtil.getChildOfType(typeElem, classOf[ScExistentialClause]) != null
    }

    for {
      args <- isInstOf.typeArgs
      condition <- ifStmt.condition
      if args.typeArgs.size == 1
    } yield {
      val typeElem = args.typeArgs.head
      val typeName0 = typeElem.getText
      val typeName =
        if (typeNeedParentheses(typeElem)) s"($typeName0)"
        else typeName0
      val asInstOfInBody = findAsInstOfCalls(ifStmt.thenBranch, isInstOf)
      val guardCond = guardCondition(condition, isInstOf)
      val asInstOfInGuard = findAsInstOfCalls(guardCond, isInstOf)
      val asInstOfEverywhere = asInstOfInBody ++ asInstOfInGuard

      implicit val projectContext = ifStmt.projectContext
      if (asInstOfInBody.count(checkAndStoreNameAndDef) == 0) {
        //no usage of asInstanceOf
        if (asInstOfEverywhere.isEmpty) {
          buildCaseClauseText("_ : " + typeName, guardCond, ifStmt.thenBranch, ifStmt.getProject)
        }
        //no named usage
        else {
          val suggestedNames = NameSuggester.suggestNames(asInstOfEverywhere.head)(
            new ScalaVariableValidator(ifStmt, false, ifStmt.getParent, ifStmt.getParent)
          )
          val name = suggestedNames.head
          asInstOfEverywhere.foreach { c =>
            val newExpr = createExpressionFromText(name)
            inWriteAction {
              c.replaceExpression(newExpr, removeParenthesis = true)
            }
          }

          renameData += ((caseClauseIndex, suggestedNames.toSeq))
          buildCaseClauseText(s"$name : $typeName", guardCond, ifStmt.thenBranch, ifStmt.getProject)
        }
      }
      //have named usage, use this name in case clause pattern definition
      else {
        //deleting unnecessary val declaration
        val patternDef = definition.get
        inWriteAction {
          patternDef.delete()
        }
        val name = definedName.get
        val newExpr = createExpressionFromText(name)
        inWriteAction {
          asInstOfEverywhere.foreach(_.replaceExpression(newExpr, removeParenthesis = true))
        }
        buildCaseClauseText(s"$name : $typeName", guardCond, ifStmt.thenBranch, ifStmt.getProject)
      }
    }
  }

  private def buildDefaultCaseClauseText(body: Option[ScExpression], project: Project): Option[String] =  {
    Some(buildCaseClauseText("_ ", None, body, project))
  }

  private def buildCaseClauseText(patternText: String, guardCondition: Option[ScExpression],
                                  body: Option[ScExpression], project: Project): String = {
    val builder = new StringBuilder
    builder.append("case ").append(patternText)
    guardCondition.map(cond => builder.append(" if " + cond.getText))
    val arrow = ScalaPsiUtil.functionArrow(project)
    builder.append(s" $arrow")
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
    if (!builder.last.isWhitespace) builder.append("\n")
    builder.toString()
  }

  def listOfIfAndIsInstOf(currentIfStmt: ScIfStmt, currentCall: ScGenericCall, onlyFirst: Boolean): List[(ScIfStmt, ScGenericCall)] = {
    for (currentBase <- baseExpr(currentCall)) {
      currentIfStmt.elseBranch match {
        case Some(nextIfStmt: ScIfStmt) =>
          for {
            nextCond <- nextIfStmt.condition
            nextCall <- findIsInstanceOfCalls(nextCond, onlyFirst)
            nextBase <- baseExpr(nextCall)
            if equiv(currentBase, nextBase)
          } {
            return (currentIfStmt, currentCall) :: listOfIfAndIsInstOf(nextIfStmt, nextCall, onlyFirst)
          }
          return (currentIfStmt, currentCall) :: Nil
        case _ => return (currentIfStmt, currentCall) :: Nil
      }
    }
    Nil
  }

  private def buildCaseClausesText(ifStmt: ScIfStmt, isInstOfUnderFix: ScGenericCall, onlyFirst: Boolean): (String, RenameData) = {
    implicit val project = ifStmt.getProject

    val builder = new StringBuilder
    val (ifStmts, isInstOf) = listOfIfAndIsInstOf(ifStmt, isInstOfUnderFix, onlyFirst).unzip

    val renameData = new RenameData()
    for {
      index <- ifStmts.indices
      text <- buildCaseClauseText(ifStmts(index), isInstOf(index), index, renameData)
    } {
      builder.append(text)
    }

    if (ifStmts != Nil) {
      val lastElse = ifStmts.last.elseBranch
      val defaultText: Option[String] = buildDefaultCaseClauseText(lastElse, project)
      defaultText.foreach(builder.append)
    }

    (builder.toString(), renameData)
  }

  @tailrec
  def findIsInstanceOfCalls(condition: ScExpression, onlyFirst: Boolean): List[ScGenericCall] = {
    if (onlyFirst) {
      condition match {
        case IsInstanceOfCall(call) => List(call)
        case infixExpr: ScInfixExpr if infixExpr.operation.refName == "&&" => findIsInstanceOfCalls(infixExpr.left, onlyFirst)
        case parenth: ScParenthesisedExpr => findIsInstanceOfCalls(parenth.innerElement.orNull, onlyFirst)
        case _ => Nil
      }
    }
    else {
      separateConditions(condition).collect {case IsInstanceOfCall(call) => call}
    }
  }

  def findAsInstOfCalls(body: Option[ScExpression], isInstOfCall: ScGenericCall): Seq[ScGenericCall] = {

    def isAsInstOfCall(genCall: ScGenericCall) = {
      genCall.referencedExpr match {
        case ref: ScReferenceExpression if ref.refName == "asInstanceOf" =>
          ref.resolve() match {
            case _: SyntheticNamedElement => true
            case _ => false
          }
        case _ => false
      }
    }

    def equalTypes(firstCall: ScGenericCall, secondCall: ScGenericCall): Boolean = {
      val option = for {
        firstArgs <- firstCall.typeArgs
        secondArgs <- secondCall.typeArgs
        firstTypes = firstArgs.typeArgs
        secondTypes = secondArgs.typeArgs
        if firstTypes.size == 1 && secondTypes.size == 1
      } yield {
        val firstType = firstTypes.head.calcType
        val secondType = secondTypes.head.calcType
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
          if equiv(base1, base2)
        } {
          result += call
        }
        super.visitGenericCallExpression(call)
      }
    }

    for (expr <- body) expr.accept(visitor)
    result
  }

  def setElementsForRename(matchStmt: ScMatchStmt, renameHelper: InplaceRenameHelper, renameData: RenameData) {
    val caseClauses = matchStmt.caseClauses.toList

    for {
      (index, suggestedNames) <- renameData
      caseClause = caseClauses(index)
      name = suggestedNames.head
    } {
      val primary = mutable.ArrayBuffer[ScNamedElement]()
      val dependents = mutable.SortedSet()(Ordering.by[ScalaPsiElement, Int](_.getTextOffset))

      val patternVisitor = new ScalaRecursiveElementVisitor() {
        override def visitPattern(pat: ScPattern) {
          pat match {
            case bp: ScBindingPattern if bp.name == name =>
              primary += bp
            case _ =>
          }
          super.visitPattern(pat)
        }
      }

      val referenceVisitor = new ScalaRecursiveElementVisitor() {
        override def visitReferenceExpression(ref: ScReferenceExpression) {
          for (prim <- primary) {
            if (ref.refName == name && ref.resolve() == prim)
              dependents += ref
          }
          super.visitReferenceExpression(ref)
        }
      }

      caseClause.accept(patternVisitor)
      caseClause.accept(referenceVisitor)
      for (prim <- primary) renameHelper.addGroup(prim, dependents.toSeq, suggestedNames)
    }
  }

  def baseExpr(gCall: ScGenericCall): Option[ScExpression] = gCall.referencedExpr match {
    case ref: ScReferenceExpression => ref.qualifier
    case _ => None
  }

  private def guardCondition(condition: ScExpression, isInstOfCall: ScGenericCall): Option[ScExpression] =  {
    val conditions = separateConditions(condition)
    conditions match {
      case Nil => None
      case _ =>
        val guardConditions: List[ScExpression] = conditions.filterNot(equiv(_, isInstOfCall))
        val guardConditionsText: String = guardConditions.map(_.getText).mkString(" && ")
        val guard = createExpressionFromText(guardConditionsText, condition).asInstanceOf[ScExpression]

        Option(guard)
    }
  }

  def equiv(elem1: PsiElement, elem2: PsiElement): Boolean = {
    val comparator = new Comparator[PsiElement]() {
      def compare(element1: PsiElement, element2: PsiElement): Int = {
        if (element1 == element2) return 0
        (element1, element2) match {
          case (par1: ScParameter, par2: ScParameter) =>
            val name1 = par1.name
            val name2 = par2.name
            if (name1 != null && name2 != null) name1.compareTo(name2)
            else 1
          case _ => 1
        }
      }
    }
    PsiEquivalenceUtil.areElementsEquivalent(elem1, elem2, comparator, false)
  }

  def separateConditions(expr: ScExpression): List[ScExpression] = {
    expr match {
      case parenth: ScParenthesisedExpr => parenth.innerElement match {
        case Some(infixExpr: ScInfixExpr) if infixExpr.operation.refName == "&&" =>
          separateConditions(infixExpr.left) ::: separateConditions(infixExpr.right) ::: Nil
        case genCall: ScGenericCall => genCall :: Nil
        case _ => parenth :: Nil
      }
      case infixExpr: ScInfixExpr if infixExpr.operation.refName == "&&" =>
        separateConditions(infixExpr.left) ::: separateConditions(infixExpr.right) ::: Nil
      case _ => expr :: Nil
    }
  }
}
