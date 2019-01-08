package org.jetbrains.plugins.scala
package annotator
package gutter

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader

/**
 * User: Alexander Podkhalyuzin
 * Date: 31.10.2008
 */

object GutterIcons {
  val RECURSION_ICON = IconLoader.getIcon("/org/jetbrains/plugins/scala/gutter/recursion.svg")
  val TAIL_RECURSION_ICON = IconLoader.getIcon("/org/jetbrains/plugins/scala/gutter/tail-recursion.svg")

  val OVERRIDING_METHOD_ICON = AllIcons.Gutter.OverridingMethod
  val IMPLEMENTING_METHOD_ICON = AllIcons.Gutter.ImplementingMethod

  val OVERRIDDEN_METHOD_MARKER_RENDERER = AllIcons.Gutter.OverridenMethod
  val IMPLEMENTED_METHOD_MARKER_RENDERER = AllIcons.Gutter.ImplementedMethod
  val IMPLEMENTED_INTERFACE_MARKER_RENDERER = IMPLEMENTED_METHOD_MARKER_RENDERER
  val SUBCLASSED_CLASS_MARKER_RENDERER = OVERRIDDEN_METHOD_MARKER_RENDERER
}