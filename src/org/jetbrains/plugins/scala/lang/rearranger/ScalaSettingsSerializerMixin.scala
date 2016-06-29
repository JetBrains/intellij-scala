package org.jetbrains.plugins.scala
package lang.rearranger

import com.intellij.psi.codeStyle.arrangement.DefaultArrangementSettingsSerializer
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken

/**
 * @author Roman.Shein
 * Date: 19.07.13
 */
class ScalaSettingsSerializerMixin extends DefaultArrangementSettingsSerializer.Mixin{
  def deserializeToken(id: String): ArrangementSettingsToken =
    getTokenById(id).orNull
}
