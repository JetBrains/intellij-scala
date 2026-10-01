package org.jetbrains.plugins.scala.browser

import com.intellij.ide.browsers.{BrowserLauncher, WebBrowser}
import com.intellij.openapi.project.Project

import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*

/**
 * Test double for [[BrowserLauncher]] that records URLs passed via the `browse(String, WebBrowser?, Project?)`
 * overload — the one `BrowserUtil.browse(String)` ultimately calls.
 *
 * The known callers use the URL overload. Other overloads no-op so this
 * focused double does not affect incidental browser requests in a fixture.
 */
final class CapturingBrowserLauncher extends BrowserLauncher {
  private val capturedUrls = new ConcurrentLinkedQueue[String]

  def getCapturedUrls: Seq[String] = capturedUrls.asScala.toSeq

  override def open(url: String): Unit = ()

  override def browse(file: java.nio.file.Path): Unit = ()

  override def browse(url: String, browser: WebBrowser, project: Project): Unit = {
    capturedUrls.add(url)
  }
}
