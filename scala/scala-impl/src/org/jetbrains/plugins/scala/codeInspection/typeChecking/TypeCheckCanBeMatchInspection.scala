package org.jetbrains.plugins.scala
package codeInspection
package typeChecking

import com.intellij.codeInsight.PsiEquivalenceUtil
import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScExistentialClause, ScTypeElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaPsiElement, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticNamedElement
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester
import org.jetbrains.plugins.scala.lang.refactoring.util.{InplaceRenameHelper, ScalaVariableValidator}

import scala.annotation.{nowarn, tailrec}
import scala.collection.mutable

/**
 * Nikolay.Tropin
 * 5/6/13
 */
@nowarn("msg=" + AbstractInspection.DeprecationText)
final class TypeCheckCanBeMatchInspection extends AbstractInspection(TypeCheckCanBeMatchInspection.inspectionName) {

  import TypeCheckCanBeMatchInspection._

  override def actionFor(implicit holder: ProblemsHolder, isOnTheFly: Boolean): PartialFunction[PsiElement, Any] = {
    case IsInstanceOfCall(call) =>
      for {
        ifStmt <- Option(PsiTreeUtil.getParentOfType(call, classOf[ScIf]))
        condition <- ifStmt.condition
        if findIsInstanceOfCalls(condition, onlyFirst = true).contains(call)
        if typeCheckIsUsedEnough(ifStmt, call)
      } {
        val fix = new TypeCheckCanBeMatchQuickFix(call, ifStmt)
        holder.registerProblem(call, inspectionName, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, fix)
      }
  }

  private def typeCheckIsUsedEnough(ifStmt: ScIf, isInstOf: ScGenericCall): Boolean = {
    val chainSize = listOfIfAndIsInstOf(ifStmt, isInstOf, onlyFirst = true).size
    val typeCastsNumber = findAsInstanceOfCalls(ifStmt.condition, isInstOf).size + findAsInstanceOfCalls(ifStmt.thenExpression, isInstOf).size
    chainSize > 1 || typeCastsNumber > 0
  }

  private class TypeCheckCanBeMatchQuickFix(isInstOfUnderFix: ScGenericCall, ifStmt: ScIf)
    extends AbstractFixOnTwoPsiElements(inspectionName, isInstOfUnderFix, ifStmt) {

    override protected def doApplyFix(isInstOf: ScGenericCall, ifSt: ScIf)
                                     (implicit project: Project): Unit = {
      val (matchStmtOption, renameData) = buildMatchStmt(ifSt, isInstOf, onlyFirst = true)
      for (matchStmt <- matchStmtOption) {
        val newMatch = inWriteAction {
          ifSt.replaceExpression(matchStmt, removeParenthesis = true).asInstanceOf[ScMatch]
        }
        if (!ApplicationManager.getApplication.isUnitTestMode) {
          val renameHelper = new InplaceRenameHelper(newMatch)
          setElementsForRename(newMatch, renameHelper, renameData)
          renameHelper.startRenaming()
        }
      }
    }
  }

}

object TypeCheckCanBeMatchInspection {
  val inspectionId = "TypeCheckCanBeMatch"
  @Nls
  val inspectionName: String = ScalaInspectionBundle.message("type.check.can.be.replaced.by.pattern.matching")

  private type RenameData = mutable.ArrayBuffer[(Int, Seq[String])]

  def buildMatchStmt(ifStmt: ScIf, isInstOfUnderFix: ScGenericCall, onlyFirst: Boolean)
                    (implicit project: Project): (Option[ScMatch], RenameData) =
    baseExpr(isInstOfUnderFix) match {
      case Some(expr: ScExpression) =>
        val matchedExprText = expr.getText
        val (caseClausesText, renameData) = buildCaseClausesText(ifStmt, isInstOfUnderFix, onlyFirst)
        val matchStmtText = s"$matchedExprText match { \n " + caseClausesText + "}"
        val matchStmt = createExpressionFromText(matchStmtText).asInstanceOf[ScMatch]
        (Some(matchStmt), renameData)
      case _ => (None, null)
    }

