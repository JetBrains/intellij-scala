package org.jetbrains.plugins.scala.util

import com.intellij.notification.{NotificationGroup, NotificationGroupManager}

/**
 * Helper methods to replace deprecated [[com.intellij.notification.NotificationGroup]] static methods.
 *
 * Uses groups registered as EPs instead of dynamically created.<br>
 * IDs must correspond to ones declared in `scala-plugin-common.xml`
 */
object ScalaNotificationGroups {
  private def manager = NotificationGroupManager.getInstance()

  /**
   * General notification group for any scala-related notification.
   *
   * NOTE: please use custom notification group for every semantically-grouped features instead of scalaGeneral
   */
  def scalaGeneral: NotificationGroup = manager.getNotificationGroup("scala.general")

  //Balloon (by default)
  def javaToScalaConverter: NotificationGroup = manager.getNotificationGroup("java.to.scala.converter")
  def sbtProjectImport: NotificationGroup = manager.getNotificationGroup("sbt.project.import")
  def sbtShell: NotificationGroup = manager.getNotificationGroup("sbt.shell")

  //Sticky Balloon (by default)
  def scalaPluginVerifier: NotificationGroup = manager.getNotificationGroup("scala.plugin.verifier")
  def scalaPluginUpdater: NotificationGroup = manager.getNotificationGroup("scala.plugin.updater")
  def scala3Disclaimer: NotificationGroup = manager.getNotificationGroup("scala3.disclaimer")
  def scalaFeaturesAdvertiser: NotificationGroup = manager.getNotificationGroup("scala.features.advertiser")
}
