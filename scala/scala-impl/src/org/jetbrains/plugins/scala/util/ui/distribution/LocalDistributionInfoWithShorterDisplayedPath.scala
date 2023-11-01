package org.jetbrains.plugins.scala.util.ui.distribution

import com.intellij.openapi.roots.ui.distribution.AbstractDistributionInfo
import com.intellij.openapi.ui.UiUtils
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.NonNls


/**
 * Similar to [[com.intellij.openapi.roots.ui.distribution.LocalDistributionInfo]]
 * but with shorted path representation.
 * It's needed because paths of compiler bridge jars are very long (they are located deep in the coursier directories)
 * and it messes up the UI layout.
 */
final class LocalDistributionInfoWithShorterDisplayedPath(_path: String) extends AbstractDistributionInfo {
  @NonNls
  val canonicalPath:  String = UiUtils.getCanonicalPath(_path)

  //This implementation is inspired by shortening logic in com.intellij.openapi.roots.ui.configuration.SdkListPresenter.presentDetectedSdkPath
  override def getName: String = {
    val presentablePath = UiUtils.getPresentablePath(canonicalPath)
    StringUtil.shortenTextWithEllipsis(presentablePath, 60, 30)
  }

  override def getDescription: String = null
}