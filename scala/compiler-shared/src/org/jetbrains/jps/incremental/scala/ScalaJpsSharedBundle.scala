package org.jetbrains.jps.incremental.scala

import com.intellij.DynamicBundle
import org.jetbrains.annotations.{Nls, PropertyKey}

import scala.annotation.varargs

object ScalaJpsSharedBundle {
  private final val BUNDLE = "messages.ScalaJpsSharedBundle"

  private object INSTANCE extends DynamicBundle(BUNDLE)

  //noinspection ReferencePassedToNls
  @Nls
  @varargs
  def message(@PropertyKey(resourceBundle = BUNDLE) key: String, params: Any*): String =
    INSTANCE.getMessage(key, params: _*)
}
