package org.jetbrains.plugins.scala.debugger

import java.io.{File, FilenameFilter}
import java.security.MessageDigest

import com.intellij.openapi.compiler.CompilerPaths
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import org.apache.commons.codec.binary.Hex

class CompilationCache(private val module: Module, additionalKeys: Seq[String] = Seq.empty) {
  private val cacheRoot = new File(sys.props("user.home"), ".cache/IJ_scala_tests_cache/")
  private val cacheDebug = sys.props.get("IJ_scala_tests_cache.debug").nonEmpty
  private lazy val hash = computeHash()

  def withModuleOutputCache[T](defaultValue: T)(fun: => T): T = {
    if (!tryLoadFromCache()) {
      val result = fun
      saveToCache()
      result
    } else defaultValue
  }

  def makeCached(makeFun: => List[String]): List[String] = withModuleOutputCache(List[String]())(makeFun)

  private def saveToCache(): Unit = {
    val testRoot = testCacheRoot(hash)
    FileUtil.copyDirContent(new File(CompilerPaths.getModuleOutputPath(module, false)), testRoot)
  }

  private def tryLoadFromCache(): Boolean = {
    val cacheRoot = testCacheRoot(hash)
    val filter = new FilenameFilter { override def accept(file: File, s: String): Boolean = s.endsWith(".class") }
    if (cacheRoot.list(filter).nonEmpty) {
      val outputDir = new File(CompilerPaths.getModuleOutputPath(module, false))
      outputDir.mkdirs()
      FileUtil.copyDirContent(cacheRoot, outputDir)
      val timestamp = System.currentTimeMillis()
      outputDir.listFiles().foreach(_.setLastModified(timestamp)) // to avoid out-of-date class errors
      refreshVfs(outputDir.getAbsolutePath)
      if (cacheDebug) println(s"Loaded cache from: ${cacheRoot.getAbsolutePath}")
      true
    } else false
  }

  private def testCacheRoot(hash: String) = {
    val testRoot = new File(cacheRoot, s"$hash/")
    testRoot.mkdirs()
    testRoot
  }

  private def computeHash(): String = {
    val md5 = MessageDigest.getInstance("MD5")
    def computeSourceFiles(f: VirtualFile): Unit = {
      if (f.isDirectory)
        f.getChildren.foreach(computeSourceFiles)
      else
        md5.update(f.contentsToByteArray())
    }
    ModuleRootManager.getInstance(module).getSourceRoots.foreach(computeSourceFiles)
    additionalKeys.foreach(k => md5.update(k.getBytes))
    Hex.encodeHexString(md5.digest())
  }

  private def refreshVfs(path: String): Unit = {
    LocalFileSystem.getInstance.refreshAndFindFileByIoFile(new File(path)) match {
      case null =>
      case file => file.refresh(false, false)
    }
  }
}

object CompilationCache {
  def apply(module: Module, additionalKeys: Seq[String] = Seq.empty) = new CompilationCache(module, additionalKeys)
}
