package org.jetbrains.plugins.scala.debugger.ui

import com.intellij.debugger.ui.tree.render.{ChildrenRenderer, CompoundRendererProvider, ToStringRenderer, ValueLabelRenderer}
import com.sun.jdi.Type

import java.{lang => jl}
import java.util.concurrent.CompletableFuture
import java.util.{function => juf}

class ScalaClassRendererProvider extends CompoundRendererProvider {
  private val renderer = new ScalaClassRenderer()

  override def getName: String = "Scala class"

  override def getValueLabelRenderer: ValueLabelRenderer = renderer

  override def getChildrenRenderer: ChildrenRenderer = renderer

  override def isEnabled: Boolean = true

  override def getIsApplicableChecker: juf.Function[Type, CompletableFuture[jl.Boolean]] =
    renderer.isApplicableFor
}
