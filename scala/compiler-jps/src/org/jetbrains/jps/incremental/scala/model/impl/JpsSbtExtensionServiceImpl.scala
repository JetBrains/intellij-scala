package org.jetbrains.jps.incremental.scala.model.impl

import org.jetbrains.jps.incremental.scala.model.impl.JpsSbtModuleExtensionImpl.ProductionOnTestRole
import org.jetbrains.jps.incremental.scala.model.{JpsSbtExtensionService, JpsSbtModuleExtension}
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.java.impl.JpsJavaAwareProject
import org.jetbrains.jps.model.module.{JpsDependencyElement, JpsModule}

final class JpsSbtExtensionServiceImpl extends JpsSbtExtensionService {

  override def getExtension(module: JpsModule): Option[JpsSbtModuleExtension] = {
    Option(module.getContainer.getChild(JpsSbtModuleExtensionImpl.Role))
  }

  override def getOrCreateExtension(module: JpsModule, moduleType: Option[String], displayModuleName: Option[String]): JpsSbtModuleExtension =
    module.getContainer.getChild(JpsSbtModuleExtensionImpl.Role) match {
      case null =>
        val extension = new JpsSbtModuleExtensionImpl(moduleType, displayModuleName)
        module.getContainer.setChild(JpsSbtModuleExtensionImpl.Role, extension)
        extension
      case extension =>
        extension
    }

  //note: the code based on JpsGradleExtensionServiceImpl#isProductionOnTestDependency
  override def isProductionOnTestDependency(dependency: JpsDependencyElement): Boolean = {
    val project = dependency.getContainingModule.getProject
    project match {
      case javaAwareProject: JpsJavaAwareProject =>
        javaAwareProject.isProductionOnTestDependency(dependency)
      case _ =>
        val child = dependency.getContainer.getChild(ProductionOnTestRole)
        child != null && child.getData
    }
  }

  //note: the code based on JpsGradleExtensionServiceImpl#setProductionOnTestDependency
  override def setProductionOnTestDependency(dependency: JpsDependencyElement, value: Boolean): Unit = {
    val container = dependency.getContainer
    if (value) {
      container.setChild(ProductionOnTestRole, JpsElementFactory.getInstance.createSimpleElement(true))
    } else {
      container.removeChild(ProductionOnTestRole)
    }
  }
}
