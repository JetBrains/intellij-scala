package org.jetbrains.plugins.scala.dfa
package cfg
package impl

import org.jetbrains.plugins.scala.dfa.cfg.Builder.{Property, Variable}
import org.jetbrains.plugins.scala.dfa.utils.BuilderWithSize

import scala.collection.immutable.ArraySeq
import scala.collection.mutable

private[cfg] class BuilderImpl[SourceInfo] extends Builder[SourceInfo] {
  override type Value = cfg.Value
  override type UnlinkedJump = UnlinkedJumpImpl
  override type LoopLabel = LoopLabelImpl

  private type NodeImpl = impl.NodeImpl[SourceInfo] with Node
  private type JumpingImpl = impl.JumpingImpl[SourceInfo] with Jumping
  private type Block = impl.BlockImpl[SourceInfo]

  private class Scope(val block: Block, var variables: Map[Variable, Value])

  private var nextValueId = 0
  private var curSourceInfo = Option.empty[SourceInfo]
  private val nodesBuilder = BuilderWithSize.newBuilder[NodeImpl](ArraySeq)
  private val blocksBuilder = BuilderWithSize.newBuilder[Block](ArraySeq)


  // Normally there should be scope whenever there is a block and vice versa.
  // But there is this weird state where we insert phi nodes to build the scope.
  // Tn that state there is a block but no a scope.
  private var curMaybeBlock = Option.empty[Block]
  private var curMaybeScope = Option.empty[Scope]

  locally {
    startBlock("<main>", Seq.empty)
  }

  private def currentBlock: Block = curMaybeBlock.get
  private def currentScope: Scope =
    curMaybeScope.get.ensuring(curMaybeScope.nonEmpty, "Whenever there is a scope, there should be a block")

  private def startBlock(name: String, incomingScopes: Seq[Scope]): Unit = {
    assert(curMaybeBlock.isEmpty)
    assert(curMaybeScope.isEmpty)
    val block = new Block(name, index = blocksBuilder.elementsAdded, nodeBegin = nodesBuilder.elementsAdded)
    curMaybeBlock = Some(block)

    // build scope
    val newVariables = unifyVariables(incomingScopes)

    curMaybeScope = Some(new Scope(block, newVariables))
  }

  private def unifyVariables(incomingScope: Seq[Scope]): Map[Variable, Value] = {
    val builder = Map.newBuilder[Variable, Value]

    val lifeVariables = incomingScope
      .map(_.variables.keySet)
      .reduceOption(_ & _)
      .getOrElse(Set.empty)

    for (variable <- lifeVariables) {
      val incomingValues = incomingScope.groupMap(_.variables(variable))(_.block)
      val phi = addNode(new PhiValueImpl(incomingValues))

      builder += (variable -> phi)
    }

    builder.result()
  }

  private def closeBlock(): Scope = {
    val scope = currentScope
    val block = scope.block
    block._endIndex = nodesBuilder.elementsAdded

    blocksBuilder += block
    curMaybeBlock = None
    curMaybeScope = None
    scope
  }

  private def closeBlockIfNeeded(): Option[Scope] =
    curMaybeBlock.map { _ => closeBlock() }

  private def newValueId(): Int = {
    val next = nextValueId
    nextValueId += 1
    next
  }

  private def addNode(node: NodeImpl): node.type = {
    node._index = nodesBuilder.elementsAdded
    node._sourceInfo = curSourceInfo
    node._block = currentBlock

    node match {
      case value: ValueImpl[SourceInfo] =>
        value._valueId = newValueId()
      case _ =>
    }

    nodesBuilder += node
    node
  }

  override def constant(const: DfAny): Value =
    addNode(new ConstantImpl(const))

  override def readVariable(variable: Variable): Value =
    currentScope.variables(variable)
  override def writeVariable(variable: Variable, value: Value): Unit =
    currentScope.variables += variable -> value

  override def readProperty(base: Value, property: Property): Value = ???
  override def writeProperty(base: Value, property: Property, value: Value): Unit = ???

  /***** Forward jumps ****/
  private val unlinkedJumps = mutable.Set.empty[UnlinkedJump]
  private def addForwardJump(jump: JumpingImpl, nameAfterBlock: Option[String] = None): UnlinkedJump = {
    addNode(jump)
    val prevBlockInfo = closeBlock()
    val unlinkedJump = new UnlinkedJumpImpl(jump, prevBlockInfo)
    unlinkedJumps += unlinkedJump

    nameAfterBlock.foreach(startBlock(_, Seq(prevBlockInfo)))

    unlinkedJump
  }

  override def jumpToFuture(): UnlinkedJump = addForwardJump(new JumpImpl)
  override def jumpToFutureIfNot(cond: Value, afterBlockName: String): UnlinkedJump =
    addForwardJump(new JumpIfNotImpl(cond), Some(afterBlockName))

  override def jumpHere(blockName: String, labels: Seq[UnlinkedJump]): Unit = {
    val prevBlockInfo = closeBlockIfNeeded()
    val targetIndex = nodesBuilder.elementsAdded

    labels.foreach(_.finish(targetIndex))

    startBlock(blockName, prevBlockInfo.toSeq ++ labels.map(_.blockInfo))
  }

  class UnlinkedJumpImpl(private[BuilderImpl] val jumping: JumpingImpl,
                         private[BuilderImpl] val blockInfo: Scope) {
    private[BuilderImpl] def finish(targetIndex: Int): Unit = {
      assert(unlinkedJumps contains this)
      unlinkedJumps -= this
      jumping._targetIndex = targetIndex
    }
  }

  /***** Backward jumps *****/
  override def loopJumpHere(): LoopLabelImpl = ???
  override def jumpBack(loop: LoopLabelImpl): Unit = ???

  class LoopLabelImpl {

  }

  /***** Additional stuff *****/
  override def withSourceInfo[R](sourceInfo: SourceInfo)(body: => R): R = {
    val old = curSourceInfo
    curSourceInfo = Some(sourceInfo)
    try body
    finally curSourceInfo = old
  }

  private var nextFreshVarId = 0
  override def freshVariable(): Variable =
    try Variable(new AnyRef)("freshVar#" + nextFreshVarId)
    finally nextFreshVarId += 1

  /***** Create Graph *****/
  override def finish(): Graph[SourceInfo] = {
    addNode(new EndImpl)
    closeBlock()

    assert(unlinkedJumps.isEmpty, "Unlinked labels: " + unlinkedJumps.iterator.map(_.jumping.index).mkString(", "))

    val nodes = nodesBuilder.result()
    val blocks = blocksBuilder.result()
    val graph = new Graph[SourceInfo](nodes, blocks)
    blocks.foreach(_._graph = graph)

    {
      // check if blocks have correct boundaries
      assert(blocks.head.nodeBegin == 0)
      assert(blocks.size == 1 || (blocks: Seq[Block]).sliding(2).forall { case Seq(a, b) => a.nodeEnd == b.nodeBegin })
      assert(blocks.last.nodeEnd == graph.nodes.size)

      // check indices
      nodes.zipWithIndex.foreach { case (node, idx) => assert(node.index == idx) }
      blocks.zipWithIndex.foreach { case (block, idx) => assert(block.index == idx) }

      // sanity checks
      nodes.foreach(_.sanityCheck())
      blocks.foreach(_.sanityCheck())
    }

    graph
  }
}