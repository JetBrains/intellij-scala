package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.specialSupport

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations.Transformable

final case class CollectionAccessAssertion(index: Transformable, exceptionName: String)
