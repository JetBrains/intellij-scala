package org.jetbrains.plugins.scala.annotator.element

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.annotator.usageTracker.UsageTracker.registerUsedImports
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall

object ScMethodCallAnnotator extends ElementAnnotator[ScMethodCall] {
  override def annotate(element: ScMethodCall, holder: AnnotationHolder, typeAware: Boolean): Unit = {
    registerUsedImports(element, element.getImportsUsed)
  }
}
