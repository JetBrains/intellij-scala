package org.jetbrains.sbt

import java.lang.ref.{Reference, SoftReference}
import java.util.ResourceBundle

import com.intellij.CommonBundle

/**
 * @author Nikolay Obedin
 * @since 8/5/14.
 */

object SbtBundle {

  def apply(key: String, params: AnyRef*): String = CommonBundle.message(get(), key, params : _*)

  private var ourBundle: Reference[ResourceBundle] = null
  private val BUNDLE = "org.jetbrains.sbt.SbtBundle"

  private def get(): ResourceBundle = {
    var bundle: ResourceBundle = null
    if (ourBundle != null) bundle = ourBundle.get
    if (bundle == null) {
      bundle = ResourceBundle.getBundle(BUNDLE)
      ourBundle = new SoftReference[ResourceBundle](bundle)
    }
    bundle
  }
}