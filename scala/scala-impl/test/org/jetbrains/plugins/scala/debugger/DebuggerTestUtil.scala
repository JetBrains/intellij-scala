package org.jetbrains.plugins.scala.debugger

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader.JDKVersion
import org.jetbrains.plugins.scala.compiler.ScalaCompileServerSettings

/**
  * @author Nikolay.Tropin
  */
object DebuggerTestUtil {

  def enableCompileServer(enable: Boolean): Unit = {
    val compileServerSettings = ScalaCompileServerSettings.getInstance()
    compileServerSettings.COMPILE_SERVER_ENABLED = enable
    compileServerSettings.COMPILE_SERVER_SHUTDOWN_IDLE = true
    compileServerSettings.COMPILE_SERVER_SHUTDOWN_DELAY = 30
    ApplicationManager.getApplication.saveSettings()
  }

  def forceJdk8ForBuildProcess(): Unit = {
    val jdk8 = SmartJDKLoader.getOrCreateJDK(JDKVersion.JDK18)
    if (jdk8.getHomeDirectory == null) {
      throw new RuntimeException(s"Failed to set up JDK, got: ${jdk8.toString}")
    }
    val jdkHome = jdk8.getHomeDirectory.getCanonicalPath
    Registry.get("compiler.process.jdk").setValue(jdkHome)
  }

}
