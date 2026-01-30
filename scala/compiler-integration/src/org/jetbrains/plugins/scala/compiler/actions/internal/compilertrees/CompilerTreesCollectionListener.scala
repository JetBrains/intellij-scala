package org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees

import scala.collection.mutable

abstract class CompilerTreesCollectionListener {
  def phaseAdded(phase: PhaseWithTreeText): Unit
  def collectionFinished(): Unit
}

object CompilerTreesCollectionListener {
  final class Composite(
    private val listeners: Seq[CompilerTreesCollectionListener] = Nil
  ) extends CompilerTreesCollectionListener {

    def withExtraListener(listener: CompilerTreesCollectionListener): Composite = {
      new Composite(listeners :+ listener)
    }

    override def phaseAdded(phase: PhaseWithTreeText): Unit = {
      listeners.foreach(_.phaseAdded(phase))
    }

    override def collectionFinished(): Unit = {
      listeners.foreach(_.collectionFinished())
    }
  }

  class Collecting extends CompilerTreesCollectionListener {
    private val collected = mutable.ArrayBuffer.empty[PhaseWithTreeText]
    private var _isFinished: Boolean = false

    def collectedPhases: Seq[PhaseWithTreeText] = collected.toSeq
    def isFinished: Boolean = _isFinished

    override def phaseAdded(phase: PhaseWithTreeText): Unit = collected += phase
    override def collectionFinished(): Unit = _isFinished = true
  }
}
