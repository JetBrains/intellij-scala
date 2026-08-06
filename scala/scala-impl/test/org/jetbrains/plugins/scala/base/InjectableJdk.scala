package org.jetbrains.plugins.scala.base

import com.intellij.pom.java.LanguageLevel

trait InjectableJdk {

  private var _injectedJdkVersion: Option[LanguageLevel] = None
  def injectedJdkVersion: Option[LanguageLevel] = _injectedJdkVersion
  def injectedJdkVersion_=(value: LanguageLevel): Unit = _injectedJdkVersion = Some(value)

  def defaultJdkVersion: LanguageLevel = InjectableJdk.DefaultJdk

  def testProjectJdkVersion: LanguageLevel =
    injectedJdkVersion.getOrElse(defaultJdkVersion)

}

object InjectableJdk {

  val DefaultJdk = LanguageLevel.JDK_17
}
