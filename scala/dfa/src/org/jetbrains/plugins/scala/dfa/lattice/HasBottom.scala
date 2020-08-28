package org.jetbrains.plugins.scala.dfa.lattice

trait HasBottom[+L] {
  def bottom: L
}

trait HasBottomOps {
  final def latticeBottom[L](implicit provider: HasBottom[L]): L = provider.bottom
}

object HasBottomOps extends HasBottomOps