package org.jetbrains.plugins.scala.codeInsight.hints

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Key

package object chain {
  private[chain] val ScalaExprChainKey: Key[Boolean] = Key.create("SCALA_EXPR_CHAIN_KEY")
  private[chain] val ScalaExprChainDisposableKey: Key[Disposable] = Key.create("SCALA_EXPR_CHAIN_DISPOSABLE_KEY")
  private[chain] val typeHintsMenu = "TypeHintsMenu"
}
