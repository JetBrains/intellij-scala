package org.jetbrains.bsp.project.importing.setup

import com.intellij.openapi.application.ApplicationManager

/**
 * Additional `-Dkey=value` options for the sbt processes of sbt-over-BSP:
 *  - the sbt run that generates the BSP connection file, see [[SbtConfigSetup]]
 *  - the sbt BSP server started from the connection file, see [[org.jetbrains.bsp.protocol.session.GenericConnector]]
 *
 * The production implementation, [[NoopSbtOverBspVmOptions]], adds no options. Tests replace the service, e.g. to make
 * sbt resolve dependencies through the JetBrains Maven Central mirror instead of Maven Central.
 */
trait SbtOverBspVmOptions:
  def vmOptions: Seq[String]

object SbtOverBspVmOptions:
  def getInstance: SbtOverBspVmOptions =
    ApplicationManager.getApplication.getService(classOf[SbtOverBspVmOptions])
