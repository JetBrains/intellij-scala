package org.jetbrains.plugins.scala.debugger.ui

import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.impl.DebuggerUtilsAsync
import com.intellij.debugger.ui.tree.render.ClassRenderer
import com.sun.jdi.{ClassType, Field, ObjectReference}

import java.util.concurrent.CompletableFuture
import scala.jdk.CollectionConverters._

class ScalaObjectRenderer extends ClassRenderer {

  import ScalaObjectRenderer._

  override val getUniqueId: String = getClass.getSimpleName

  //noinspection UnstableApiUsage,ApiStatus
  setIsApplicableChecker {
    case ct: ClassType =>
      DebuggerUtilsAsync.allFields(ct).thenApply(_.asScala.exists(isModule))
    case _ => CompletableFuture.completedFuture(false)
  }

  override def shouldDisplay(context: EvaluationContext, objInstance: ObjectReference, field: Field): Boolean =
    !isModule(field) && !isBitmap(field)
}

private object ScalaObjectRenderer {
  private val Module: String = "MODULE$"

  private val Bitmap: String = "bitmap$"

  def isModule(f: Field): Boolean = f.name() == Module

  def isBitmap(f: Field): Boolean = f.name().startsWith(Bitmap)
}
