package org.jetbrains.plugins.scala.lang.psi.api

import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValueProvider.Result
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.controlFlow.impl.{AllVariablesControlFlowPolicy, ScalaControlFlowBuilder}
import org.jetbrains.plugins.scala.lang.psi.controlFlow.{Instruction, ScControlFlowPolicy}

import scala.collection.mutable

/**
 * Represents elements with control flow cached
 * @author ilyas
 */

trait ScControlFlowOwner extends ScalaPsiElement {

  private val myControlFlowCache = mutable.Map[ScControlFlowPolicy, ControlFlowCacheProvider]()

  private def buildControlFlow(policy: ScControlFlowPolicy = AllVariablesControlFlowPolicy) = {
    val builder = new ScalaControlFlowBuilder(null, null, policy)
    controlFlowScope match {
      case Some(elem) => builder.buildControlflow(elem)
      case None => Seq.empty
    }
  }

  def getControlFlow(policy: ScControlFlowPolicy = AllVariablesControlFlowPolicy): Seq[Instruction] = {
    val provider = myControlFlowCache.getOrElseUpdate(policy, new ControlFlowCacheProvider(policy))
    provider.compute().getValue
  }

  def controlFlowScope: Option[ScalaPsiElement]

  private class ControlFlowCacheProvider(policy: ScControlFlowPolicy) extends CachedValueProvider[Seq[Instruction]] {

    override def compute(): Result[Seq[Instruction]] = Result.create(buildControlFlow(policy), ScControlFlowOwner.this)
  }
}
