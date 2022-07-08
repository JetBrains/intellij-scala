package org.jetbrains.plugins.scala.lang.refactoring.extractMethod.duplicates

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaPsiElement, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.refactoring.extractMethod.ExtractMethodParameter
import org.jetbrains.plugins.scala.lang.refactoring.extractMethod.duplicates.DuplicatesUtil._
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

class DuplicatePattern(val elements: Seq[PsiElement], parameters: Seq[ExtractMethodParameter])
                      (implicit val projectContext: ProjectContext) {
  val paramOccurences: Map[ScReferenceExpression, ExtractMethodParameter] = collectParamOccurences()
  val definitions: Seq[ScTypedDefinition] = collectDefinitions()

  def collectDefinitions(): Seq[ScTypedDefinition] = {
    val buffer = ListBuffer[ScTypedDefinition]()
    val visitor = new ScalaRecursiveElementVisitor {
      override def visitFunctionDefinition(fun: ScFunctionDefinition): Unit = {
        buffer += fun
        super.visitFunctionDefinition(fun)
      }

      override def visitPatternDefinition(pat: ScPatternDefinition): Unit = {
        pat.bindings.foreach(buffer += _)
        super.visitPatternDefinition(pat)
      }

      override def visitVariableDefinition(varr: ScVariableDefinition): Unit = {
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
      override def visitReferenceExpression(ref: ScReferenceExpression): Unit = {
        parameters.find(_.fromElement == ref.resolve)
                .foreach(p => buffer += (ref -> p))
        super.visitReferenceExpression(ref)
      }
    }
    elements.foreach(_.accept(visitor))
    buffer.toMap
  }

  def isDuplicateStart(candidate: PsiElement): Option[DuplicateMatch] = {
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

  def findDuplicates(scope: PsiElement): Seq[DuplicateMatch] = {
    val result = ListBuffer[DuplicateMatch]()
    val seen = mutable.Set[PsiElement]()
    val visitor = new ScalaRecursiveElementVisitor {
      override def visitScalaElement(element: ScalaPsiElement): Unit = {
        if (isSignificant(element)) {
          isDuplicateStart(element) match {
            case Some(mtch) if !seen(mtch.candidates(0)) =>
              result += mtch
              seen += mtch.candidates(0)
            case _ => super.visitScalaElement(element)
          }
        }
      }
    }
    scope.acceptChildren(visitor)
    result.toSeq
  }
}
