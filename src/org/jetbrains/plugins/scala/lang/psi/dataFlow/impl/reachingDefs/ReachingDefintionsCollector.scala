package org.jetbrains.plugins.scala.lang.psi.dataFlow.impl.reachingDefs

import org.jetbrains.plugins.scala.lang.psi.api.base.{ScStableCodeReferenceElement, ScReferenceElement}
import _root_.org.jetbrains.plugins.scala.lang.psi.api.{ScalaRecursiveElementVisitor, ScControlFlowOwner}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.dataFlow.DfaEngine
import org.jetbrains.plugins.scala.lang.psi.controlFlow.Instruction
import collection.mutable.ArrayBuffer
import com.intellij.psi.{PsiClass, PsiMethod, PsiNamedElement, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.controlFlow.impl.{ReadWriteVariableInstruction, DefineValueInstruction}
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiUtil, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValueDeclaration, ScTypeAlias, ScFun, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr
import scala.collection.mutable
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.extensions._
import com.intellij.codeInsight.PsiEquivalenceUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScObject}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.{SyntheticNamedElement, ScSyntheticFunction}

/**
 * @author ilyas
 */

object ReachingDefintionsCollector {
  import ReachingDefinitions.{A => RDSet, _}

  /**
   * @param elements a fragment to analyze
   * @param place since Extract Method refactoring is in fact RDC's main client, it should define if we need to
   *                  use captured variable as an argument
   */
  def collectVariableInfo(elements: Seq[PsiElement], place: ScalaPsiElement): FragmentVariableInfos = {
    import PsiTreeUtil._
    // for every reference element, define if it's definition is in scope
    val inputInfos = getInputInfo(elements, place)

    // CFG -> DFA
    val commonParent = findCommonParent(elements: _*)
    val cfowner = getParentOfType(commonParent, classOf[ScControlFlowOwner])
    if (cfowner == null) {
      val message = "cfowner == null: " + elements.map(_.getText).mkString("(", ", ", ")") + "\n" + "files: " +
        elements.map(_.getContainingFile.getName).mkString("(", ", ", ")")
      throw new RuntimeException(message)
    }
    val cfg = cfowner.getControlFlow(cached = false) //todo: make cache more right to not get PsiInvalidAccess
    val engine = new DfaEngine(cfg, ReachingDefinitionsInstance, ReachingDefinitionsLattice)
    val dfaResult = engine.performDFA

    // instructions in given fragment
    val fragmentInstructions = filterByFragment(cfg, elements)

    // for every WRITE or VAL define, if it escapes `elements' or not
    // i.e. if there instructions with greater numbers, which are "reached" by WRITE
    // instructions from the fragment

    val outputInfos = computeOutputVariables(fragmentInstructions, dfaResult)

    // take into account scope
    // todo implement

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

  private def isInFragment(element: PsiElement, fragment: Seq[PsiElement]) = fragment.exists(_.isAncestorOf(element))

  private def filterByFragment(cfg: Seq[Instruction], fragment: Seq[PsiElement]) = cfg.filter(i =>
    i.element match {
      case None => false
      case Some(e) => isInFragment(e, fragment)
    })

  private def getInputInfo(elements: Seq[PsiElement], place: ScalaPsiElement): Iterable[VariableInfo] = {
    val inputDefs = new ArrayBuffer[VariableInfo]

    def isInClosure(elem: PsiElement) = {
      val parent = PsiTreeUtil.getParentOfType(elem, classOf[ScFunctionExpr], classOf[ScFunction])
      parent != null && isInFragment(parent, elements)
    }

    val visitor = new ScalaRecursiveElementVisitor {
      override def visitReference(ref: ScReferenceElement) {
        ref match {
          case _: ScStableCodeReferenceElement => super.visitReference(ref)
          case _ =>
            val element = ref.resolve()
            element match {
              case named: PsiNamedElement if !isInFragment(named, elements) && !isVisible(named, place) &&
                      !inputDefs.map(_.element).contains(named) =>
                val isReferenceParameter = isInClosure(ref) && ScalaPsiUtil.isLValue(ref)
                inputDefs += VariableInfo(named, isReferenceParameter)
              case _ => super.visitReference(ref)
            }
        }
      }
    }

    for (e <- elements if e.isInstanceOf[ScalaPsiElement]) e.asInstanceOf[ScalaPsiElement].accept(visitor)
    inputDefs
  }

  def computeOutputVariables(innerInstructions: Seq[Instruction],
                             dfaResult: scala.collection.mutable.Map[Instruction, RDSet]): Iterable[VariableInfo] = {
    val buffer = new ArrayBuffer[PsiNamedElement]
    for ((i@ReadWriteVariableInstruction(_, readRef, false), rdset) <- dfaResult if !innerInstructions.contains(i);
         reaching <- rdset if innerInstructions.contains(reaching)) {
      val definitionToRead = readRef.resolve()
      reaching match {
        case DefineValueInstruction(_, named, _)
          if !buffer.contains(named) && (named eq definitionToRead) => buffer += named
        case ReadWriteVariableInstruction(_, ref, true) => ref.resolve() match {
          case named: PsiNamedElement
            if !buffer.contains(named) && (named eq definitionToRead) => buffer += named
          case _ =>
        }
        case _ =>
      }
    }
    buffer.map(VariableInfo(_, isRef = false))
  }


}

case class FragmentVariableInfos(inputVariables: Iterable[VariableInfo],
                                 outputVariables: Iterable[VariableInfo])

object FragmentVariableInfos {
  def empty = FragmentVariableInfos(Nil, Nil)
}

/**
 * @param isRef local variable must be treated as reference parameter
 */
case class VariableInfo(element: PsiNamedElement, isRef: Boolean)