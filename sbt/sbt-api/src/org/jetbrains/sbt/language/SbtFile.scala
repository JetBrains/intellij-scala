package org.jetbrains.sbt.language

import com.intellij.openapi.module.Module
import com.intellij.psi.impl.source.PsiFileWithStubSupport

trait SbtFile extends PsiFileWithStubSupport {
  def findBuildModule(module: Module): Option[Module]
}