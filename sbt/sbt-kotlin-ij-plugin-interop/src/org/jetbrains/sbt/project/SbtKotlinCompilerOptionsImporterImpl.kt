package org.jetbrains.sbt.project

import com.intellij.facet.FacetManager
import com.intellij.facet.ModifiableFacetModel
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.util.execution.ParametersListUtil
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.config.CompilerSettings
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.facet.KotlinFacetConfiguration
import org.jetbrains.kotlin.idea.facet.KotlinFacetType

class SbtKotlinCompilerOptionsImporterImpl : SbtKotlinCompilerOptionsImporter {

  override fun setAdditionalArguments(module: Module, options: List<String>, modelsProvider: IdeModifiableModelsProvider) {
    if (options.isEmpty())
      return

    val facetModel = modelsProvider.getModifiableFacetModel(module)
    val facet = findOrCreateFacet(module, facetModel)
    setAdditionalArguments(facet, options)
  }

  private fun setAdditionalArguments(
      facet: KotlinFacet,
      options: List<String>
  ) {
    val settings = facet.configuration.settings
    settings.useProjectSettings = false

    val compilerSettings = settings.compilerSettings ?: CompilerSettings()
    compilerSettings.additionalArguments = ParametersListUtil.join(options)
    settings.compilerSettings = compilerSettings
    settings.updateMergedArguments()
  }

  @TestOnly
  override fun getAdditionalArguments(module: Module): List<String> {
    val facet = findFacet(module) ?: return emptyList()
    val compilerSettings = facet.configuration.settings.compilerSettings ?: return emptyList()
    val additionalArguments = compilerSettings.additionalArguments
    if (additionalArguments.isBlank()) return emptyList()
    return ParametersListUtil.parse(additionalArguments)
  }

  private fun findOrCreateFacet(module: Module): KotlinFacet {
    val facet = findFacet(module)
    return facet ?: createFacetAndAddToModule(module)
  }

  private fun findOrCreateFacet(module: Module, model: ModifiableFacetModel): KotlinFacet {
    val facet = findFacet(model)
    return facet ?: createFacetAndAddToModule(module, model)
  }

  private fun createFacetAndAddToModule(module: Module): KotlinFacet {
    val facetManager = FacetManager.getInstance(module)
    val facet = createFacet(module)

    val model = facetManager.createModifiableModel()
    model.addFacet(facet)
    model.commit()

    return facet
  }

  private fun createFacetAndAddToModule(module: Module, model: ModifiableFacetModel): KotlinFacet {
    val facet = createFacet(module)
    model.addFacet(facet)
    return facet
  }

  private fun findFacet(module: Module): KotlinFacet? =
    FacetManager.getInstance(module).allFacets.filterIsInstance<KotlinFacet>().firstOrNull()

  private fun findFacet(model: ModifiableFacetModel): KotlinFacet? =
    model.allFacets.filterIsInstance<KotlinFacet>().firstOrNull()

  private fun createFacet(module: Module): KotlinFacet {
    val facetType = KotlinFacetType.INSTANCE
    val configuration: KotlinFacetConfiguration = facetType.createDefaultConfiguration()
    return facetType.createFacet(module, facetType.defaultFacetName, configuration, null)
  }
}
