package org.jetbrains.plugins.scala.lang.psi.impl

import com.intellij.openapi.project.Project
import com.intellij.psi.JVMElementFactoryProvider

final class ScalaFactoryProvider extends JVMElementFactoryProvider {

  override def getFactory(project: Project) = new ScalaPsiElementFactoryImpl(project)
}
