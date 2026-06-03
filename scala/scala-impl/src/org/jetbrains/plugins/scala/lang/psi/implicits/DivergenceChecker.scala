package org.jetbrains.plugins.scala.lang.psi.implicits

import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, ReplaceWith}
import org.jetbrains.plugins.scala.lang.psi.types.{Context, ScAbstractType, ScCompoundType, ScExistentialArgument, ScExistentialType, ScType}
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.UnloadableThreadLocal

import scala.collection.immutable.HashMap

class DivergenceInfo private (val coreType: ScType) {
  lazy val complexity: Int = DivergenceChecker.complexity(coreType)
  lazy val topLevelTypeConstructors: Set[ScType] = DivergenceChecker.topLevelTypeConstructors(coreType)
  lazy val coveringSet: Set[PsiNamedElement] = DivergenceChecker.coveringSet(coreType)

  def dominates(other: DivergenceInfo): Boolean = {
    complexity > other.complexity &&
      topLevelTypeConstructors.exists(other.topLevelTypeConstructors.contains) &&
      coveringSet == other.coveringSet
  }

  def equivOrDominates(other: DivergenceInfo)(implicit context: Context): Boolean = {
    coreType.conforms(other.coreType) || this.dominates(other)
  }
}

object DivergenceInfo {
  def apply(tp: ScType): DivergenceInfo =
    new DivergenceInfo(coreType(tp))


  private def abstractsToUpper(tp: ScType): ScType = {
    val noAbstracts = tp.updateLeaves {
      case ScAbstractType(_, _, upper) => upper
    }

    noAbstracts.removeAliasDefinitions()
  }

  private def coreType(tp: ScType): ScType = {
    implicit val projectCtx: ProjectContext = tp.projectContext
    tp match {
      case ScCompoundType(comps, _, _) => abstractsToUpper(ScCompoundType(comps, Map.empty, Map.empty)).removeUndefines()
      case ScExistentialType(quant, _) => abstractsToUpper(ScExistentialType(quant.recursiveUpdate {
        case arg: ScExistentialArgument => ReplaceWith(arg.upper)
        case _ => ProcessSubtypes
      })).removeUndefines()
      case _ => abstractsToUpper(tp).removeUndefines()
    }
  }
}

/**
 * For specification details see https://scala-lang.org/files/archive/spec/2.13/07-implicits.html
 */
object DivergenceChecker {
  type DivergenceStack = HashMap[PsiElement, List[DivergenceInfo]]

  private val threadLocalStack: UnloadableThreadLocal[DivergenceStack] =
    new UnloadableThreadLocal(HashMap.empty)

  def currentStack: DivergenceStack = threadLocalStack.value

  def withDivergenceStackOpt[T](divergenceStack: Option[DivergenceStack])(body: => T): T = {
    divergenceStack match {
      case Some(stack) => withDivergenceStack(stack)(body)
      case None => body
    }
  }

  def withDivergenceStack[T](divergenceStack: DivergenceStack)(body: => T): T = {
    val old = threadLocalStack.value
    threadLocalStack.value = divergenceStack
    try body
    finally threadLocalStack.value = old
  }

  def withDivergenceCheck[T](element: PsiElement, tp: ScType, onDivergence: => T)(body: => T): T = {
    implicit val context: Context = Context(element)

    val info = DivergenceInfo(tp)
    val stack = threadLocalStack.value

    stack.get(element) match {
      case Some(prevInfos) =>
        val isDivergent = prevInfos.exists(info.equivOrDominates)
        if (isDivergent) {
          return onDivergence
        }
        threadLocalStack.value = stack.updated(element, info :: prevInfos)
      case None =>
        threadLocalStack.value = stack + (element -> List(info))
    }

    try body
    finally {
      threadLocalStack.value = stack
    }
  }

  def topLevelTypeConstructors(tp: ScType): Set[ScType] = {
    tp match {
      case ScProjectionType(_, element) => Set(ScDesignatorType(element))
      case ParameterizedType(designator, _) => Set(designator)
      case tp@ScDesignatorType(_: ScObject) => Set(tp)
      case ScDesignatorType(v: ScTypedDefinition) =>
        val valueType: ScType = v.`type`().getOrAny
        topLevelTypeConstructors(valueType)
      case ScCompoundType(comps, _, _) => comps.flatMap(topLevelTypeConstructors).toSet
      case _ => Set(tp)
    }
  }

  def complexity(tp: ScType): Int = {
    tp match {
      case ScProjectionType(proj, _)     => 1 + complexity(proj)
      case ParameterizedType(_, args)    => 1 + args.foldLeft(0)(_ + complexity(_))
      case ScExistentialType(quant, _)   => 1 + complexity(quant)
      case ScDesignatorType(_: ScObject) => 1
      case ScDesignatorType(v: ScTypedDefinition) =>
        val valueType: ScType = v.`type`().getOrAny
        1 + complexity(valueType)
      case ScCompoundType(comps, _, _) => comps.foldLeft(0)(_ + complexity(_))
      case _                           => 1
    }
  }

  def coveringSet(coreTp: ScType): Set[PsiNamedElement] = {
    val designators = Set.newBuilder[PsiNamedElement]

    def extract(tp: ScType): Unit = tp match {
      case ParameterizedType(designator, args) =>
        extract(designator)
        args.foreach(extract)
      case ScCompoundType(comps, _, _) => comps.foreach(extract)
      case ScExistentialType(quant, wildcards) => extract(quant)
        wildcards.iterator
          .map(_.upper)
          .filterNot(_.isAny)
          .foreach(extract)
      case ScDesignatorType(designator) => designators += designator
      case _ => tp.extractDesignated(expandAliases = true).foreach(designators += _)
    }

    extract(coreTp)
    //println(s"$coreTp: ${designators.result().toSeq.map(_.getName).sorted.mkString(",")}")
    designators.result()
  }
}
