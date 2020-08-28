package org.jetbrains.plugins.scala.dfa.lattice

trait HasTop[+L] {
  def top: L
}

trait HasTopOps {
  final def latticeTop[L](implicit provider: HasTop[L]): L = provider.top
}

object HasTopOps extends HasTopOps