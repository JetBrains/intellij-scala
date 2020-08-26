package org.jetbrains.plugins.scala.dfa
package cfg
package impl

import scala.collection.mutable

private[cfg] class BuilderImpl[Info, V, P] extends Builder[Info, V, P] {
  override type Value = cfg.Value
  override type UnlinkedJump = UnlinkedJumpImpl
  override type LoopLabel = LoopLabelImpl

  private type NodeImpl = impl.NodeImpl[Info] with Node
  private type JumpingImpl = impl.JumpingImpl[Info] with Jumping
  private type Block = impl.BlockImpl[Info]

  private var nextValueId = 0
  private var curSourceInfo = Option.empty[Info]
  private val nodes = mutable.ArrayBuffer.empty[NodeImpl]
  private val blocks = mutable.ArrayBuffer.empty[Block]

  private def newValueId(): Int = {
    val next = nextValueId
    nextValueId += 1
    next
  }

  private def addNode(node: NodeImpl): node.type = {
    node._index = nodes.size
    node._sourceInfo = curSourceInfo
    node._block = currentBlock

    node match {
      case value: ValueImpl[Info] =>
        value._valueId = newValueId()
      case _ =>
    }

    nodes += node
    node
  }

  private def startBlock(): Block = {
    assert(_currentBlock.isEmpty)
    val block = new Block(blockIndex = blocks.size, nodeBegin = nodes.size)
    assert(blocks.lastOption.forall(_.nodeEnd == block.nodeBegin))
    blocks += block
    _currentBlock = Some(block)
    block
  }

  private def endBlock(): Unit = {
    val Some(cur) = _currentBlock.ensuring(_.nonEmpty)
    cur._endIndex = nodes.size
    _currentBlock = None
  }

  private var _currentBlock = Option.empty[Block]
  private def currentBlock: Block = _currentBlock match {
    case Some(block) => block
    case None => startBlock()
  }

  override def constant(const: DfAny): Value =
    addNode(new ConstantImpl(const))

  override def readVariable(variable: V): Unit = ???
  override def writeVariable(variable: V, value: Value): Unit = ???

  override def readProperty(base: Value, property: P): Value = ???
  override def writeProperty(base: Value, property: P, value: Value): Unit = ???

  /***** Forward jumps ****/
  private val unlinkedJumps = mutable.Set.empty[UnlinkedJump]
  private def addForwardJump(jump: JumpingImpl): UnlinkedJump = {
    val unlinkedJump = new UnlinkedJumpImpl(addNode(jump))
    endBlock()
    unlinkedJumps += unlinkedJump
    unlinkedJump
  }

  override def jumpToFuture(): UnlinkedJump = addForwardJump(new JumpImpl)
  override def jumpToFutureIfNot(cond: Value): UnlinkedJump = addForwardJump(new JumpIfNotImpl(cond))
  override def jumpHere(labels: Seq[UnlinkedJump]): Unit = {
    endBlock()
    val targetIndex = nodes.size
    labels.foreach(_.finish(targetIndex))
  }

  class UnlinkedJumpImpl(private[BuilderImpl] val jumping: JumpingImpl) {
    def finish(targetIndex: Int): Unit = {
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

  /***** Create Graph *****/
  override def finish(): Graph[Info] = {
    addNode(new EndImpl)

    val graph = new Graph[Info](nodes.toArray[Node])

    assert(unlinkedJumps.isEmpty, "Unlinked labels: " + unlinkedJumps.iterator.map(_.jumping.index).mkString(", "))

    graph
  }
}
