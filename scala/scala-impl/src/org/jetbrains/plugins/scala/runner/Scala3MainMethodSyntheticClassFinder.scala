package org.jetbrains.plugins.scala.runner

import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.caches.{ModTracker, cachedInUserData}
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.psi.types.TypePresentationContext
import org.jetbrains.plugins.scala.runner.Scala3MainMethodSyntheticClass.MainMethodParameters
import org.jetbrains.plugins.scala.runner.Scala3MainMethodSyntheticClass.MainMethodParameters.CustomParameter

/**
 * This class is only needed during application configuration verification
 *
 * @see [[com.intellij.execution.application.ApplicationConfiguration#checkConfiguration()]]
 * @see [[com.intellij.execution.configurations.JavaRunConfigurationModule#checkClassName(java.lang.String, java.lang.String)]]
 */
private final class Scala3MainMethodSyntheticClassFinder(project: Project)
  extends PsiElementFinder {

  override def findClass(qualifiedName: String, scope: GlobalSearchScope): PsiClass = {
    if (qualifiedName.isEmpty) return null

    val project = scope.getProject
    if (project == null) return null

    findClass(qualifiedName, scope, project)
  }

  private def findClass(qualifiedName: String, scope: GlobalSearchScope, project: Project): Scala3MainMethodSyntheticClass = {
    import ScalaIndexKeys.StubIndexKeyExt
    val results = ScalaIndexKeys.ANNOTATED_MAIN_FUNCTION_BY_PKG_KEY.elements(qualifiedName, scope)(project)
    if (results.nonEmpty) {
      val function = results.head
      syntheticClassForFunction(function, qualifiedName)
    }
    else null
  }

  private def syntheticClassForFunction(function: ScFunction, qualifiedName: String): Scala3MainMethodSyntheticClass = cachedInUserData("syntheticClassForFunction", function, ModTracker.anyScalaPsiChange, (function, qualifiedName)) {
    val params = function.parameterList.params

    val mainParams = if (isDefaultMainVarargs(params))
      MainMethodParameters.Default
    else {
      val customParams = params.map(param => customParameter(param))
      MainMethodParameters.Custom(customParams)
    }

    new Scala3MainMethodSyntheticClass(
      PsiManager.getInstance(project),
      // HACK: the file doesn't actually contain the function,
      // but it's required during accessibility checks in application configuration validation
      function.getContainingFile,
      qualifiedName,
      mainParams
    )
  }

  private def isDefaultMainVarargs(params: Seq[ScParameter]): Boolean = {
    if (params.size == 1) {
      val param = params.head
      param.isVarArgs && {
        val typ = param.`type`()
        typ.exists(_.extractClass.exists(_.qualifiedName == "java.lang.String"))
      }
    } else {
      false
    }
  }

  private def customParameter(param: ScParameter): CustomParameter = {
    val typeText = param.`type`().fold(_ => "", _.presentableText(TypePresentationContext.emptyContext))
    CustomParameter(param.name, typeText, param.isVarArgs)
  }

  // Not implemented because it isn't required during application configuration verification
  override def findClasses(qualifiedName: String, scope: GlobalSearchScope): Array[PsiClass] = PsiClass.EMPTY_ARRAY
}
