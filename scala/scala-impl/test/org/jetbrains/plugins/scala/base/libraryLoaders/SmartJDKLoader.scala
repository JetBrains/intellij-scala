package org.jetbrains.plugins.scala
package base
package libraryLoaders

import java.io.File

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.projectRoots.{JavaSdk, JavaSdkVersion, Sdk}
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.IdeaTestUtil
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.junit.Assert

case class InternalJDKLoader() extends SmartJDKLoader() {
  override protected def createSdkInstance(): Sdk = JavaAwareProjectJdkTableImpl.getInstanceEx.getInternalJdk
}

/**
  * Consider using this instead of HeavyJDKLoader if you don't need java interop in your tests
  */
case class MockJDKLoader(jdkVersion: JavaSdkVersion = JavaSdkVersion.JDK_1_8) extends SmartJDKLoader(jdkVersion) {
  override protected def createSdkInstance(): Sdk = jdkVersion match {
    case JavaSdkVersion.JDK_1_9 => IdeaTestUtil.getMockJdk9
    case JavaSdkVersion.JDK_1_8 => IdeaTestUtil.getMockJdk18
    case JavaSdkVersion.JDK_1_7 => IdeaTestUtil.getMockJdk17
    case _ => Assert.fail(s"mock JDK version $jdkVersion is unavailable in IDEA test platform"); null
  }
}

case class HeavyJDKLoader(jdkVersion: JavaSdkVersion = JavaSdkVersion.JDK_1_8) extends SmartJDKLoader(jdkVersion) {
  override protected def createSdkInstance(): Sdk = SmartJDKLoader.getOrCreateJDK(jdkVersion)
}

abstract class SmartJDKLoader(jdkVersion: JavaSdkVersion = JavaSdkVersion.JDK_1_8) extends LibraryLoader {
  override def init(implicit module: Module, version: ScalaVersion): Unit = {
    ModuleRootModificationUtil.setModuleSdk(module, createSdkInstance())
  }

  override def clean(implicit module: Module): Unit = {
    ModuleRootModificationUtil.setModuleSdk(module, null)
    val jdkTable = JavaAwareProjectJdkTableImpl.getInstanceEx
    val allJdks = jdkTable.getAllJdks
    inWriteAction { allJdks.foreach(jdkTable.removeJdk) }
  }

  protected def createSdkInstance(): Sdk
}

object SmartJDKLoader {

  private val candidates = Seq(
    "/usr/lib/jvm",                     // linux style
    "C:\\Program Files\\Java\\",        // windows style
    "C:\\Program Files (x86)\\Java\\",  // windows 32bit style
    "/Library/Java/JavaVirtualMachines" // mac style
  )

  def getOrCreateJDK(jdkVersion: JavaSdkVersion = JavaSdkVersion.JDK_1_8): Sdk = {
    val jdkTable = JavaAwareProjectJdkTableImpl.getInstanceEx
    val jdkName = jdkVersion.getDescription
    Option(jdkTable.findJdk(jdkName)).getOrElse {
      val pathOption = SmartJDKLoader.discoverJDK(jdkVersion)
      Assert.assertTrue(s"Couldn't find $jdkVersion", pathOption.isDefined)
      VfsRootAccess.allowRootAccess(pathOption.get)
      val jdk = JavaSdk.getInstance.createJdk(jdkName, pathOption.get, false)
      inWriteAction { jdkTable.addJdk(jdk) }
      jdk
    }
  }

  private def discoverJDK(jdkVersion: JavaSdkVersion): Option[String] = discoverJre(candidates, jdkVersion).map(new File(_).getParent)

  private def discoverJre(paths: Seq[String], jdkVersion: JavaSdkVersion): Option[String] = {
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


