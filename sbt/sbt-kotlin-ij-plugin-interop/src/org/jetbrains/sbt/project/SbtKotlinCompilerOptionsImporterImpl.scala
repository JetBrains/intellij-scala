package org.jetbrains.sbt.project

import com.intellij.facet.{FacetManager, FacetModel, ModifiableFacetModel}
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.util.execution.ParametersListUtil
import org.jetbrains.annotations.{Nullable, TestOnly}
import org.jetbrains.kotlin.config.CompilerSettings
import org.jetbrains.kotlin.idea.facet.{KotlinFacet, KotlinFacetConfiguration, KotlinFacetType}

import java.util
import java.util.Collections

final class SbtKotlinCompilerOptionsImporterImpl extends SbtKotlinCompilerOptionsImporter {
  override def setAdditionalArguments(module: Module, options: util.List[String], modelsProvider: IdeModifiableModelsProvider): Unit = {
    if (options.isEmpty)
      return

    val facetModel = modelsProvider.getModifiableFacetModel(module)
    val facet = findOrCreateFacet(module, facetModel)
    setAdditionalArguments(facet, options)
  }

  private def setAdditionalArguments(
    facet: KotlinFacet,
    options: util.List[String]
  ): Unit = {
    val settings = facet.getConfiguration.getSettings
    settings.setUseProjectSettings(false)

    val compilerSettings = Option(settings.getCompilerSettings).getOrElse(new CompilerSettings())
    compilerSettings.setAdditionalArguments(ParametersListUtil.join(options))
    settings.setCompilerSettings(compilerSettings)
    settings.updateMergedArguments()
  }

  @TestOnly
  override def getAdditionalArguments(module: Module): util.List[String] = {
    val facet = findFacet(module)
    if (facet == null) return Collections.emptyList()
    val compilerSettings = facet.getConfiguration.getSettings.getCompilerSettings
    if (compilerSettings == null) return Collections.emptyList()
    val additionalArguments = compilerSettings.getAdditionalArguments
    if (additionalArguments.isBlank) return Collections.emptyList()
    ParametersListUtil.parse(additionalArguments)
  }

  private def findOrCreateFacet(module: Module, model: ModifiableFacetModel): KotlinFacet = {
    val facet = findFacet(model)
    if (facet != null) facet else createFacetAndAddToModule(module, model)
  }

  private def createFacetAndAddToModule(module: Module, model: ModifiableFacetModel): KotlinFacet = {
    val facet = createFacet(module)
    model.addFacet(facet)
    facet
  }

  @Nullable
  private def findFacet(module: Module): KotlinFacet = {
    val facetManager = FacetManager.getInstance(module)
    findFacet(facetManager)
  }

  @Nullable
  private def findFacet(model: FacetModel): KotlinFacet =
    model.getAllFacets.collectFirst { case facet: KotlinFacet => facet }.orNull

  private def createFacet(module: Module): KotlinFacet = {
    val facetType = KotlinFacetType.Companion.getINSTANCE
    val configuration: KotlinFacetConfiguration = facetType.createDefaultConfiguration()
    facetType.createFacet(module, facetType.getDefaultFacetName, configuration, null)
  }
}
