package org.jetbrains.sbt.project.versionNotifications.utils

import com.intellij.ide.browsers.{BrowserLauncher, WebBrowser}
import com.intellij.openapi.project.Project

import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*

/**
 * Test double for [[BrowserLauncher]] that records URLs passed via the `browse(String, WebBrowser?, Project?)`
 * overload — the one `BrowserUtil.browse(String)` ultimately calls.
 *
 * The production code does not exercise other overloads (file/Path/open) under test.
 * So they no-op rather than fail; flipping them to a failure would make the test fragile to incidental future calls.
 */
private[versionNotifications]
final class CapturingBrowserLauncher extends BrowserLauncher {
  private val capturedUrls = new ConcurrentLinkedQueue[String]

  def getCapturedUrls: Seq[String] = capturedUrls.asScala.toSeq

  override def open(url: String): Unit = ()

  override def browse(file: java.nio.file.Path): Unit = ()

  override def browse(url: String, browser: WebBrowser, project: Project): Unit = {
    capturedUrls.add(url)
  }
}
