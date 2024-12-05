package org.jetbrains.plugins.scala.inferAst

import com.intellij.codeInspection.dataFlow.memory.{DfaMemoryState, DfaMemoryStateImpl}
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory

import scala.collection.mutable

sealed abstract class AstAction
object AstAction {
  case object Start extends AstAction
  case class Token(token: String) extends AstAction
  case class Mark(index: Int) extends AstAction
  case class Done(index: Int, elementType: String) extends AstAction
  case class Rollback(index: Int) extends AstAction
  case class Drop(index: Int) extends AstAction
  case class Precede(oldIndex: Int, newIndex: Int) extends AstAction
  case class Error(error: String) extends AstAction
  case object Exit extends AstAction


  implicit val startExitProvider: AstAutomaton.StartExitActionProvider[AstAction] =
    new AstAutomaton.StartExitActionProvider[AstAction] {
      override def start: AstAction = AstAction.Start
      override def exit: AstAction = AstAction.Exit
    }
}


class InferAstDfaMemoryState private(prev: DfaMemoryStateImpl) extends DfaMemoryStateImpl(prev) {
  private var lastActions: Set[Int] = Set(-1)
  private var actions: Map[Int, Set[AstAction]] = Map.empty
  private var predecessors: Map[Int, Set[Int]] = Map.empty

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
      case other: InferAstDfaMemoryState =>
        val result = super.tryJoinExactly(other)
        if (result eq this) this.mergeActions(other)
        else if (result eq other) other.mergeActions(this)
        result
      case _ =>
        null
    }
  }

  private def mergeActions(other: InferAstDfaMemoryState): Unit = {
    def mergeMap[T](a: Map[Int, Set[T]], b: Map[Int, Set[T]]): Map[Int, Set[T]] =
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


  def addAction(index: Int, action: AstAction): Unit =
    addActions(index, List(action))

  def addActions(index: Int, actions: IterableOnce[AstAction]): Unit = {
    val actionIt = actions.iterator
    if (!actionIt.hasNext) return
    predecessors += index -> (predecessors.getOrElse(index, Set.empty) | lastActions)
    lastActions = Set(index)
    this.actions += index -> (this.actions.getOrElse(index, Set.empty) ++ actionIt)
  }

  def buildAstAutomaton(): AstAutomaton[AstAction] = {
    val result = new AstAutomaton[AstAction]
    val indexToNodes = mutable.Map.empty[Int, Set[result.Node]]
    indexToNodes(-1) = Set(result.start)

    def buildNode(index: Int): Set[result.Node] = {
      val isNew = !indexToNodes.contains(index)
      val nodes = indexToNodes.getOrElseUpdate(index, actions(index).map(new result.Node(_)))
      if (isNew) {
        val predIndices = predecessors.getOrElse(index, Set.empty)

        assert(predIndices.nonEmpty, s"No predecessors for index $index")
        if (predIndices.isEmpty) {
          result.start ~> nodes
        } else {
          predIndices.foreach { predIdx =>
            buildNode(predIdx).foreach { predNode => predNode ~> nodes }
          }
        }
      }
      nodes
    }

    for {
      idx <- lastActions
      node <- buildNode(idx)
    } {
      node ~> result.exit
    }

    assert(lastActions.nonEmpty)

    result
  }
}

object InferAstDfaMemoryState {
  def apply(factory: DfaValueFactory): InferAstDfaMemoryState = new InferAstDfaMemoryState(new DfaMemoryStateImpl(factory))
}