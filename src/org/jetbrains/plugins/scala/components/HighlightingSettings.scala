package org.jetbrains.plugins.scala.components

import scala.beans.BeanProperty

/**
 * @author Pavel Fatin
 */
class HighlightingSettings {
  @BeanProperty
  var TYPE_AWARE_HIGHLIGHTING_ENABLED: Boolean = false

  @BeanProperty
  var SUGGEST_TYPE_AWARE_HIGHLIGHTING: Boolean = true
}
