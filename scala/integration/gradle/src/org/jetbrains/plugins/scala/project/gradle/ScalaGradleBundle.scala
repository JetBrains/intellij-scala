package org.jetbrains.plugins.scala.project.gradle

import com.intellij.DynamicBundle
import org.jetbrains.annotations.{Nls, PropertyKey}

import scala.annotation.varargs

object ScalaGradleBundle {
  private final val BUNDLE = "messages.ScalaGradleBundle"
  private object INSTANCE extends DynamicBundle(BUNDLE)

  @Nls
  @varargs
  def message(@PropertyKey(resourceBundle = BUNDLE) key: String, params: Any*): String =
    INSTANCE.getMessage(key, params)
}