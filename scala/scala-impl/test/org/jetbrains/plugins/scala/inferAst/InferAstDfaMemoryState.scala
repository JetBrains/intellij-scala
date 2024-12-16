package org.jetbrains.plugins.scala.inferAst

import com.intellij.codeInspection.dataFlow.memory.{DfaMemoryState, DfaMemoryStateImpl}
import com.intellij.codeInspection.dataFlow.types.DfTypes
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory

import scala.collection.mutable

sealed abstract class AstAction
object AstAction {
  case class Token(token: String) extends AstAction
  case class Mark(index: Int) extends AstAction
  case class Done(index: Int, elementType: String) extends AstAction
  case class Call(analysisItem: AnalysisItem, result: Option[Boolean]) extends AstAction {
    override def toString: String = s"Call(${analysisItem.obj.name}::${analysisItem.method.name}, ${result.fold("None")(_.toString)})"
  }
  case class Collapse(index: Int, token: String) extends AstAction
  case class Rollback(index: Int) extends AstAction
  case class Drop(index: Int) extends AstAction
  case class Precede(oldIndex: Int, newIndex: Int) extends AstAction
  case class Error(error: String) extends AstAction
  case object Empty extends AstAction {
    override def toString: String = "<>"
  }
}

trait ActionPos
case object StartActionPos extends ActionPos
case class IndexActionPos(index: Int) extends ActionPos
case class AfterCallActionPos(index: Int, result: Boolean) extends ActionPos

class InferAstDfaMemoryState private(prev: DfaMemoryStateImpl) extends DfaMemoryStateImpl(prev) {
  private var lastActions: Set[ActionPos] = Set(StartActionPos)
  private var actions: Map[ActionPos, Set[AstAction]] = Map.empty
  private var predecessors: Map[ActionPos, Set[ActionPos]] = Map.empty

  def this(prev: InferAstDfaMemoryState) = {
    this(prev: DfaMemoryStateImpl)

    // copy our stuff
    lastActions = prev.lastActions
    actions = prev.actions
    predecessors = prev.predecessors
  }

  override def createCopy(): InferAstDfaMemoryState = new InferAstDfaMemoryState(this)

  override def afterMerge(that: DfaMemoryState): Unit = {
    super.afterMerge(that)
    val other = that.asInstanceOf[InferAstDfaMemoryState]
    mergeActions(other)
  }

  override def tryJoinExactly(other: DfaMemoryState): DfaMemoryState = {
    other match {
      case other: InferAstDfaMemoryState if isEmptyStack =>
        val result = super.tryJoinExactly(other)
        if (result eq this) this.mergeActions(other)
        else if (result eq other) other.mergeActions(this)
        result
      case _ =>
        null
    }
  }

  override def merge(that: DfaMemoryState): Unit = super.merge(that)

  private def mergeActions(other: InferAstDfaMemoryState): Unit = {
    def mergeMap[K, T](a: Map[K, Set[T]], b: Map[K, Set[T]]): Map[K, Set[T]] =
      (a.keySet | b.keySet).iterator
        .map { key => key -> (a.getOrElse(key, Set.empty) | b.getOrElse(key, Set.empty)) }
        .toMap

    lastActions = lastActions | other.lastActions
    actions = mergeMap(actions, other.actions)
    predecessors = mergeMap(predecessors, other.predecessors)
  }

  override def hashCode(): Int =
    super.hashCode() +
      7 * lastActions.hashCode() +
      11 * actions.hashCode() +
      13 * predecessors.hashCode()

  override def equals(obj: Any): Boolean = obj match {
    case other: InferAstDfaMemoryState =>
      super.equals(other) && other.lastActions == lastActions && other.actions == actions && other.predecessors == predecessors
    case _ => false
  }

  override def isSuperStateOf(other: DfaMemoryState): Boolean = other match {
    case other: InferAstDfaMemoryState =>
      super.isSuperStateOf(other) && other.lastActions == lastActions && other.actions == actions && other.predecessors == predecessors
    case _ =>
      false
  }


  def addAction(pos: ActionPos, action: AstAction): Unit =
    addActions(pos, List(action))

  def addActions(pos: ActionPos, actions: IterableOnce[AstAction]): Unit = {
    val actionIt = actions.iterator
    if (!actionIt.hasNext) return
    predecessors += pos -> (predecessors.getOrElse(pos, Set.empty) | lastActions)
    lastActions = Set(pos)
    this.actions += pos -> (this.actions.getOrElse(pos, Set.empty) ++ actionIt)
  }

  def buildAstAutomaton(): AstAutomaton[AstAction] = {
    val result = new AstAutomaton[AstAction]
    val indexToNodes = mutable.Map.empty[ActionPos, Set[result.Node]]

    def buildNode(index: ActionPos): Set[result.Node] = {
      val isNew = !indexToNodes.contains(index)
      val nodes = indexToNodes.getOrElseUpdate(index, actions(index).map(new result.Node(_)))
      if (isNew) {
        val predIndices = predecessors.getOrElse(index, Set.empty)

        if (predIndices.isEmpty) {
          ???
          nodes.foreach(_.markStart())
        } else {
          val target =
            if (predIndices.count(_ != StartActionPos) == 1) {
              nodes
            } else {
              val mergeNode = new result.Node(AstAction.Empty)
              mergeNode ~> nodes
              Set(mergeNode)
            }

          predIndices.foreach {
            case StartActionPos => target.foreach(_.markStart())
            case predIdx => buildNode(predIdx).foreach { predNode => predNode ~> target }
          }
        }
      }
      nodes
    }

    for {
      idx <- lastActions if idx != StartActionPos
      node <- buildNode(idx)
    } {
      node.markExit()
    }

    assert(lastActions.nonEmpty)

    result
  }
}

object InferAstDfaMemoryState {
  def apply(factory: DfaValueFactory): InferAstDfaMemoryState = new InferAstDfaMemoryState(new DfaMemoryStateImpl(factory))
}