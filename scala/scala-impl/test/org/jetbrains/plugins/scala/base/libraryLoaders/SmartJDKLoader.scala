package org.jetbrains.plugins.scala.base.libraryLoaders

import java.io.File

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.projectRoots.{JavaSdk, Sdk}
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader.JDKVersion.JDKVersion
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader._
import org.jetbrains.plugins.scala.debugger.ScalaVersion
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.junit.Assert

case class SmartJDKLoader(jdkVersion: JDKVersion = JDKVersion.JDK18)(rootDisposable: Option[() => Disposable] = None) extends LibraryLoader {
  override def init(implicit module: Module, version: ScalaVersion): Unit = {
    val jdk = SmartJDKLoader.getOrCreateJDK(jdkVersion)
    ModuleRootModificationUtil.setModuleSdk(module, jdk)
    Disposer.register(rootDisposable.getOrElse(() => module.getProject)(), () => inWriteAction {
      JavaAwareProjectJdkTableImpl.getInstanceEx.removeJdk(jdk)
    })
  }
}

object SmartJDKLoader {

  object JDKVersion extends Enumeration {
    type JDKVersion = Value
    val JDK16, JDK17, JDK18, JDK19 = Value
  }

  private val candidates = Seq(
    "/usr/lib/jvm",                     // linux style
    "C:\\Program Files\\Java\\",        // windows style
    "C:\\Program Files (x86)\\Java\\",  // windows 32bit style
    "/Library/Java/JavaVirtualMachines" // mac style
  )

  def getOrCreateJDK(jdkVersion: JDKVersion = JDKVersion.JDK18): Sdk = {
    val jdkTable = JavaAwareProjectJdkTableImpl.getInstanceEx
    val jdkName = jdkVersion.toString
    Option(jdkTable.findJdk(jdkName)).getOrElse {
      val pathOption = SmartJDKLoader.discoverJDK(jdkVersion)
      Assert.assertTrue(s"Couldn't find $jdkVersion", pathOption.isDefined)
      VfsRootAccess.allowRootAccess(pathOption.get)
      val jdk = JavaSdk.getInstance.createJdk(jdkName, pathOption.get, false)
      inWriteAction { jdkTable.addJdk(jdk) }
      jdk
    }
  }

  private def discoverJDK(jdkVersion: JDKVersion): Option[String] = discoverJre(candidates, jdkVersion).map(new File(_).getParent)

  private def discoverJre(paths: Seq[String], jdkVersion: JDKVersion): Option[String] = {
    import java.io._

    val versionMajor = jdkVersion.toString.last.toString

    def isJDK(f: File) = f.listFiles().exists { b =>
      b.getName == "bin" && b.listFiles().exists(x => x.getName == "javac.exe" || x.getName == "javac")
    }
    def inJvm(path: String, suffix: String) = {
      val postfix = if (path.startsWith("/Library")) "/Contents/Home" else ""  // mac workaround
      Option(new File(path))
        .filter(_.exists())
        .flatMap(_.listFiles()
          .sortBy(_.getName) // TODO somehow sort by release number to get the newest actually
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


