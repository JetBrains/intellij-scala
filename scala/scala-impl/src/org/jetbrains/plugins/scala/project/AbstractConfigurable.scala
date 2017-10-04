package org.jetbrains.plugins.scala
package project

import com.intellij.openapi.options.Configurable

/**
 * @author Pavel Fatin
 */
abstract class AbstractConfigurable(name: String) extends Configurable with Configurable.NoScroll {
  def getDisplayName: String = name
}
