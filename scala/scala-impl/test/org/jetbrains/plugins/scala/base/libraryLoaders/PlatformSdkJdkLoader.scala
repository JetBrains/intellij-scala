package org.jetbrains.plugins.scala.base.libraryLoaders

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots.ModuleRootModificationUtil
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.extensions.inWriteAction

final class PlatformSdkJdkLoader(jdk: Sdk) extends LibraryLoader {
  override def init(implicit module: Module, version: ScalaVersion): Unit = {
    val jdkTable = JavaAwareProjectJdkTableImpl.getInstanceEx
    inWriteAction(jdkTable.addJdk(jdk))
    ModuleRootModificationUtil.setModuleSdk(module, jdk)
  }

  override def clean(implicit module: Module): Unit = {
    ModuleRootModificationUtil.setModuleSdk(module, null)
    val jdkTable = JavaAwareProjectJdkTableImpl.getInstanceEx
    inWriteAction(jdkTable.removeJdk(jdk))
  }
}
