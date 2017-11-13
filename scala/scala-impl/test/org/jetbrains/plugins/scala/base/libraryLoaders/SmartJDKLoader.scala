package org.jetbrains.plugins.scala.base.libraryLoaders

import java.io.File

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.projectRoots.{JavaSdk, Sdk}
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader.JDKVersion.JDKVersion
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader._
import org.jetbrains.plugins.scala.debugger.ScalaVersion
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.junit.Assert

//FIXME: case sensitive name warning
case class SmartJDKLoader(jdkVersion: JDKVersion = JDKVersion.JDK18) extends LibraryLoader {
  override def init(implicit module: Module, version: ScalaVersion): Unit = {
    val jdk = SmartJDKLoader.getOrCreateJDK(jdkVersion, module.getProject)
    ModuleRootModificationUtil.setModuleSdk(module, jdk)
  }
}

object SmartJDKLoader {

  object JDKVersion extends Enumeration {
    type JDKVersion = Value
    val JDK17, JDK18, JDK19 = Value
  }

  val candidates = Seq(
    "/usr/lib/jvm",                     // linux style
    "C:\\Program Files\\Java\\",        // windows style
    "C:\\Program Files (x86)\\Java\\",  // windows 32bit style
    "/Library/Java/JavaVirtualMachines" // mac style
  )

  def getOrCreateJDK(jdkVersion: JDKVersion = JDKVersion.JDK18, parentDisposable: Disposable): Sdk = {
    val jdkTable = JavaAwareProjectJdkTableImpl.getInstanceEx
    val jdkName = jdkVersion.toString
    Option(jdkTable.findJdk(jdkName)).getOrElse {
      val pathOption = SmartJDKLoader.discoverJDK(jdkName.last.toString)
      Assert.assertTrue(s"Couldn't find $jdkVersion", pathOption.isDefined)
      VfsRootAccess.allowRootAccess(pathOption.get)
      val jdk = JavaSdk.getInstance.createJdk(jdkName, pathOption.get, false)
      inWriteAction {
        jdkTable.addJdk(jdk, parentDisposable)
      }
      jdk
    }
  }

  def discoverJRE18(): Option[String] = discoverJre(candidates, "8")

  def discoverJRE16(): Option[String] = discoverJre(candidates, "6")

  def discoverJDK18(): Option[String] = discoverJRE18().map(new File(_).getParent)

  def discoverJDK16(): Option[String] = discoverJRE16().map(new File(_).getParent)

  def discoverJDK(versionMajor: String): Option[String] = discoverJre(candidates, versionMajor).map(new File(_).getParent)

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


