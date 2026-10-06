package org.jetbrains.plugins.scala.base

import com.intellij.pom.java.LanguageLevel

trait InjectableJdk {

  // A non-generic state accessor avoids Scala 3's raw trait-field accessors in Java subclasses.
  private object JdkVersionInjection {
    var value: Option[LanguageLevel] = None
  }
  def injectedJdkVersion: Option[LanguageLevel] = JdkVersionInjection.value
  def injectedJdkVersion_=(value: LanguageLevel): Unit = JdkVersionInjection.value = Some(value)

  def defaultJdkVersion: LanguageLevel = InjectableJdk.DefaultJdk

  def testProjectJdkVersion: LanguageLevel =
    injectedJdkVersion.getOrElse(defaultJdkVersion)

}

object InjectableJdk {

  val DefaultJdk = LanguageLevel.JDK_17
}
