package org.jetbrains.plugins.scala
package base
package libraryLoaders

import java.io.File

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.projectRoots.{JavaSdk, JavaSdkVersion, Sdk}
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.junit.Assert

case class InternalJDKLoader() extends SmartJDKLoader() {
  //noinspection ScalaDeprecation
  override protected def createSdkInstance(): Sdk = JavaAwareProjectJdkTableImpl.getInstanceEx.getInternalJdk
}

/**
  * Consider using this instead of HeavyJDKLoader if you don't need java interop in your tests
  */
case class MockJDKLoader(languageLevel: LanguageLevel = LanguageLevel.JDK_11) extends SmartJDKLoader() {
  override protected def createSdkInstance(): Sdk = IdeaTestUtil.getMockJdk(languageLevel.toJavaVersion)
}

case class HeavyJDKLoader(languageLevel: LanguageLevel = LanguageLevel.JDK_11) extends SmartJDKLoader() {
  override protected def createSdkInstance(): Sdk = SmartJDKLoader.getOrCreateJDK(languageLevel)
}

abstract class SmartJDKLoader() extends LibraryLoader {
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

  private val jdkPaths = Seq(
    "/usr/lib/jvm",                      // linux style
    "C:\\Program Files\\Java\\",         // windows style
    "C:\\Program Files (x86)\\Java\\",   // windows 32bit style
    "/Library/Java/JavaVirtualMachines", // mac style
    System.getProperty("user.home") + "/.jabba/jdk" // jabba (for github actions)
  )

  def getOrCreateJDK(languageLevel: LanguageLevel = LanguageLevel.JDK_11): Sdk = {
    val jdkVersion = JavaSdkVersion.fromLanguageLevel(languageLevel)
    val jdkName = jdkVersion.getDescription

    val jdkTable = JavaAwareProjectJdkTableImpl.getInstanceEx
    Option(jdkTable.findJdk(jdkName)).getOrElse {
      val pathOption = SmartJDKLoader.discoverJDK(jdkVersion).map(_.getAbsolutePath)
      Assert.assertTrue(s"Couldn't find $jdkVersion", pathOption.isDefined)
      VfsRootAccess.allowRootAccess(pathOption.get)
      val jdk = JavaSdk.getInstance.createJdk(jdkName, pathOption.get, false)
      inWriteAction { jdkTable.addJdk(jdk) }
      jdk
    }
  }

  private def discoverJDK(jdkVersion: JavaSdkVersion): Option[File] =
    discoverJre(jdkPaths, jdkVersion)

  private def discoverJre(paths: Seq[String], jdkVersion: JavaSdkVersion): Option[File] = {
    val versionMajor = jdkVersion.ordinal().toString
    val versionStrings = Seq(s"1.$versionMajor", s"-$versionMajor")
    val fromEnv64 = sys.env.get(s"${jdkVersion}_x64") // teamcity style
    val fromEnv = sys.env.get(jdkVersion.toString)
    val priorityPaths = Seq(currentJava(versionMajor), fromEnv64.orElse(fromEnv).map(new File(_))).flatten

    priorityPaths.headOption
      .orElse {
        val fullSearchPaths = paths.flatMap { p => versionStrings.map((p, _)) }
        val validPaths = fullSearchPaths.flatMap((inJvm _).tupled)
        validPaths.headOption
      }
  }

  private def findJDK(dir: File) = {
    val macDir = new File(dir, "/Contents/Home") // mac workaround
    val candidates = List(macDir, dir)
    candidates
      .filter(_.isDirectory)
      .find { _
        .listFiles()
        .exists { b =>
          b.getName == "bin" &&
            b.listFiles().exists(x => x.getName == "javac.exe" || x.getName == "javac")
        }
    }
  }

  private def inJvm(path: String, versionString: String): List[File] =
    List(new File(path))
      .filter(_.exists())
      .flatMap { dir =>
        dir
          .listFiles()
          .sortBy(_.getName) // TODO somehow sort by release number to get the newest actually
          .reverse
          .filter(_.getName.contains(versionString))
          .flatMap(findJDK)
        }

  private def currentJava(versionMajor: String) =
    sys.props.get("java.version")
      .filter(v => v.startsWith(s"1.$versionMajor") || v.startsWith(versionMajor))
      .flatMap(_ => sys.props.get("java.home"))
      .flatMap(d => findJDK(new File(d).getParentFile))
}


