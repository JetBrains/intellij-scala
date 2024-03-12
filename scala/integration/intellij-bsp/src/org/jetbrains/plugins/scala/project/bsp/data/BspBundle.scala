package org.jetbrains.plugins.scala.project.bsp.data

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

object BspBundle {
  private final val BUNDLE = "messages.ScalaBspBundle"
  private final val INSTANCE = new BspBundle

  @Nls def message(@PropertyKey(resourceBundle = BUNDLE) key: String, params: AnyRef*): String = INSTANCE.getMessage(key, params)
}
final class BspBundle private extends DynamicBundle(BspBundle.BUNDLE) {
}
