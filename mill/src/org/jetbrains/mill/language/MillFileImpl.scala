package org.jetbrains.mill
package language


import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.{Module, ModuleManager, ModuleUtilCore}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi._
import com.intellij.psi.search.{GlobalSearchScope, searches}
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.caches.{ModTracker, cached, cachedInUserData}
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.ScDeclarationSequenceHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl._
import org.jetbrains.plugins.scala.project.{ModuleExt, ScalaFeatures}
import org.jetbrains.sbt.project.SbtBuildModuleUriProvider
import org.jetbrains.sbt.project.module.SbtModule.{Build, Imports}

import scala.jdk.CollectionConverters._

final class MillFileImpl private[language](provider: FileViewProvider)
  extends ScalaFileImpl(provider, MillFileType, MillLanguage.INSTANCE)
    with MillFile {



}
