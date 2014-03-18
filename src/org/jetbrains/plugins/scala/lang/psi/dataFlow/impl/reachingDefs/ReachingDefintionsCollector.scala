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

  def collectVariableInfo(elements: Seq[PsiElement], place: ScalaPsiElement): FragmentVariableInfos =
    collectVariableInfo(elements, visibilityFilter(place))
  /**
   * @param elements a fragment to analyze
   * @param isVisible since Extract Method refactoring is in fact RDC's main client, it should define if we need to
   *                  use captured variable as an argument
   */
  def collectVariableInfo(elements: Seq[PsiElement], isVisible: (PsiNamedElement) => Boolean): FragmentVariableInfos = {
    import PsiTreeUtil._
    val isInFragment = ancestorFilter(elements)
    // for every reference element, define is it's definition in scope
    val inputInfos = getInputInfo(elements, isInFragment, isVisible)

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
    val fragmentInstructions = filterByFragment(cfg, isInFragment)

    // for every WRITE or VAL define, if it escapes `elements' or not
    // i.e. if there instructions with greater numbers, which are "reached" by WRITE
    // instructions from the fragment

    val outputInfos = computeOutputVariables(fragmentInstructions, dfaResult)

    // take into account scope
    // todo implement

    FragmentVariableInfos(inputInfos, outputInfos)
  }

  private def ancestorFilter(elements: Seq[PsiElement]) = new ((PsiElement) => Boolean) {
    val cache: mutable.Map[PsiElement, Boolean] = new mutable.HashMap[PsiElement, Boolean]

    def apply(elem: PsiElement): Boolean = {
      if (elem == null) return false
      cache.getOrElseUpdate(elem, elements.exists(e => PsiTreeUtil.isAncestor(e, elem, false)))
    }
  }

  //defines if the given PsiNamedElement is visible at `place`
  private def visibilityFilter(place: ScalaPsiElement) = new ((PsiNamedElement) => Boolean) {
    val cache: mutable.Map[PsiElement, Boolean] = new mutable.HashMap[PsiElement, Boolean]

    def apply(elem: PsiNamedElement): Boolean = {
      def checkResolve(ref: PsiElement) = cache.getOrElseUpdate(ref, ref match {
        case r: ScReferenceElement =>
          r.multiResolve(false).map(_.getElement).exists(PsiEquivalenceUtil.areElementsEquivalent(_, elem))
        case _ => false
      })
      val isInstanceMethod = elem match {
        case fun: ScFunction => fun.isInstance && !fun.containingClass.isInstanceOf[ScObject]
        case m: PsiMethod => !m.hasModifierPropertyScala("static")
        case _ => false
      }
      val isSynthetic = elem.isInstanceOf[SyntheticNamedElement]
      import ScalaPsiElementFactory.{createExpressionWithContextFromText, createDeclarationFromText}
      val resolvesAtNewPlace = elem match {
        case _: PsiMethod | _: ScFun =>
          checkResolve(createExpressionWithContextFromText(elem.name + " _", place.getContext, place).getFirstChild)
        case _: ScTypeAlias | _: ScTypeDefinition =>
          val decl = createDeclarationFromText(s"val dummyVal: ${elem.name}", place.getContext, place).asInstanceOf[ScValueDeclaration]
          decl.typeElement match {
            case Some(st: ScSimpleTypeElement) => st.reference.exists(checkResolve)
            case _ => false
          }
        case _ =>
          checkResolve(createExpressionWithContextFromText(elem.name, place.getContext, place))
      }
      isInstanceMethod || isSynthetic || resolvesAtNewPlace
    }
  }

  private def filterByFragment(cfg: Seq[Instruction], checker: (PsiElement) => Boolean) = cfg.filter(i =>
    i.element match {
      case None => false
      case Some(e) => checker(e)
    })

  private def getInputInfo(elements: Seq[PsiElement],
                           isInFragment: (PsiElement) => Boolean,
                           isVisible: (PsiNamedElement) => Boolean): Iterable[VariableInfo] = {
    val inputDefs = new ArrayBuffer[VariableInfo]

    def isInClosure(elem: PsiElement) = {
      val parent = PsiTreeUtil.getParentOfType(elem, classOf[ScFunctionExpr], classOf[ScFunction])
      parent != null && isInFragment(parent)
    }

    val visitor = new ScalaRecursiveElementVisitor {
      override def visitReference(ref: ScReferenceElement) {
        ref match {
          case _: ScStableCodeReferenceElement => super.visitReference(ref)
          case _ =>
            val element = ref.resolve()
            element match {
              case named: PsiNamedElement if !isInFragment(named) && !isVisible(named) &&
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