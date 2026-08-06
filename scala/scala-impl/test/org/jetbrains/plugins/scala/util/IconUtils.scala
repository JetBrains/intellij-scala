package org.jetbrains.plugins.scala.util

import com.intellij.psi.impl.ElementPresentationUtil
import com.intellij.ui.icons.CoreIconManager
import com.intellij.ui.{IconManager, LayeredIcon, PlatformIcons}
import org.junit.Assert.fail

import javax.swing.Icon
import scala.annotation.unused
import scala.jdk.CollectionConverters._

object IconUtils {

  /**
   * By default IconManager is deactivated and `com.intellij.ui.DummyIconManager` is used
   * We need a proper IconManager implementation, in order layered icons are properly built in structure view tests.
   * (see [[org.jetbrains.plugins.scala.util.BaseIconProvider.getIcon]])
   */
  def registerIconLayersInIconManager(): Unit = {
    IconManager.getInstance() match {
      case iconManager: CoreIconManager =>
        // workaround for IDEA-274148 (can remove it when the issue is fixed)
        // copied from com.intellij.psi.impl.ElementPresentationUtil static initializer

        // Needed to make sure that `ElementPresentationUtil.<clinit>` is run before the rest of the code
        // in this method.
        @unused val dummyDescription = ElementPresentationUtil.getDescription(null)

        val mapping = Map(
          ElementPresentationUtil.FLAGS_STATIC -> PlatformIcons.StaticMark,
          ElementPresentationUtil.FLAGS_FINAL -> PlatformIcons.FinalMark,
          ElementPresentationUtil.FLAGS_JUNIT_TEST -> PlatformIcons.JunitTestMark,
          ElementPresentationUtil.FLAGS_RUNNABLE -> PlatformIcons.RunnableMark
        )

        val iconLayersListMethod = classOf[CoreIconManager].getMethods.find(_.getName.contains("getIconLayers"))
          .getOrElse(throw new IllegalStateException("Cannot obtain the static iconLayers list from CoreIconManager.Companion"))
        iconLayersListMethod.setAccessible(true)
        val iconLayers = iconLayersListMethod.invoke(null).asInstanceOf[java.util.List[?]]
        mapping.foreach { case (mask, icon) =>
          if (findIconLayerWithMask(iconLayers, mask).isEmpty) {
            // The icon layer has not been registered. We need to register it ourselves.
            iconManager.registerIconLayer(mask, iconManager.getPlatformIcon(icon))
          }
        }
      case m =>
        fail(s"Unexpected icon manager: ${m.getClass} (expected ${classOf[CoreIconManager]})")
    }
  }

  private def findIconLayerWithMask(iconLayers: java.util.List[?], mask: Int): Option[Int] =
    iconLayers.asScala.zipWithIndex.find { case (iconLayer, _) =>
      val iconLayerClass = iconLayer.getClass
      val flagMaskField = iconLayerClass.getField("flagMask")
      flagMaskField.setAccessible(true)
      val flagMask = flagMaskField.get(iconLayer)
      flagMask == mask
    }.map(_._2)

  def createLayeredIcon(icons: Icon*): Icon = {
    val result = new LayeredIcon(icons.length)
    icons.zipWithIndex.foreach { case (icon, index) => result.setIcon(icon, index) }
    result
  }
}