  private def buildCaseClauseText(ifStmt: ScIf, isInstOfCall: ScGenericCall, caseClauseIndex: Int, renameData: RenameData): Option[String] = {
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

    val typeArgs = isInstOfCall.typeArgs.typeArgs
    for {
      condition <- ifStmt.condition
      if typeArgs.size == 1
    } yield {
      val typeElem = typeArgs.head
      val typeName0 = typeElem.getText
      val typeName = PsiTreeUtil.getChildOfType(typeElem, classOf[ScExistentialClause]) match {
        case null => typeName0
        case _ => "(" + typeName0 + ")"
      }

      val asInstOfInBody = findAsInstanceOfCalls(ifStmt.thenExpression, isInstOfCall)
      val guardCondition = findGuardCondition(condition)(equiv(_, isInstOfCall))
      val asInstOfInGuard = findAsInstanceOfCalls(guardCondition, isInstOfCall)
      val asInstOfEverywhere = asInstOfInBody ++ asInstOfInGuard

      implicit val project: Project = ifStmt.getProject
      val name = if (asInstOfInBody.count(checkAndStoreNameAndDef) == 0) {
        //no usage of asInstanceOf
        asInstOfEverywhere.toSeq match {
          case Seq() => "_"
          case _ =>
            //no named usage
            val validator = new ScalaVariableValidator(
              ifStmt,
              false,
              ifStmt.getParent,
              ifStmt.getParent
            )

            val suggestedNames = NameSuggester.suggestNames(asInstOfEverywhere.head, validator)
            val text = suggestedNames.head
            for {
              expression <- asInstOfEverywhere
              newExpr = createExpressionFromText(text)
            } inWriteAction {
              expression.replaceExpression(newExpr, removeParenthesis = true)
            }

            renameData.addOne((caseClauseIndex, suggestedNames))
            text
        }
      } else {
        inWriteAction(definition.get.delete())
        val text = definedName.get
        val newExpr = createExpressionFromText(text)
        inWriteAction {
          for {
            expression <- asInstOfEverywhere
          } expression.replaceExpression(newExpr, removeParenthesis = true)
        }

        text
      }

      buildCaseClauseText(name + " : " + typeName, guardCondition, ifStmt.thenExpression)
    }
  }

  private def buildCaseClauseText(patternText: String,
                                  guardCondition: Option[ScExpression],
                                  body: Option[ScExpression])
                                 (implicit project: Project): String = {
    val builder = new mutable.StringBuilder()
      .append("case ")
      .append(patternText)
    guardCondition
      .map(_.getText)
      .foreach(text => builder.append(" if ").append(text))
    builder.append(" ")
      .append(ScalaPsiUtil.functionArrow)
    val elements = body.toList.flatMap {
      case block: ScBlock =>
        for {
          element <- block.children.toList
          elementType = element.getNode.getElementType
          if elementType != ScalaTokenTypes.tLBRACE &&
            elementType != ScalaTokenTypes.tRBRACE
        } yield element
      case expression => expression :: Nil
    }
    elements
      .map(_.getText)
      .foreach(builder.append)

    if (!builder.last.isWhitespace) builder.append("\n")
    builder.toString
  }

