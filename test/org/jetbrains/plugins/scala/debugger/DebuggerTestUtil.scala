package org.jetbrains.plugins.scala.debugger

import java.io.File

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.projectRoots.{JavaSdk, Sdk}
import org.jetbrains.plugins.scala.compiler.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.util.TestUtils

/**
  * @author Nikolay.Tropin
  */
object DebuggerTestUtil {
  val jdk8Name = "java 1.8"

  def findJdk8(): Sdk = {
    val jdkTable = JavaAwareProjectJdkTableImpl.getInstanceEx
    Option(jdkTable.findJdk(jdk8Name)).getOrElse {
      val pathDefault = TestUtils.getTestDataPath.replace("\\", "/") + "/mockJDK1.8/jre"
      val path = discoverJRE18().getOrElse(pathDefault)
      val jdk = JavaSdk.getInstance.createJdk(jdk8Name, path)
      inWriteAction {
        jdkTable.addJdk(jdk)
      }
      jdk
    }
  }

  def setCompileServerSettings(): Unit = {
    val compileServerSettings = ScalaCompileServerSettings.getInstance()
    compileServerSettings.COMPILE_SERVER_ENABLED = true
    compileServerSettings.COMPILE_SERVER_SDK = DebuggerTestUtil.jdk8Name
    compileServerSettings.COMPILE_SERVER_SHUTDOWN_IDLE = true
    compileServerSettings.COMPILE_SERVER_SHUTDOWN_DELAY = 30
    ApplicationManager.getApplication.saveSettings()
  }

  val candidates = Seq(
    "/usr/lib/jvm",                     // linux style
    "C:\\Program Files\\Java\\",        // windows style
    "C:\\Program Files (x86)\\Java\\",  // windows 32bit style
    "/Library/Java/JavaVirtualMachines" // mac style
  )

  def discoverJRE18() = discoverJre(candidates, "8")

  def discoverJRE16() = discoverJre(candidates, "6")

  def discoverJDK18() = discoverJRE18().map(new File(_).getParent)

  def discoverJDK16() = discoverJRE16().map(new File(_).getParent)

  def discoverJre(paths: Seq[String], versionMajor: String): Option[String] = {
    import java.io._
    def isJDK(f: File) = f.listFiles().exists { b =>
      b.getName == "bin" && b.listFiles().exists(x => x.getName == "javac.exe" || x.getName == "javac")
    }
    def inJvm(path: String, suffix: String) = {
      val postfix = if (path.startsWith("/Library")) "/Contents/Home" else ""  // mac workaround
      postfix
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
