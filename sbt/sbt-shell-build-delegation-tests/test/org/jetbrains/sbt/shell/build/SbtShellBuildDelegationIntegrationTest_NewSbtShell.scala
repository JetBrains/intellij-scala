package org.jetbrains.sbt.shell.build

import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.sbt.project.RequiresJdk
import org.junit.experimental.categories.Category

@Category(Array(classOf[SlowTests2]))
@RequiresJdk(LanguageLevel.JDK_17)
class SbtShellBuildDelegationIntegrationTest_NewSbtShell
  extends SbtShellBuildDelegationIntegrationTestBase {

  override protected def useNewSbtShell: Boolean = true
}
