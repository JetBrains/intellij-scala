package org.jetbrains.plugins.scala.tasty.reader

import dotty.tools.tasty.TastyBuffer.NameRef

import scala.collection.mutable.ArrayBuffer

// See dotty.tools.dotc.core.tasty.TastyUnpickler.NameTable

private class NameTable extends (NameRef => TermName) {
  private val names = new ArrayBuffer[TermName]

  def add(name: TermName): ArrayBuffer[TermName] = {
    names += name
  }

  def apply(ref: NameRef): TermName = names(ref.index)

  def contents: Iterable[TermName] = names
}
