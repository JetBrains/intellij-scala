package org.jetbrains.plugins.scala

import com.intellij.lang.{DependentLanguage, Language}
import com.intellij.openapi.application.{ApplicationManager, Experiments}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.uast.UElement

package object uast {

  implicit class ReferenceExt(private val ref: ScReference) extends AnyVal {
    @Nullable
    def resolveTo[T >: Null : reflect.ClassTag]: T = ref.resolve() match {
      case instance if reflect.classTag[T].runtimeClass.isInstance(instance) => instance.asInstanceOf[T]
      case _ => null
    }
  }

  private[uast] def isEnabled =
    ApplicationManager.getApplication.isUnitTestMode ||
      Experiments.getInstance().isFeatureEnabled("scala.uast.enabled")

  private[uast] object DummyDialect extends Language(ScalaLanguage.INSTANCE, "DummyDialect") with DependentLanguage

  private[uast] def toClassTag(@Nullable requiredType: Class[_ <: UElement]) =
    reflect.ClassTag[UElement](if (requiredType == null) classOf[UElement] else requiredType)

}
