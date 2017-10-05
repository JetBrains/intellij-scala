package org.jetbrains.plugins.scala.lang.psi.impl

import com.intellij.openapi.project.Project
import com.intellij.psi.{JVMElementFactory, JVMElementFactoryProvider}

/**
 * @author Alefas
 * @since 15.05.12
 */
class ScalaFactoryProvider extends JVMElementFactoryProvider {
  def getFactory(project: Project): JVMElementFactory = {
    new ScalaPsiElementFactoryImpl()(project)
  }
}
