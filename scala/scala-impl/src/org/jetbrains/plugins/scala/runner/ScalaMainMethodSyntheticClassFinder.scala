package org.jetbrains.plugins.scala.runner

import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.caches.ModTracker
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.adapters.PsiClassAdapter
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.PsiClassFake
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.psi.types.TypePresentationContext
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData
import org.jetbrains.plugins.scala.runner.Scala3MainMethodSyntheticClass.MainMethodParameters.CustomParameter
import org.jetbrains.plugins.scala.runner.Scala3MainMethodSyntheticClass.{MainMethodParameters, extractNameFromFqn}

import scala.annotation.unused

/**
 * This class is only needed during application configuration verification
 *
 * @see [[com.intellij.execution.application.ApplicationConfiguration#checkConfiguration()]]
 * @see [[com.intellij.execution.configurations.JavaRunConfigurationModule#checkClassName(java.lang.String, java.lang.String)]]
 */
@unused("registered in scala-plugin-common.xml")
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

  @CachedInUserData(function, ModTracker.anyScalaPsiChange)
  private def syntheticClassForFunction(function: ScFunction, qualifiedName: String): Scala3MainMethodSyntheticClass = {
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

private final class Scala3MainMethodSyntheticClass(
  psiManager: PsiManager,
  containingFile: PsiFile,
  qualifiedName: String,
  val parameters: MainMethodParameters,
) extends LightElement(psiManager, ScalaLanguage.INSTANCE)
  with PsiNameIdentifierOwner
  with PsiClassAdapter
  with PsiClassFake {

  override val getName: String = extractNameFromFqn(qualifiedName)
  override def getQualifiedName: String = qualifiedName

  override def getText = ""
  override def getNameIdentifier: PsiIdentifier = null

  override def getContainingFile: PsiFile = containingFile
  override def getContext: PsiFile = containingFile

  override def toString = s"synthetic class for scala @main method: $qualifiedName"

  override def setName(newName: String): PsiElement = throw new IncorrectOperationException("nonphysical element")
  override def copy = throw new IncorrectOperationException("nonphysical element")
  override def accept(v: PsiElementVisitor): Unit = throw new IncorrectOperationException("should not call")

  override def processDeclarations(
    processor: PsiScopeProcessor,
    state: ResolveState,
    lastParent: PsiElement,
    place: PsiElement
  ): Boolean = {
    // NOTE: we probably need add some fake psi file with all fake @main method classes declarations
    // strictly speaking ScalaMainMethodSyntheticClass can't declare itself, but this solution works...
    processor.execute(this, state)
    false
  }
}

private object Scala3MainMethodSyntheticClass {

  sealed trait MainMethodParameters

  object MainMethodParameters {
    object Default extends MainMethodParameters // (args: String*)
    case class Custom(parameterNames: Seq[CustomParameter]) extends MainMethodParameters

    case class CustomParameter(name: String, typ: String, isVararg: Boolean)
  }

  private def extractNameFromFqn(qualifiedName: String): String =
    qualifiedName.lastIndexOf('.') match {
      case -1  => qualifiedName // in root package
      case idx => qualifiedName.substring(idx)
    }
}