package org.jetbrains.plugins.scala
package lang
package psi
package impl

import com.intellij.openapi.project.Project
import com.intellij.psi.JVMElementFactoryProvider

final class ScalaFactoryProvider extends JVMElementFactoryProvider {

  override def getFactory(project: Project) = new ScalaPsiElementFactoryImpl(project)
}
