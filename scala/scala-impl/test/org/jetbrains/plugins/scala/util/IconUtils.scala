package org.jetbrains.plugins.scala.util

import com.intellij.icons.AllIcons
import com.intellij.ui.{IconManager, LayeredIcon}
import com.intellij.ui.icons.CoreIconManager
import org.junit.Assert.fail

import javax.swing.Icon

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
        val FLAGS_STATIC = 0x200
        val FLAGS_FINAL = 0x400
        val FLAGS_JUNIT_TEST = 0x2000
        val FLAGS_RUNNABLE = 0x4000

        iconManager.registerIconLayer(FLAGS_STATIC, AllIcons.Nodes.StaticMark)
        iconManager.registerIconLayer(FLAGS_FINAL, AllIcons.Nodes.FinalMark)
        iconManager.registerIconLayer(FLAGS_JUNIT_TEST, AllIcons.Nodes.JunitTestMark)
        iconManager.registerIconLayer(FLAGS_RUNNABLE, AllIcons.Nodes.RunnableMark)
      case m =>
        fail(s"Unexpected icon manager: ${m.getClass} (expected ${classOf[CoreIconManager]})")
    }
  }


  def createLayeredIcon(icons: Icon*): Icon = {
    val result = new LayeredIcon(icons.length)
    icons.zipWithIndex.foreach { case (icon, index) => result.setIcon(icon, index) }
    result
  }
}
