package org.jetbrains.plugins.scala.lang.psi.dataFlow.impl.reachingDefs

import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import _root_.org.jetbrains.plugins.scala.lang.psi.api.ScControlFlowOwner
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.dataFlow.DfaEngine
import org.jetbrains.plugins.scala.lang.psi.controlFlow.Instruction
import collection.mutable.ArrayBuffer
import com.intellij.psi.{PsiPackage, PsiMethod, PsiNamedElement, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.controlFlow.impl.{DefinitionInstruction, ExtractMethodControlFlowPolicy, ReadWriteVariableInstruction}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValueDeclaration, ScTypeAlias, ScFun, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.extensions._
import com.intellij.codeInsight.PsiEquivalenceUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScObject}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticNamedElement
import com.intellij.psi.util.PsiTreeUtil._
import ReachingDefinitions._
import scala.collection.mutable

/**
 * @author ilyas
 */

object ReachingDefintionsCollector {
  
  def collectVariableInfo(fragment: Seq[PsiElement], place: ScalaPsiElement): FragmentVariableInfos = {
    // CFG -> DFA
    val commonParent = findCommonParent(fragment: _*)
    val cfowner = getParentOfType(commonParent, classOf[ScControlFlowOwner])
    if (cfowner == null) {
      val message = "cfowner == null: " + fragment.map(_.getText).mkString("(", ", ", ")") + "\n" + "files: " +
              fragment.map(_.getContainingFile.getName).mkString("(", ", ", ")")
      throw new RuntimeException(message)
    }
    val cfg = cfowner.getControlFlow(cached = false, policy = ExtractMethodControlFlowPolicy) //todo: make cache more right to not get PsiInvalidAccess
    val engine = new DfaEngine(cfg, ReachingDefinitionsInstance, ReachingDefinitionsLattice)
    val dfaResult = engine.performDFA

    // instructions in given fragment
    val fragmentInstructions = filterByFragment(cfg, fragment)
    
    val inputInfos = computeInputVaribles(fragmentInstructions).filter(info => !isVisible(info.element, place))
    val outputInfos = computeOutputVariables(fragmentInstructions, dfaResult)

    FragmentVariableInfos(inputInfos, outputInfos)
  }

  //defines if the given PsiNamedElement is visible at `place`
  private def isVisible(element: PsiNamedElement, place: ScalaPsiElement): Boolean = {
    def checkResolve(ref: PsiElement) = ref match {
      case r: ScReferenceElement =>
        r.multiResolve(false).map(_.getElement).exists(PsiEquivalenceUtil.areElementsEquivalent(_, element))
      case _ => false
    }
    val isInstanceMethod = element match {
      case fun: ScFunction => fun.isInstance
      case m: PsiMethod => !m.hasModifierPropertyScala("static")
      case _ => false
    }
    val isSynthetic = element match {
      case _: SyntheticNamedElement => true
      case fun: ScFunction => fun.isSynthetic
      case _ => false
    }
    import ScalaPsiElementFactory.{createExpressionWithContextFromText, createDeclarationFromText}
    val resolvesAtNewPlace = element match {
      case _: PsiMethod | _: ScFun =>
        checkResolve(createExpressionWithContextFromText(element.name + " _", place.getContext, place).getFirstChild)
      case _: ScObject =>
        checkResolve(createExpressionWithContextFromText(element.name, place.getContext, place))
      case _: ScTypeAlias | _: ScTypeDefinition =>
        val decl = createDeclarationFromText(s"val dummyVal: ${element.name}", place.getContext, place)
                .asInstanceOf[ScValueDeclaration]
        decl.typeElement match {
          case Some(st: ScSimpleTypeElement) => st.reference.exists(checkResolve)
          case _ => false
        }
      case _ =>
        checkResolve(createExpressionWithContextFromText(element.name, place.getContext, place))
    }
    isInstanceMethod || isSynthetic || resolvesAtNewPlace
  }

  private def isInFragment(element: PsiElement, fragment: Seq[PsiElement]) =
    fragment.exists(PsiTreeUtil.isAncestor(_, element, false))

  private def filterByFragment(cfg: Seq[Instruction], fragment: Seq[PsiElement]) = cfg.filter {
    i => i.element.exists(isInFragment(_, fragment))
  }

  def computeOutputVariables(innerInstructions: Seq[Instruction],
                             dfaResult: mutable.Map[Instruction, RDSet]): Iterable[VariableInfo] = {
    val buffer = new ArrayBuffer[PsiNamedElement]
    for {
      (read @ ReadWriteVariableInstruction(_, readRef, Some(definitionToRead), false), rdset) <- dfaResult
      if !innerInstructions.contains(read)
      reaching <- rdset
      if innerInstructions.contains(reaching)
    } {
      reaching match {
        case DefinitionInstruction(_, named, _) if !buffer.contains(named) && (named == definitionToRead) =>
          buffer += named
        case _ =>
      }
    }
    buffer.sortBy(_.getTextRange.getStartOffset).map(VariableInfo)
  }

  def computeInputVaribles(innerInstructions: Seq[Instruction]): Iterable[VariableInfo] = {
    val buffer = mutable.Set[PsiNamedElement]()
    val definedHere = innerInstructions.collect {
      case DefinitionInstruction(_, named, _) => named
    }
    innerInstructions.foreach {
      case ReadWriteVariableInstruction(_, _, Some(definition), _) if !definedHere.contains(definition) =>
        definition match {
          case _: PsiPackage =>
          case _ => buffer += definition
        }
      case _ =>
    }
    buffer.toSeq.sortBy(_.getTextRange.getStartOffset).map(VariableInfo)
  }


}

case class FragmentVariableInfos(inputVariables: Iterable[VariableInfo],
                                 outputVariables: Iterable[VariableInfo])

object FragmentVariableInfos {
  def empty = FragmentVariableInfos(Nil, Nil)
}

case class VariableInfo(element: PsiNamedElement)