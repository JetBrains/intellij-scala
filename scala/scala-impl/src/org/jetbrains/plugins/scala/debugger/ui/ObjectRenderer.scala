package org.jetbrains.plugins.scala.debugger.ui

import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.impl.DebuggerUtilsAsync
import com.intellij.debugger.ui.tree.render.ClassRenderer
import com.sun.jdi.{ClassType, Field, ObjectReference}

import java.util.concurrent.CompletableFuture
import scala.jdk.CollectionConverters._

class ObjectRenderer extends ClassRenderer {

  import ObjectRenderer._

  override val getUniqueId: String = getClass.getSimpleName

  //noinspection UnstableApiUsage,ApiStatus
  setIsApplicableChecker {
    case ct: ClassType =>
      DebuggerUtilsAsync.allFields(ct).thenApply(_.asScala.exists(isModule))
    case _ => CompletableFuture.completedFuture(false)
  }

  override def shouldDisplay(context: EvaluationContext, objInstance: ObjectReference, field: Field): Boolean =
    !isModule(field)
}

private object ObjectRenderer {
  private val Module: String = "MODULE$"

  def isModule(f: Field): Boolean = f.name() == Module
}
