package org.jetbrains.plugins.scala.compiler.zinc

import com.intellij.openapi.compiler.{CompilerMessage, CompilerMessageCategory}
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.testFramework.CompilerTester
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.SbtProjectSystem
import org.junit.Assert.{assertNotNull, assertTrue}

import java.nio.file.Path

abstract class ZincTestBase extends ExternalSystemImportingTestCase  {

  override def getExternalSystemId: ProjectSystemId = SbtProjectSystem.Id

  override def getExternalSystemConfigFileName: String = Sbt.BuildFile

  protected var compiler: CompilerTester = _

  protected var sdk: Sdk = _

  protected var rootModule: Module = _

  protected def assertNoErrorsOrWarnings(messages: Seq[CompilerMessage]): Unit = {
    val errorsAndWarnings = messages.filter { message =>
      val category = message.getCategory
      category == CompilerMessageCategory.ERROR || category == CompilerMessageCategory.WARNING
    }
    assertTrue(s"Expected no compilation errors or warnings, got: ${errorsAndWarnings.mkString(System.lineSeparator())}", errorsAndWarnings.isEmpty)
  }

  protected def findClassFileInRootModule(name: String): Path =
    findClassFile(rootModule, name)

  protected def findClassFile(module: Module, name: String): Path = {
    val cls = compiler.findClassFile(name, module)
    assertNotNull(s"Could not find compiled class file $name", cls)
    cls.toPath
  }

  protected def removeFile(path: Path): Unit = {
    val virtualFile = VfsUtil.findFileByIoFile(path.toFile, true)
    inWriteAction {
      virtualFile.delete(null)
    }
  }

  protected def assertCompilingScalaSources(messages: Seq[CompilerMessage], number: Int): Unit = {
    val message = messages.find { message =>
      val text = message.getMessage
      text.contains("compiling") && text.contains("Scala source")
    }.orNull
    assertNotNull("Could not find Compiling Scala sources message", message)
    val expected = s"compiling $number Scala source"
    val text = message.getMessage
    assertTrue(s"Compiling wrong number of Scala sources, expected '$expected', got '$text'", text.contains(expected))
  }
}
