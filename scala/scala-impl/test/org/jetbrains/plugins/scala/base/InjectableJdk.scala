package org.jetbrains.plugins.scala.base

import com.intellij.pom.java.LanguageLevel

trait InjectableJdk {

  private var _injectedJdkVersion: Option[LanguageLevel] = None
  def injectedJdkVersion: Option[LanguageLevel] = _injectedJdkVersion
  def injectedJdkVersion_=(value: LanguageLevel): Unit = _injectedJdkVersion = Some(value)

  def testProjectJdkVersion: LanguageLevel =
    injectedJdkVersion.getOrElse(LanguageLevel.JDK_11)
}
