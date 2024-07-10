package org.jetbrains.jps.incremental.scala.model.impl

import org.jetbrains.jps.incremental.scala.model.{JpsSbtExtensionService, JpsSbtModuleExtension}
import org.jetbrains.jps.model.module.JpsModule

final class JpsSbtExtensionServiceImpl extends JpsSbtExtensionService {

  override def getExtension(module: JpsModule): Option[JpsSbtModuleExtension] = {
    Option(module.getContainer.getChild(JpsSbtModuleExtensionImpl.Role))
  }

  override def getOrCreateExtension(module: JpsModule, moduleType: Option[String]): JpsSbtModuleExtension =
    module.getContainer.getChild(JpsSbtModuleExtensionImpl.Role) match {
      case null =>
        val extension = new JpsSbtModuleExtensionImpl(moduleType)
        module.getContainer.setChild(JpsSbtModuleExtensionImpl.Role, extension)
        extension
      case extension =>
        extension
    }
}
