package org.jetbrains.plugins.scala.debugger

import java.io.File

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.projectRoots.{JavaSdk, Sdk}
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.plugins.scala.compiler.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.extensions._

/**
  * @author Nikolay.Tropin
  */
object DebuggerTestUtil {
  val jdk8Name = "JDK 1.8"

  def findJdk8(): Sdk = {
    val jdkTable = JavaAwareProjectJdkTableImpl.getInstanceEx
    Option(jdkTable.findJdk(jdk8Name)).getOrElse {
      val path = discoverJRE18().getOrElse(throw new RuntimeException("Could not find jdk8 installation, " +
                                            "please define a valid JDK_18_x64 or JDK_18, " +
                                            s"current - ${sys.env("JDK_18_x64")} or ${sys.env("JDK_18")}"))
      val jdk = JavaSdk.getInstance.createJdk(jdk8Name, path)
      inWriteAction {
        jdkTable.addJdk(jdk)
      }
      jdk
    }
  }

  def enableCompileServer(enable: Boolean): Unit = {
    val compileServerSettings = ScalaCompileServerSettings.getInstance()
    compileServerSettings.COMPILE_SERVER_ENABLED = enable
    compileServerSettings.COMPILE_SERVER_SHUTDOWN_IDLE = true
    compileServerSettings.COMPILE_SERVER_SHUTDOWN_DELAY = 30
    ApplicationManager.getApplication.saveSettings()
  }

  def forceJdk8ForBuildProcess(): Unit = {
    val jdk8 = findJdk8()
    if (jdk8.getHomeDirectory == null) {
      throw new RuntimeException(s"Failed to set up JDK, got: ${jdk8.toString}")
    }
    val jdkHome = jdk8.getHomeDirectory.getParent.getCanonicalPath
    Registry.get("compiler.process.jdk").setValue(jdkHome)
  }

  val candidates = Seq(
    "/usr/lib/jvm",                     // linux style
    "C:\\Program Files\\Java\\",        // windows style
    "C:\\Program Files (x86)\\Java\\",  // windows 32bit style
    "/Library/Java/JavaVirtualMachines" // mac style
  )

  def discoverJRE18(): Option[String] = discoverJre(candidates, "8")

  def discoverJRE16(): Option[String] = discoverJre(candidates, "6")

  def discoverJDK18(): Option[String] = discoverJRE18().map(new File(_).getParent)

  def discoverJDK16(): Option[String] = discoverJRE16().map(new File(_).getParent)

  def discoverJre(paths: Seq[String], versionMajor: String): Option[String] = {
    import java.io._
    def isJDK(f: File) = f.listFiles().exists { b =>
      b.getName == "bin" && b.listFiles().exists(x => x.getName == "javac.exe" || x.getName == "javac")
    }
    def inJvm(path: String, suffix: String) = {
      val postfix = if (path.startsWith("/Library")) "/Contents/Home" else ""  // mac workaround
      Option(new File(path))
        .filter(_.exists())
        .flatMap(_.listFiles()
          .sortBy(_.getName)
          .reverse
          .find(f => f.getName.contains(suffix) && isJDK(new File(f, postfix)))
          .map(new File(_, s"$postfix/jre").getAbsolutePath)
        )
    }
    def currentJava() = {
      sys.props.get("java.version") match {
        case Some(v) if v.startsWith(s"1.$versionMajor") =>
          sys.props.get("java.home") match {
            case Some(path) if isJDK(new File(path).getParentFile) =>
              Some(path)
            case _ => None
          }
        case _ => None
      }
    }
    val versionStrings = Seq(s"1.$versionMajor", s"-$versionMajor")
    val priorityPaths = Seq(
      currentJava(),
      Option(sys.env.getOrElse(s"JDK_1${versionMajor}_x64",
        sys.env.getOrElse(s"JDK_1$versionMajor", null))
      ).map(_+"/jre")  // teamcity style
    )
    if (priorityPaths.exists(_.isDefined)) {
      priorityPaths.flatten.headOption
    } else {
      val fullSearchPaths = paths flatMap { p => versionStrings.map((p, _)) }
      for ((path, ver) <- fullSearchPaths) {
        inJvm(path, ver) match {
          case x@Some(p) => return x
          case _ => None
        }
      }
      None
    }
  }
}
