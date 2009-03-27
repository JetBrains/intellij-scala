package org.jetbrains.plugins.scala.annotator.gutter

import com.intellij.openapi.util.IconLoader

/**
 * User: Alexander Podkhalyuzin
 * Date: 31.10.2008
 */

object GutterIcons {
  val OVERRIDING_METHOD_ICON = IconLoader.getIcon("/gutter/overridingMethod.png")
  val IMPLEMENTING_METHOD_ICON = IconLoader.getIcon("/gutter/implementingMethod.png")

  val OVERRIDEN_METHOD_MARKER_RENDERER = IconLoader.getIcon("/gutter/overridenMethod.png")
  val IMPLEMENTED_METHOD_MARKER_RENDERER = IconLoader.getIcon("/gutter/implementedMethod.png")
  val IMPLEMENTED_INTERFACE_MARKER_RENDERER = IMPLEMENTED_METHOD_MARKER_RENDERER
  val SUBCLASSED_CLASS_MARKER_RENDERER = OVERRIDEN_METHOD_MARKER_RENDERER
}