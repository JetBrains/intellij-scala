package org.jetbrains.plugins.scala.debugger

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

  def discoverJDK18() = {
    import java.io._
    def isJDK(f: File) = f.listFiles().exists { b =>
      b.getName == "bin" && b.listFiles().exists(x => x.getName == "javac.exe" || x.getName == "javac")
    }
    def inJvm(path: String, suffix: String, postfix: String = "") = {
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
        case Some(version) if version.startsWith("1.8") =>
          sys.props.get("java.home") match {
            case Some(path) if isJDK(new File(path).getParentFile) =>
              println(s"found current jdk under $path")
              Some(path)
            case _ => None
          }
        case _ => None
      }
    }
    val candidates = Seq(
      currentJava(),
      Option(sys.env.getOrElse("JDK_18_x64", sys.env.getOrElse("JDK_18", null))).map(_+"/jre"),  // teamcity style
      inJvm("/usr/lib/jvm", "1.8"),                   // oracle style
      inJvm("/usr/lib/jvm", "-8"),                    // openjdk style
      inJvm("C:\\Program Files\\Java\\", "1.8"),      // oracle windows style
      inJvm("C:\\Program Files (x86)\\Java\\", "1.8"),      // oracle windows style
      inJvm("/Library/Java/JavaVirtualMachines", "1.8", "/Contents/Home")// mac style
    )
    candidates.flatten.headOption
  }

  def addJdk8(): Sdk = {
    val jdkTable = JavaAwareProjectJdkTableImpl.getInstanceEx
    val pathDefault = TestUtils.getTestDataPath.replace("\\", "/") + "/mockJDK1.8/jre"
    val path = discoverJDK18().getOrElse(pathDefault)
    val jdk = JavaSdk.getInstance.createJdk(jdk8Name, path)
    val oldJdk = jdkTable.findJdk(jdk8Name)
    inWriteAction {
      if (oldJdk != null) jdkTable.removeJdk(oldJdk)
      jdkTable.addJdk(jdk)
    }
    jdk
  }

  def findJdk8(): Sdk = {
    val jdkTable = JavaAwareProjectJdkTableImpl.getInstanceEx
    Option(jdkTable.findJdk(jdk8Name)).getOrElse {
      val pathDefault = TestUtils.getTestDataPath.replace("\\", "/") + "/mockJDK1.8/jre"
      val path = discoverJDK18().getOrElse(pathDefault)
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
}
