package org.jetbrains.plugins.scala.debugger.ui

import com.intellij.debugger.ui.tree.render.{ChildrenRenderer, CompoundRendererProvider, ValueLabelRenderer}
import com.sun.jdi.{ClassType, Type}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.debugger.filters.ScalaDebuggerSettings

import java.util.concurrent.CompletableFuture
import java.util.{function => juf}
import java.{lang => jl}

class ScalaCollectionRendererProvider extends CompoundRendererProvider {
  import ScalaCollectionRenderer._

  override val getName: String = ScalaBundle.message("scala.collection")

  override val getValueLabelRenderer: ValueLabelRenderer = createSizeLabelRenderer()

  override val getChildrenRenderer: ChildrenRenderer = ScalaToArrayRenderer

  override def isEnabled: Boolean = ScalaDebuggerSettings.getInstance().FRIENDLY_COLLECTION_DISPLAY_ENABLED

  override val getIsApplicableChecker: juf.Function[Type, CompletableFuture[jl.Boolean]] = {
    case ct: ClassType => forallAsync(instanceOfAsync(ct, collectionClassName), notStreamAsync(ct), notViewAsync(ct))
    case _ => CompletableFuture.completedFuture(false)
  }
}
