package org.jetbrains.plugins.scala.debugger.ui

import com.intellij.debugger.ui.tree.render.{ChildrenRenderer, CompoundRendererProvider, ValueLabelRenderer}
import com.sun.jdi.Type
import org.jetbrains.plugins.scala.ScalaBundle

import java.util.concurrent.CompletableFuture
import java.util.{function => juf}
import java.{lang => jl}

class ClassWithSpecializedFieldsRendererProvider extends CompoundRendererProvider {
  private val renderer = new ClassWithSpecializedFieldsRenderer()

  override val getName: String = ScalaBundle.message("scala.class.with.specialized.fields")

  override def getValueLabelRenderer: ValueLabelRenderer = renderer

  override def getChildrenRenderer: ChildrenRenderer = renderer

  override def isEnabled: Boolean = true

  override val getIsApplicableChecker: juf.Function[Type, CompletableFuture[jl.Boolean]] =
    renderer.isApplicableFor
}
