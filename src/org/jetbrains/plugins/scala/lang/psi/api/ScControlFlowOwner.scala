package org.jetbrains.plugins.scala.lang.psi.api

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.controlFlow.impl.{AllVariablesControlFlowPolicy, ScalaControlFlowBuilder}
import org.jetbrains.plugins.scala.lang.psi.controlFlow.{Instruction, ScControlFlowPolicy}

import scala.collection.mutable

/**
 * Represents elements with control flow cached
 * @author ilyas
 */

trait ScControlFlowOwner extends ScalaPsiElement {

  private val myControlFlowCache = mutable.Map[ScControlFlowPolicy, Seq[Instruction]]()

  private def buildControlFlow(scope: Option[ScalaPsiElement], policy: ScControlFlowPolicy = AllVariablesControlFlowPolicy) = {
    val builder = new ScalaControlFlowBuilder(null, null, policy)
    scope match {
      case Some(elem) =>
        val controlflow = builder.buildControlflow(elem)
        myControlFlowCache += (policy -> controlflow)
        controlflow
      case None => Seq.empty
    }
  }

  def getControlFlow(cached: Boolean, policy: ScControlFlowPolicy = AllVariablesControlFlowPolicy): Seq[Instruction] = {
    if (!cached || !myControlFlowCache.contains(policy)) buildControlFlow(controlFlowScope, policy)
    else myControlFlowCache(policy)
  }

  def controlFlowScope: Option[ScalaPsiElement]
}