package org.jetbrains.sbt.project

import com.intellij.openapi.util.registry.Registry
import org.junit.Ignore

class SbtNewShellProjectStructureImportingTest extends SbtShellProjectStructureImportingTest {

  override def setUp(): Unit = {
    super.setUp()
    val newShellRegistry = Registry.get("sbt.new.shell")
    newShellRegistry.setValue(true)
  }

  // Tests with sbt < 1.5.0 are ignored because the new shell will be only available for sbt 1.5.0+

  @Ignore
  override def testSimpleSbt013(): Unit = ()

  @Ignore
  override def testSimpleSbt104(): Unit = ()

  @Ignore
  override def testSimpleSbt116(): Unit = ()

  @Ignore
  override def testSimpleSbt128(): Unit = ()

  @Ignore
  override def testSimpleSbt1313(): Unit = ()

  @Ignore
  override def testSimpleSbt149(): Unit = ()
}
