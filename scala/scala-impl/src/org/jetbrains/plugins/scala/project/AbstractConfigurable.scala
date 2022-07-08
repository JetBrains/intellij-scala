package org.jetbrains.plugins.scala
package project

import com.intellij.openapi.options.Configurable
import org.jetbrains.annotations.Nls

abstract class AbstractConfigurable(@Nls name: String) extends Configurable with Configurable.NoScroll {
  override def getDisplayName: String = name
}
