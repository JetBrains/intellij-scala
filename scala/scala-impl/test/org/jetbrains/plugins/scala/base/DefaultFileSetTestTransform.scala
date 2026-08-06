package org.jetbrains.plugins.scala.base

import com.intellij.psi.impl.DebugUtil

trait DefaultFileSetTestTransform { self: NoSdkFileSetTestBase =>
  override protected def transform(testName: String, fileText: String): String = {
    val lightFile = createLightFile(fileText)
    //noinspection ScalaWrongPlatformMethodsUsage
    DebugUtil.psiToString(lightFile, true).replace(": " + lightFile.getName, "")
  }
}
