package org.jetbrains.plugins.scala.lang.transformation.annotations

import org.jetbrains.plugins.scala.extensions.&&
import org.jetbrains.plugins.scala.lang.psi.api.Typed
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScUnderscoreSection
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.lang.transformation._

/**
  * @author Pavel Fatin
  */
object AddTypeToUnderscoreParameter extends AbstractTransformer {
  def transformation = {
    case (e: ScUnderscoreSection) && Typed(t) if !e.nextSibling.exists(_.getText == ":") =>
      val annotation = annotationFor(t, e)
      e.replace(code"(_: $annotation)")
  }
}
