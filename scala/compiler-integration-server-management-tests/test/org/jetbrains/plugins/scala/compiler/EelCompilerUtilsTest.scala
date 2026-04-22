package org.jetbrains.plugins.scala.compiler

import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.EelPlatform.Arch
import com.intellij.platform.eel.provider.{EelNioBridgeServiceKt, LocalEelDescriptor}
import com.intellij.platform.testFramework.junit5.eel.fixture.{IsolatedFileSystem, FixturesKt as EelFixtures}
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import org.jetbrains.plugins.scala.extensions.PathExt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Path

@TestApplication
class EelCompilerUtilsTest:

  private val windowsFixture: TestFixture[IsolatedFileSystem] =
    EelFixtures.eelFixture(EelPlatform.Windows(Arch.X86_64.INSTANCE))

  private val eelTemporaryWindowsDirectory: TestFixture[Path] =
    EelFixtures.tempDirFixture(windowsFixture)

  private val macFixture: TestFixture[IsolatedFileSystem] =
    EelFixtures.eelFixture(EelPlatform.Darwin(Arch.ARM_64.INSTANCE))

  private val eelTemporaryMacDirectory: TestFixture[Path] =
    EelFixtures.tempDirFixture(macFixture)

  private val linuxFixture: TestFixture[IsolatedFileSystem] =
    EelFixtures.eelFixture(EelPlatform.Linux(Arch.Unknown.INSTANCE))

  private val eelTemporaryLinuxDirectory: TestFixture[Path] =
    EelFixtures.tempDirFixture(linuxFixture)

  @Test
  def asTargetLocalPathStringWindows(): Unit =
    val eelDescriptor = windowsFixture.get().getEelDescriptor
    val basePath = eelTemporaryWindowsDirectory.get()
    val baseTargetLocalPathString = EelNioBridgeServiceKt.asEelPath(basePath, eelDescriptor).toString
    val myCustomPath = basePath / "my" / "custom" / "path" / "file.txt"
    val actual = EelCompilerUtils.asTargetLocalPathString(myCustomPath, eelDescriptor)
    val expected = baseTargetLocalPathString + "\\my\\custom\\path\\file.txt"
    assertEquals(expected, actual)

  @Test
  def asTargetLocalPathStringLocalDescriptor(@TempDir basePath: Path): Unit =
    val eelDescriptor = LocalEelDescriptor.INSTANCE
    val baseTargetLocalPathString = EelNioBridgeServiceKt.asEelPath(basePath, eelDescriptor).toString
    val myCustomPath = basePath / "my" / "custom" / "path" / "file.txt"
    val actual = EelCompilerUtils.asTargetLocalPathString(myCustomPath, eelDescriptor)
    val sep = java.io.File.separator
    val expected = baseTargetLocalPathString + sep + "my" + sep + "custom" + sep + "path" + sep + "file.txt"
    assertEquals(expected, actual)

  @Test
  def asTargetLocalPathStringMac(): Unit =
    val eelDescriptor = macFixture.get().getEelDescriptor
    val basePath = eelTemporaryMacDirectory.get()
    val baseTargetLocalPathString = EelNioBridgeServiceKt.asEelPath(basePath, eelDescriptor).toString
    val myCustomPath = basePath / "my" / "custom" / "path" / "file.txt"
    val actual = EelCompilerUtils.asTargetLocalPathString(myCustomPath, eelDescriptor)
    val expected = baseTargetLocalPathString + "/my/custom/path/file.txt"
    assertEquals(expected, actual)

  @Test
  def asTargetLocalPathStringLinux(): Unit =
    val eelDescriptor = linuxFixture.get().getEelDescriptor
    val basePath = eelTemporaryLinuxDirectory.get()
    val baseTargetLocalPathString = EelNioBridgeServiceKt.asEelPath(basePath, eelDescriptor).toString
    val myCustomPath = basePath / "my" / "custom" / "path" / "file.txt"
    val actual = EelCompilerUtils.asTargetLocalPathString(myCustomPath, eelDescriptor)
    val expected = baseTargetLocalPathString + "/my/custom/path/file.txt"
    assertEquals(expected, actual)
