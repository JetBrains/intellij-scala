package org.jetbrains.plugins.scala
package base
package libraryLoaders

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.projectRoots.{JavaSdk, JavaSdkVersion, Sdk}
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.util.SystemProperties
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.junit.Assert

import java.io.File
import scala.annotation.nowarn

case class InternalJDKLoader() extends SmartJDKLoader() {
  //noinspection ScalaDeprecation
  override protected def createSdkInstance(): Sdk = {
    JavaAwareProjectJdkTableImpl.getInstanceEx.getInternalJdk: @nowarn("cat=deprecation")
  }
}

/**
  * Consider using this instead of HeavyJDKLoader if you don't need java interop in your tests
  */
case class MockJDKLoader(languageLevel: LanguageLevel = LanguageLevel.JDK_17) extends SmartJDKLoader() {
  override protected def createSdkInstance(): Sdk = IdeaTestUtil.getMockJdk(languageLevel.toJavaVersion)
}

case class HeavyJDKLoader(languageLevel: LanguageLevel = LanguageLevel.JDK_17) extends SmartJDKLoader() {
  override protected def createSdkInstance(): Sdk = SmartJDKLoader.getOrCreateJDK(languageLevel)
}

abstract class SmartJDKLoader() extends LibraryLoader {
  private lazy val instance: Sdk = createSdkInstance()

  override def init(implicit module: Module, version: ScalaVersion): Unit = {
    ModuleRootModificationUtil.setModuleSdk(module, instance)
  }

  override def clean(implicit module: Module): Unit = {
    ModuleRootModificationUtil.setModuleSdk(module, null)
    val jdkTable = JavaAwareProjectJdkTableImpl.getInstanceEx
    inWriteAction(jdkTable.removeJdk(instance))
  }

  protected def createSdkInstance(): Sdk
}

object SmartJDKLoader {

  private val jdkPaths = {
    val userHome = SystemProperties.getUserHome
    Seq(
      "/usr/lib/jvm",                      // linux style
      "C:\\Program Files\\Java\\",         // windows style
      "C:\\Program Files (x86)\\Java\\",   // windows 32bit style
      "/Library/Java/JavaVirtualMachines", // mac style
      userHome + "/Downloads/jdk", // mac style
      userHome + "/Library/Java/JavaVirtualMachines", // mac style
      userHome + "/.jabba/jdk", // jabba (for github actions)
      userHome + "/.jdks", // by default IDEA downloads JDKs here
      userHome + "/.sdkman/candidates/java" // SDKMAN style
    )
  }

  def getOrCreateJDK(languageLevel: LanguageLevel = LanguageLevel.JDK_17): Sdk = {
    val jdkVersion = JavaSdkVersion.fromLanguageLevel(languageLevel)
    val jdkName = jdkVersion.getDescription

    val jdkTable = JavaAwareProjectJdkTableImpl.getInstanceEx
    val registeredJdkFromTable = Option(jdkTable.findJdk(jdkName))
    registeredJdkFromTable.getOrElse {
      val jdk = createNewJdk(jdkVersion, jdkName)
      inWriteAction {
        jdkTable.addJdk(jdk)
      }
      jdk
    }
  }

  private def createNewJdk(jdkVersion: JavaSdkVersion, jdkName: String): Sdk = {
    val pathOption = SmartJDKLoader.discoverJDK(jdkVersion).map(_.getAbsolutePath)
    Assert.assertTrue(s"Couldn't find $jdkVersion", pathOption.isDefined)

    VfsRootAccess.allowRootAccess(ApplicationManager.getApplication, pathOption.get)
    JavaSdk.getInstance.createJdk(jdkName, pathOption.get, false)
  }

  private def discoverJDK(jdkVersion: JavaSdkVersion): Option[File] =
    discoverJre(jdkPaths, jdkVersion)

  private def discoverJre(paths: Seq[String], jdkVersion: JavaSdkVersion): Option[File] = {
    val versionMajor = jdkVersion.ordinal().toString
    val versionStrings = Seq(s"1.$versionMajor", s"-$versionMajor", s"jdk$versionMajor")
    val fromEnv = sys.env.get(jdkVersion.toString).orElse(sys.env.get(s"${jdkVersion}_0"))
    val fromEnv64 = sys.env.get(s"${jdkVersion}_x64").orElse(sys.env.get(s"${jdkVersion}_0_x64")) // teamcity style
    val priorityPaths = Seq(currentJava(versionMajor), fromEnv.orElse(fromEnv64)).flatten.map(new File(_))

    priorityPaths.headOption
      .orElse {
        val fullSearchPaths = paths.flatMap { p => versionStrings.map((p, _)) }
        val validPaths = fullSearchPaths.flatMap((inJvm _).tupled)
        validPaths.headOption
      }
  }

  private def findJDK(dir: File) = {
    val macDir = new File(dir, "/Contents/Home") // mac workaround
    val candidates = List(macDir, dir, new File(dir, "/Home"))
    candidates
      .filter(_.isDirectory)
      .find { _
        .listFiles()
        .exists { b =>
          b.getName == "bin" &&
            b.listFiles().exists(x => x.getName == "javac.exe" || x.getName == "javac")
        }
      }.orElse(Some(dir))
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
    Some(SystemInfo.JAVA_VERSION)
      .filter(v => v.startsWith(s"1.$versionMajor") || v.startsWith(versionMajor))
      .flatMap(_ => sys.props.get("java.home"))
}


