package org.jetbrains.bsp.data

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.module.Module
import org.jetbrains.sbt.project.data.service.DefaultDataService

// NOTICE: data service classes need to be registered in BSP.xml

class BspMetadataService extends DefaultDataService[BspMetadata, Module](BspMetadata.Key)
