package org.jetbrains.plugins.scala.util.ui.distribution

import com.intellij.openapi.roots.ui.distribution.AbstractDistributionInfo
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle

/**
 * Represents any kind of distribution that can be "Bundled"
 */
final class GenericBundledDistributionInfo extends AbstractDistributionInfo {
  @Nls override def getName: String = ScalaBundle.message("bundled.distribution.info.name")
  override def getDescription: String = null
}