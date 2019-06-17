package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.annotator.usageTracker.UsageTracker.registerUsedImports
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall

object ScMethodCallAnnotator extends ElementAnnotator[ScMethodCall] {

  override def annotate(element: ScMethodCall, typeAware: Boolean)
                       (implicit holder: AnnotationHolder): Unit = {
    registerUsedImports(element, element.getImportsUsed)
  }
}
