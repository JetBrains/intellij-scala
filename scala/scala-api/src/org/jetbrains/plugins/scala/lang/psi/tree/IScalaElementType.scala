package org.jetbrains.plugins.scala.lang.psi.tree

import com.intellij.lang.Language
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.ScalaLanguage

class IScalaElementType(debugName: String,
                        language: Language = ScalaLanguage.INSTANCE,
                        override val isLeftBound: Boolean = true) extends IElementType(debugName, language) {
  override final def toString: String = super.toString
}
