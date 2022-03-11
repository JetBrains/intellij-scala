package org.jetbrains.plugins.scala.debugger.ui

import com.intellij.debugger.ui.tree.render.{ChildrenRenderer, CompoundRendererProvider, ValueLabelRenderer}
import com.sun.jdi.Type

import java.util.concurrent.CompletableFuture

abstract class ScalaRendererProvider(private val renderer: ScalaClassRenderer) extends CompoundRendererProvider {

  override def getName: String = renderer.getName

  override def getValueLabelRenderer: ValueLabelRenderer = renderer

  override def getChildrenRenderer: ChildrenRenderer = renderer

  override def isEnabled: Boolean = true

  override def getIsApplicableChecker: java.util.function.Function[Type, CompletableFuture[java.lang.Boolean]] =
    tpe => CompletableFuture.completedFuture(renderer.isApplicableFor(tpe))
}

class ScalaClassRendererProvider extends ScalaRendererProvider(new ScalaClassRenderer())

class ScalaCollectionRendererProvider extends ScalaRendererProvider(new ScalaCollectionRenderer())
