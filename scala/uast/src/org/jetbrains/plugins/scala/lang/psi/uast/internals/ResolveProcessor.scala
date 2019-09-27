package org.jetbrains.plugins.scala.lang.psi.uast.internals

import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.extensions._

import scala.reflect.ClassTag

/**
  * Main entry point for all resolves in UAST module.
  */
private[uast] object ResolveProcessor {
  implicit class ReferenceExt(private val ref: ScReference) extends AnyVal {
    @Nullable
    def resolveTo[T >: Null: ClassTag](): T =
      Option(ref.resolve()).filterByType[T].orNull
  }
}
