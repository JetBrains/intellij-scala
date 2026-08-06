package org.jetbrains.plugins.scala.codeInsight.hints

import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.util.Key

package object methodChains {
  private[methodChains] val ScalaMethodChainKey: Key[Boolean] = Key.create[Boolean]("SCALA_METHOD_CHAIN_KEY")

  // Using these Inlay properties will exclude the inlay from being considered when calculating soft-wraping.
  // This especially means, that the inlay will move out of the view on the right side, when the editor is made smaller
  // instead of wrapping the line and being shown on the next line.
  // I think moving the inlay out of view is preferable to wrapping the lines and butchering the code,
  // so this should be used for all chain inlay hints.
  private[methodChains] val NonSoftWrappingInlayProperties: InlayProperties = {
    val props = new InlayProperties
    props.disableSoftWrapping(true)
    props
  }
}