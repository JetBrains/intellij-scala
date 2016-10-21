package scala.meta

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

trait ScalametaUtils {

  protected val metaVersion = "0.23.0"

  protected def scalaSdkVersion: ScalaSdkVersion = ScalaSdkVersion._2_11_8

  def getMetaLibraries: Seq[(String, String, Seq[String])] = {
    val scala = scalaSdkVersion.getMajor
    def getLibEntry(component: String) = {
      (s"scalameta-$component", s"org.scalameta/${component}_$scala/jars", Seq(s"${component}_$scala-$metaVersion.jar"))
    }
    Seq("common", "dialects", "inline",
      "inputs", "parsers", "quasiquotes", "scalameta",
      "tokenizers", "tokens", "transversers", "trees").map(getLibEntry)
  }

  protected def addIvyCacheLibraryToModule(module: Module)(libraryName: String, libraryPath: String, jarNames: Seq[String]): Unit = {
    val libsPath = TestUtils.getIvyCachePath
    val pathExtended = s"$libsPath/$libraryPath/"
    VfsRootAccess.allowRootAccess(pathExtended)
    PsiTestUtil.addLibrary(module, libraryName, pathExtended, jarNames: _*)
  }

  protected def addAllMetaLibraries(module: Module) = {
    val addToModule = addIvyCacheLibraryToModule(module) _
    getMetaLibraries.foreach(addToModule.tupled)
  }
}
