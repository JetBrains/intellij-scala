package org.jetbrains.sbt.integration

import com.intellij.lang.properties.codeInspection.unused.ImplicitPropertyUsageProvider
import com.intellij.lang.properties.psi.Property
import com.intellij.psi.PsiDirectory
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.sbt.Sbt

class SbtPropertyUsageProvider extends ImplicitPropertyUsageProvider {
  override def isUsed(property: Property): Boolean = property.containingFile.exists { file =>
    file.getName == Sbt.PropertiesFile &&
      file.parent.exists(_.asOptionOf[PsiDirectory].exists(_.getName == Sbt.ProjectDirectory))
  }
}
