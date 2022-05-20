package org.jetbrains.plugins.scala.util.ui

import com.intellij.util.ui.UIUtil

import javax.swing.AbstractButton

/** based on [[com.intellij.openapi.ui.LabeledComponent.TextWithMnemonic]] */
case class TextWithMnemonic(text: String, mnemonic: Option[Mnemonic]) {
  def setTo(button: AbstractButton): Unit = {
    button.setText(text)
    mnemonic match {
      case Some(Mnemonic(char, index)) =>
        button.setMnemonic(char)
        button.setDisplayedMnemonicIndex(index)
      case _                           =>
    }
  }
}

object TextWithMnemonic {

  def apply(text: String): TextWithMnemonic = {
    val idx = UIUtil.getDisplayMnemonicIndex(text)
    if (idx != -1)
      new TextWithMnemonic(new StringBuilder(text).deleteCharAt(idx).toString, Some(Mnemonic(text.charAt(idx), idx)))
    else
      new TextWithMnemonic(text, None)
  }

  implicit class AbstractButtonExt[T <: AbstractButton](private val button: T) extends AnyVal {
    def setTextWithMnemonic(text: String): T = {
      TextWithMnemonic(text).setTo(button)
      button
    }
  }
}