  def listOfIfAndIsInstOf(currentIfStmt: ScIf, currentCall: ScGenericCall, onlyFirst: Boolean): List[(ScIf, ScGenericCall)] = {
    for (currentBase <- baseExpr(currentCall)) {
      currentIfStmt.elseExpression match {
        case Some(nextIfStmt: ScIf) =>
          for {
            nextCondition <- nextIfStmt.condition
            nextCall <- findIsInstanceOfCalls(nextCondition, onlyFirst)
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

  private def buildCaseClausesText(ifStmt: ScIf, isInstOfUnderFix: ScGenericCall, onlyFirst: Boolean): (String, RenameData) = {
    val builder = new mutable.StringBuilder()
    val (ifStmts, isInstOf) = listOfIfAndIsInstOf(ifStmt, isInstOfUnderFix, onlyFirst).unzip

    val renameData = new RenameData()
    for {
      index <- ifStmts.indices
      text <- buildCaseClauseText(ifStmts(index), isInstOf(index), index, renameData)
    } builder.append(text)

    if (ifStmts != Nil) {
      builder ++= buildCaseClauseText("_", None, ifStmts.last.elseExpression)(ifStmt.getProject)
    }

    (builder.toString(), renameData)
  }

  @tailrec
  def findIsInstanceOfCalls(condition: ScExpression, onlyFirst: Boolean = false): List[ScGenericCall] = condition match {
    case _ if !onlyFirst =>
      separateConditions(condition).collect {
        case IsInstanceOfCall(call) => call
      }
    case IsInstanceOfCall(call) => call :: Nil
    case IsConjunction(left, _) => findIsInstanceOfCalls(left, onlyFirst)
    case ScParenthesisedExpr(expression) => findIsInstanceOfCalls(expression, onlyFirst)
    case _ => Nil
  }

  private def findAsInstanceOfCalls(maybeBody: Option[ScExpression],
                                    isInstOfCall: ScGenericCall): Iterable[ScGenericCall] = maybeBody match {
    case Some(body) =>
      def baseAndType(call: ScGenericCall) = for {
        base <- baseExpr(call)

        typeElements = call.typeArgs.typeArgs
        if typeElements.size == 1
      } yield (base, typeElements.head.calcType)

      val result = mutable.ArrayBuffer.empty[ScGenericCall]
      val visitor = new ScalaRecursiveElementVisitor {

        override def visitGenericCallExpression(call: ScGenericCall): Unit = {
          val asInstanceOfCall = call.referencedExpr match {
            case ref: ScReferenceExpression if ref.refName == "asInstanceOf" => Option(ref.resolve())
            case _ => None
          }

          if (asInstanceOfCall.exists(_.is[SyntheticNamedElement])) {
            for {
              (base1, type1) <- baseAndType(isInstOfCall)
              (base2, type2) <- baseAndType(call)

              if type1.equiv(type2) && equiv(base1, base2)
            } result += call
          }

          super.visitGenericCallExpression(call)
        }
      }

      body.accept(visitor)
      result
    case _ => Seq.empty
  }

  def setElementsForRename(matchStmt: ScMatch, renameHelper: InplaceRenameHelper, renameData: RenameData): Unit = {
    val caseClauses = matchStmt.clauses.toList

    for {
      (index, suggestedNames) <- renameData
      caseClause = caseClauses(index)
      name = suggestedNames.head
    } {
      val primary = mutable.ArrayBuffer.empty[ScNamedElement]
      val dependents = mutable.SortedSet.empty[ScalaPsiElement](Ordering.by(_.getTextOffset))

      val patternVisitor = new ScalaRecursiveElementVisitor() {
        override def visitPattern(pat: ScPattern): Unit = {
          pat match {
            case bp: ScBindingPattern if bp.name == name =>
              primary += bp
            case _ =>
          }
          super.visitPattern(pat)
        }
      }

      val referenceVisitor = new ScalaRecursiveElementVisitor() {
        override def visitReferenceExpression(ref: ScReferenceExpression): Unit = {
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

  private def baseExpr(call: ScGenericCall) = call.referencedExpr match {
    case ref: ScReferenceExpression => ref.qualifier
    case _ => None
  }

  private def findGuardCondition(condition: ScExpression)
                                (predicate: ScExpression => Boolean): Option[ScExpression] =
    separateConditions(condition)
      .filterNot(predicate)
      .map(_.getText)
      .mkString(" && ") match {
      case text if text.isEmpty => None
      case text => Some(createExpressionFromText(text, condition))
    }

  private def equiv =
    PsiEquivalenceUtil.areElementsEquivalent(
      _: PsiElement,
      _: PsiElement, {
        case (left: PsiElement, right: PsiElement) if left == right => 0
        case (left: ScParameter, right: ScParameter) =>
          left.name match {
            case null => 1
            case leftName =>
              right.name match {
                case null => 1
                case rightName => leftName.compareTo(rightName)
              }
          }
        case _ => 1
      }: java.util.Comparator[PsiElement],
      false
    )

  private def separateConditions(expression: ScExpression) = {
    @tailrec
    def separateConditions(expressions: List[ScExpression],
                           accumulator: List[ScExpression]): List[ScExpression] = expressions match {
      case Nil => accumulator
      case head :: tail =>
        val (newExpressions, newAccumulator) = head match {
          case IsConjunction(left, right) => (left :: right :: tail, accumulator)
          case ScParenthesisedExpr(infixExpression: ScInfixExpr) => (infixExpression :: tail, accumulator)
          case ScParenthesisedExpr(call: ScGenericCall) => (tail, call :: accumulator)
          case _ => (tail, head :: accumulator)
        }
        separateConditions(newExpressions, newAccumulator)
    }

    separateConditions(List(expression), Nil)
  }
}
