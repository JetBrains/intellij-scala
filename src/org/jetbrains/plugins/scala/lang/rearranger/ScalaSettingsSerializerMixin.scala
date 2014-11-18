package org.jetbrains.plugins.scala
package lang.rearranger

import com.intellij.psi.codeStyle.arrangement.DefaultArrangementSettingsSerializer

/**
 * @author Roman.Shein
 * Date: 19.07.13
 */
class ScalaSettingsSerializerMixin extends DefaultArrangementSettingsSerializer.Mixin{
  def deserializeToken(id: String) =
    getTokenById(id).orNull
}
