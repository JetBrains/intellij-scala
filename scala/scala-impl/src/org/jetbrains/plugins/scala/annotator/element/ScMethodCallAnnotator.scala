package org.jetbrains.plugins.scala
package annotator
package element

import org.jetbrains.plugins.scala.annotator.usageTracker.UsageTracker.registerUsedImports
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall

object ScMethodCallAnnotator extends ElementAnnotator[ScMethodCall] {

  override def annotate(element: ScMethodCall, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    registerUsedImports(element, element.getImportsUsed)
  }
}
