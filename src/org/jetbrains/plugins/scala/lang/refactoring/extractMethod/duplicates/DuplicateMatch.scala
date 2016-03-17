package org.jetbrains.plugins.scala.lang.refactoring.extractMethod.duplicates

import com.intellij.codeInsight.PsiEquivalenceUtil
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral, ScReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}
import org.jetbrains.plugins.scala.lang.refactoring.extractMethod.duplicates.DuplicatesUtil._
import org.jetbrains.plugins.scala.lang.refactoring.extractMethod.{ExtractMethodOutput, ExtractMethodParameter}

import scala.collection.mutable


/**
 * Nikolay.Tropin
 * 2014-05-15
 */
class DuplicateMatch(pattern: DuplicatePattern, val candidates: Seq[PsiElement])
                    (implicit val typeSystem: TypeSystem) {
  private val parameterValues = mutable.Map[ExtractMethodParameter, ScExpression]()
  private val definitionCorrespondence = mutable.Map[ScTypedDefinition, ScTypedDefinition]()

  def isDuplicate: Boolean = checkElementSeq(pattern.elements, candidates)

  def parameterText(param: ExtractMethodParameter) = parameterValues.get(param) match {
    case Some(e) => e.getText
    case None => throw new IllegalStateException(s"Could not find value of the parameter ${param.newName}")
  }

  def outputName(output: ExtractMethodOutput) = definitionCorrespondence.get(output.fromElement) match {
    case Some(td) => td.name
    case None => output.paramName
  }

  def textRange = candidates.head.getTextRange.union(candidates.last.getTextRange)

  private def checkElementSeq(subPatterns: Seq[PsiElement], subCandidates: Seq[PsiElement]): Boolean = {
    val filteredP = filtered(subPatterns)
    val filteredC = filtered(subCandidates)
    if (filteredC.size != filteredP.size) return false
    if (filteredP.isEmpty) return true
    filteredP.zip(filteredC).forall {
      case (e1, e2) => checkElement(e1, e2)
      case _ => false
    }
  }

  private def checkChildren(subPattern: PsiElement, subCandidate: PsiElement): Boolean = {
    checkElementSeq(subPattern.children.toSeq, subCandidate.children.toSeq)
  }

  private def checkElement(subPattern: PsiElement, candidate: PsiElement): Boolean = {
    if (!canBeEquivalent(subPattern, candidate)) return false

    (subPattern, candidate) match {
      case (td: ScTypedDefinition, tdCand: ScTypedDefinition) =>
        if (checkChildren(td, tdCand)) {
          definitionCorrespondence += (td -> tdCand)
          true
        } else false
      case (ref: ScReferenceExpression, expr: ScExpression) if pattern.paramOccurences.contains(ref) =>
        val p = pattern.paramOccurences(ref)
        val paramValue = parameterValues.getOrElseUpdate(p, expr)
        PsiEquivalenceUtil.areElementsEquivalent(paramValue, expr) && typesEquiv(ref, expr)
      case Both(
      (ref1: ScReferenceExpression, ref2: ScReferenceExpression),
      (ResolvesTo(td1: ScTypedDefinition), ResolvesTo(td2: ScTypedDefinition)))
        if pattern.definitions.contains(td1) =>
        definitionCorrespondence.get(td1).contains(td2) && typesEquiv(ref1, ref2)
      case Both((ref1: ScReferenceElement, ref2: ScReferenceElement), (ResolvesTo(res1), ResolvesTo(res2)))
        if res1 != res2 =>
        (res1, res2) match {
          case (sf1: ScSyntheticFunction, sf2: ScSyntheticFunction) => sf1.isStringPlusMethod && sf2.isStringPlusMethod
          case _ => false
        }
      case (intd1: ScInterpolatedStringLiteral, intd2: ScInterpolatedStringLiteral) => checkChildren(intd1, intd2)
      case (ElementType(ScalaTokenTypes.tINTERPOLATED_STRING), ElementType(ScalaTokenTypes.tINTERPOLATED_STRING)) =>
        subPattern.getText == candidate.getText
      case (lit1: ScLiteral, lit2: ScLiteral) => lit1.getValue == lit2.getValue
      case _ => checkChildren(subPattern, candidate)
    }
  }

  private def typesEquiv(expr1: ScExpression, expr2: ScExpression) = {
    (expr1.getType(), expr2.getType()) match {
      case (Success(t1, _), Success(t2, _)) =>
        def extractFromSingletonType(t: ScType) =
          if (ScType.isSingletonType(t)) ScType.extractDesignatorSingletonType(t)
          else Some(t)
        val Seq(newTp1, newTp2) = Seq(t1, t2).map(extractFromSingletonType)
        newTp1.zip(newTp2).forall {
          case (tp1, tp2) => tp1.equiv(tp2)
        }
      case (Failure(_, _), Failure(_, _)) => true
      case _ => false
    }
  }

}
