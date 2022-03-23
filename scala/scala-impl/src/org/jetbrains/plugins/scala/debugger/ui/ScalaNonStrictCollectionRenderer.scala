package org.jetbrains.plugins.scala.debugger.ui

import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.settings.NodeRendererSettings
import com.intellij.debugger.ui.tree.render.ChildrenBuilder
import com.sun.jdi._
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.debugger.filters.ScalaDebuggerSettings
import org.jetbrains.plugins.scala.debugger.ui.util._

class ScalaNonStrictCollectionRenderer extends ScalaCollectionRenderer {

  import ScalaCollectionRenderer._

  override def getName: String = ScalaBundle.message("scala.non.strict.collection.renderer")

  override def isApplicableFor(tpe: Type): Boolean =
    tpe match {
      case ct: ClassType if isNonStrictCollection(ct) => true
      case _ => false
    }

  override def isEnabled: Boolean =
    super.isEnabled && ScalaDebuggerSettings.getInstance().DO_NOT_DISPLAY_STREAMS

  override def buildChildren(value: Value, builder: ChildrenBuilder, context: EvaluationContext): Unit = {
    val startIndex = ScalaDebuggerSettings.getInstance().COLLECTION_START_INDEX
    val endIndex = ScalaDebuggerSettings.getInstance().COLLECTION_END_INDEX
    val renderer = NodeRendererSettings.getInstance().getArrayRenderer
    val ref = value.asInstanceOf[ObjectReference]

    for {
      taken <- onDebuggerManagerThread(context)(evaluateTake(ref, endIndex, context))
      dropped <- onDebuggerManagerThread(context)(evaluateDrop(taken, startIndex, context))
      array <- onDebuggerManagerThread(context)(evaluateToArray(dropped, context))
      _ <- onDebuggerManagerThread(context)(renderer.buildChildren(array, builder, context))
    } yield ()
  }
}
