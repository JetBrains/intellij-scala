package org.jetbrains.plugins.scala

import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference

package object uast {

  implicit class ReferenceExt(private val ref: ScReference) extends AnyVal {
    @Nullable
    def resolveTo[T >: Null : reflect.ClassTag]: T = ref.resolve() match {
      case instance if reflect.classTag[T].runtimeClass.isInstance(instance) => instance.asInstanceOf[T]
      case _ => null
    }
  }
}
