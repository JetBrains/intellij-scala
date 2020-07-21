package org.jetbrains.plugins.scala

import com.intellij.DynamicBundle
import org.jetbrains.annotations.{Nls, PropertyKey}

import scala.annotation.varargs

object ScalaBundle {
  private final val BUNDLE = "messages.ScalaBundle"
  private object INSTANCE extends DynamicBundle(BUNDLE)

  //noinspection ReferencePassedToNls
  @Nls
  @varargs
  def message(@PropertyKey(resourceBundle = BUNDLE) key: String, params: Any*): String =
    INSTANCE.getMessage(key, params.map(_.toString): _*)
}
