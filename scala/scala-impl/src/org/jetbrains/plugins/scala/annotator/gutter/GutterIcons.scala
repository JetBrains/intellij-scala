package org.jetbrains.plugins.scala
package annotator
package gutter

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * User: Alexander Podkhalyuzin
 * Date: 31.10.2008
 */

object GutterIcons {
  val RECURSION_ICON: Icon = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/recursion.svg")
  val TAIL_RECURSION_ICON: Icon = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/tail-recursion.svg")

  val OVERRIDING_METHOD_ICON: Icon = AllIcons.Gutter.OverridingMethod
  val IMPLEMENTING_METHOD_ICON: Icon = AllIcons.Gutter.ImplementingMethod

  val OVERRIDDEN_METHOD_MARKER_RENDERER: Icon = AllIcons.Gutter.OverridenMethod
  val IMPLEMENTED_METHOD_MARKER_RENDERER: Icon = AllIcons.Gutter.ImplementedMethod
  val IMPLEMENTED_INTERFACE_MARKER_RENDERER: Icon = IMPLEMENTED_METHOD_MARKER_RENDERER
  val SUBCLASSED_CLASS_MARKER_RENDERER: Icon = OVERRIDDEN_METHOD_MARKER_RENDERER
}