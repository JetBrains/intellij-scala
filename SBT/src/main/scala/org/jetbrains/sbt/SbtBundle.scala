package org.jetbrains.sbt

import java.lang.ref.{SoftReference, Reference}
import java.util.ResourceBundle

import com.intellij.CommonBundle

/**
 * @author Nikolay Obedin
 * @since 8/5/14.
 */

object SbtBundle {

  def apply(key: String, params: AnyRef*) = CommonBundle.message(get(), key, params : _*)

  private var instance: Reference[ResourceBundle] = null
  private val BUNDLE = "org.jetbrains.sbt.SbtBundle"

  private def get() = {
    if (instance == null)
      instance = new SoftReference(ResourceBundle.getBundle(BUNDLE))
    instance.get
  }
}