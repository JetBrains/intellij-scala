package org.jetbrains.plugins.scala.lang.scaladoc.reflinks

import com.intellij.lang.{DependentLanguage, Language}
import org.jetbrains.plugins.scala.ScalaLanguage

final class ScalaDocRefLinkLanguage
  extends Language(ScalaLanguage.INSTANCE, "ScalaDocRefLink")
    with DependentLanguage

object ScalaDocRefLinkLanguage {
  val INSTANCE: ScalaDocRefLinkLanguage = new ScalaDocRefLinkLanguage
}