package org.jetbrains.plugins.scala.codeInsight.hints

import com.intellij.openapi.util.Key

package object methodChains {
  private[methodChains] val ScalaMethodChainKey: Key[Boolean] = Key.create[Boolean]("SCALA_METHOD_CHAIN_KEY")
}