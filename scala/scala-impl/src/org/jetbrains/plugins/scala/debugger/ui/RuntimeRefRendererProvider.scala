package org.jetbrains.plugins.scala.debugger.ui

import com.intellij.debugger.ui.tree.render.{ChildrenRenderer, CompoundRendererProvider, ValueLabelRenderer}
import com.sun.jdi.Type
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.debugger.filters.ScalaDebuggerSettings

import java.util.concurrent.CompletableFuture
import java.util.{function => juf}
import java.{lang => jl}

class RuntimeRefRendererProvider extends CompoundRendererProvider {
  private val renderer = new RuntimeRefRenderer()

  override val getName: String = ScalaBundle.message("scala.runtime.references.renderer")

  override def getValueLabelRenderer: ValueLabelRenderer = renderer

  override def getChildrenRenderer: ChildrenRenderer = renderer

  override def isEnabled: Boolean = ScalaDebuggerSettings.getInstance().DONT_SHOW_RUNTIME_REFS

  override val getIsApplicableChecker: juf.Function[Type, CompletableFuture[jl.Boolean]] =
    renderer.isApplicableFor
}
