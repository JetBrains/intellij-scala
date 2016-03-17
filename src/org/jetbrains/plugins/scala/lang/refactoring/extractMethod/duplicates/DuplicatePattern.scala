package org.jetbrains.plugins.scala.lang.refactoring.extractMethod.duplicates

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.refactoring.extractMethod.ExtractMethodParameter
import org.jetbrains.plugins.scala.lang.refactoring.extractMethod.duplicates.DuplicatesUtil._

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
 * Nikolay.Tropin
 * 2014-05-15
 */
class DuplicatePattern(val elements: Seq[PsiElement], parameters: Seq[ExtractMethodParameter]) {
  val paramOccurences = collectParamOccurences()
  val definitions = collectDefinitions()

  def collectDefinitions(): Seq[ScTypedDefinition] = {
    val buffer = ListBuffer[ScTypedDefinition]()
    val visitor = new ScalaRecursiveElementVisitor {
      override def visitFunctionDefinition(fun: ScFunctionDefinition) = {
        buffer += fun
        super.visitFunctionDefinition(fun)
      }

      override def visitPatternDefinition(pat: ScPatternDefinition) = {
        pat.bindings.foreach(buffer += _)
        super.visitPatternDefinition(pat)
      }

      override def visitVariableDefinition(varr: ScVariableDefinition) = {
        varr.bindings.foreach(buffer += _)
        super.visitVariableDefinition(varr)
      }
    }
    elements.foreach(_.accept(visitor))
    buffer.toSeq
  }

  def collectParamOccurences(): Map[ScReferenceExpression, ExtractMethodParameter] = {
    val buffer = mutable.Map[ScReferenceExpression, ExtractMethodParameter]()
    val visitor = new ScalaRecursiveElementVisitor {
      override def visitReferenceExpression(ref: ScReferenceExpression) = {
        parameters.find(_.fromElement == ref.resolve)
                .foreach(p => buffer += (ref -> p))
        super.visitReferenceExpression(ref)
      }
    }
    elements.foreach(_.accept(visitor))
    buffer.toMap
  }

  def isDuplicateStart(candidate: PsiElement)
                      (implicit typeSystem: TypeSystem): Option[DuplicateMatch] = {
    withFilteredForwardSiblings(candidate, elements.size) match {
      case Some(cands) =>
        if (cands.exists(isUnder(_, elements))) None
        else {
          val mtch = new DuplicateMatch(this, cands)
          if (mtch.isDuplicate) Some(mtch)
          else None
        }
      case _ => None
    }
  }

  def findDuplicates(scope: PsiElement)
                    (implicit typeSystem: TypeSystem): Seq[DuplicateMatch] = {
    val result = ListBuffer[DuplicateMatch]()
    val seen = mutable.Set[PsiElement]()
    val visitor = new ScalaRecursiveElementVisitor {
      override def visitElement(element: ScalaPsiElement) = {
        if (isSignificant(element)) {
          isDuplicateStart(element) match {
            case Some(mtch) if !seen(mtch.candidates(0)) =>
              result += mtch
              seen += mtch.candidates(0)
            case _ => super.visitElement(element)
          }
        }
      }
    }
    scope.acceptChildren(visitor)
    result.toSeq
  }
}
