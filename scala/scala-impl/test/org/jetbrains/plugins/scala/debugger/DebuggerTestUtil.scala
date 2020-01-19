package org.jetbrains.plugins.scala
package debugger

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.{JavaSdkVersion, Sdk}
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.util.registry.Registry
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.compiler.ScalaCompileServerSettings

/**
  * @author Nikolay.Tropin
  */
object DebuggerTestUtil {

  def enableCompileServer(enable: Boolean): Unit = {
    val compileServerSettings = ScalaCompileServerSettings.getInstance()
    assert(compileServerSettings != null, "could not get instance of compileServerSettings. Was plugin artifact built before running test? ")
    compileServerSettings.COMPILE_SERVER_ENABLED = enable
    compileServerSettings.COMPILE_SERVER_SHUTDOWN_IDLE = true
    compileServerSettings.COMPILE_SERVER_SHUTDOWN_DELAY = 30
    val application = ApplicationManager.getApplication.asInstanceOf[ApplicationEx]
    application.setSaveAllowed(true)
    application.saveSettings()
  }

  def forceLanguageLevelForBuildProcess(jdk: Sdk): Unit =
    jdk.getHomeDirectory match {
      case null =>
        throw new RuntimeException(s"Failed to set up JDK, got: $jdk")
      case homeDirectory =>
        val jdkHome = homeDirectory.getCanonicalPath
        Registry.get("compiler.process.jdk").setValue(jdkHome)
    }

 }
