package org.jetbrains.plugins.scala.lang.psi.dataFlow.impl.reachingDefs

import _root_.org.jetbrains.plugins.scala.lang.psi.api.ScControlFlowOwner
import com.intellij.codeInsight.PsiEquivalenceUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiTreeUtil._
import com.intellij.psi.{PsiElement, PsiMethod, PsiNamedElement, PsiPackage}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFun, ScFunction, ScTypeAlias, ScValueDeclaration}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.controlFlow.Instruction
import org.jetbrains.plugins.scala.lang.psi.controlFlow.impl.{DefinitionInstruction, ReadWriteVariableInstruction}
import org.jetbrains.plugins.scala.lang.psi.dataFlow.DfaEngine
import org.jetbrains.plugins.scala.lang.psi.dataFlow.impl.reachingDefs.ReachingDefinitions._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticNamedElement

import scala.collection.mutable
import scala.jdk.CollectionConverters._

object ReachingDefinitionsCollector {

  def collectVariableInfo(fragment: collection.Seq[PsiElement], place: PsiElement): FragmentVariableInfos = {
    // CFG -> DFA
    val commonParent = findCommonParent(fragment.asJava)
    val cfowner = getParentOfType (
      if (commonParent.getContext != null) commonParent.getContext else commonParent,
      classOf[ScControlFlowOwner], false
    )
    if (cfowner == null) {
      val message = "cfowner == null: " + fragment.map(_.getText).mkString("(", ", ", ")") + "\n" + "files: " +
        fragment.map(_.getContainingFile.name).mkString("(", ", ", ")")
      throw new RuntimeException(message)
    }
    val cfg = cfowner.getControlFlow //todo: make cache more right to not get PsiInvalidAccess
    val engine = new DfaEngine(cfg, ReachingDefinitionsInstance, ReachingDefinitionsLattice)
    val dfaResult = engine.performDFA

    // instructions in given fragment
    val fragmentInstructions = filterByFragment(cfg, fragment)

    val inputInfos = computeInputVariables(fragmentInstructions).filter(info => !isVisible(info.element, place))
    val outputInfos = computeOutputVariables(fragmentInstructions, dfaResult)

    FragmentVariableInfos(inputInfos, outputInfos)
  }

  //defines if the given PsiNamedElement is visible at `place`
  private def isVisible(element: PsiNamedElement, place: PsiElement): Boolean = {
    def checkResolve(ref: PsiElement) = ref match {
      case r: ScReference =>
        r.multiResolveScala(false).map(_.getElement).exists(PsiEquivalenceUtil.areElementsEquivalent(_, element))
      case _ => false
    }
    val isInstanceMethod = element match {
      case fun: ScFunction => fun.isDefinedInClass
      case m: PsiMethod => !m.hasModifierPropertyScala("static")
      case _ => false
    }
    val isSynthetic = element match {
      case _: SyntheticNamedElement => true
      case fun: ScFunction => fun.isSynthetic
      case _ => false
    }
    import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createDeclarationFromText, createExpressionWithContextFromText}
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

  private def isInFragment(element: PsiElement, fragment: collection.Seq[PsiElement]) =
    fragment.exists(PsiTreeUtil.isAncestor(_, element, false))

  private def filterByFragment(cfg: Seq[Instruction], fragment: collection.Seq[PsiElement]) = cfg.filter {
    i => i.element.exists(isInFragment(_, fragment))
  }

  private def computeOutputVariables(innerInstructions: Seq[Instruction],
                                     dfaResult: mutable.Map[Instruction, RDSet]): Seq[VariableInfo] = {
    val result = mutable.SortedSet.empty[PsiNamedElement](Ordering.by(_.getTextRange.getStartOffset))
    for {
      (read@ReadWriteVariableInstruction(_, Some(definitionToRead), false), rdset) <- dfaResult
      if !innerInstructions.contains(read)
      reaching@DefinitionInstruction(named, _) <- rdset
      if named == definitionToRead
      if innerInstructions.contains(reaching)
    } {
      result += named
    }
    result.iterator.map(VariableInfo).toSeq
  }

  private def computeInputVariables(innerInstructions: Seq[Instruction]): Seq[VariableInfo] = {
    val isDefinedHere = innerInstructions.collect {
      case DefinitionInstruction(named, _) => named
    }.toSet[PsiNamedElement]

    innerInstructions
      .flatMap {
        case ReadWriteVariableInstruction(_, Some(definition), _) if !isDefinedHere(definition) =>
          definition match {
            case _: PsiPackage => None
            case _ => Some(definition)
          }
        case _ => None
      }
      .filter(_.isPhysical)
      .sortBy(_.getTextRange.getStartOffset)
      .distinct
      .map(VariableInfo)
  }
}

case class FragmentVariableInfos(inputVariables: Seq[VariableInfo],
                                 outputVariables: Seq[VariableInfo])

case class VariableInfo(element: PsiNamedElement)