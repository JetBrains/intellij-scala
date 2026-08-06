package org.jetbrains.plugins.scala.util

import com.intellij.codeInsight.runner.JavaMainMethodProvider
import com.intellij.psi.impl.PsiClassImplUtil
import com.intellij.psi.util.PsiMethodUtil
import com.intellij.psi.{PsiClass, PsiMethod}
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cachedInUserData}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

object ScalaMainMethodUtil {

  def isMainMethod(funDef: ScFunctionDefinition): Boolean =
    isScala2MainMethod(funDef) ||
      isScala3MainMethod(funDef)

  def isScala2MainMethod(funDef: ScFunctionDefinition): Boolean = {
    ScalaNamesUtil.toJavaName(funDef.name) == "main" &&
      isInTopLevelObject(funDef) &&
      PsiMethodUtil.isMainMethod(funDef)
  }

  private def isInTopLevelObject(m: ScMember): Boolean =
    m.containingClass match {
      case o: ScObject => o.isTopLevel
      case _ => false
    }

  def isScala3MainMethod(funDef: ScFunctionDefinition): Boolean =
    if (funDef.isInScala3File) {
      val mainAnnotation = funDef.annotations.find(isMainAnnotation)
      mainAnnotation.isDefined
    }
    else false

  // NOTE: we could truly to "scala.main" (but currently resolve for the annotation is broken for Scala3 for some reason)
  //  maybe it's OK to do this for optimisation reasons
  private def isMainAnnotation(annotation: ScAnnotation): Boolean = {
    val text = annotation.annotationExpr.getText
    text == "main" || text == "scala.main"
  }

  def hasScala2MainMethod(obj: ScObject): Boolean = findScala2MainMethod(obj).isDefined

  /**
   * Return main method if it's declared in object or in one of it's base classes
   */
  def findScala2MainMethod(obj: ScObject): Option[PsiMethod] = {
    if (!obj.isTopLevel) None
    else {
      cachedInUserData("findScala2MainMethod", obj, BlockModificationTracker(obj)) {
        //NOTE: PsiClassWrapper is used in order PsiMethodUtil.isMainMethod can detect main method from base class
        // otherwise some of the conditions doesn't hold (AFAIR it can't check the presence of static modifier)
        val objWrapper = new PsiClassWrapper(obj, obj.qualifiedName, obj.name)
        val mainMethods = PsiClassImplUtil.findMethodsByName(objWrapper, "main", true)
        mainMethods.find(PsiMethodUtil.isMainMethod)
      }
    }
  }

  /**
   * Checks if a class has main method according to implementations of [[com.intellij.codeInsight.runner.JavaMainMethodProvider]]
   *
   * @note implementation is similar to [[com.intellij.psi.util.PsiMethodUtil.hasMainMethod]]
   *       but our method only checks for providers, it doesn't run extra search for "main" method declaration.
   *       That is done in our implementation `JavaMainMethodProvider` in [[org.jetbrains.plugins.scala.runner.ScalaMainMethodProvider]]
   * @note This logic is primarily required for launching JavaFX without main method (see SCL-12132).
   *       This logic is located in `org.jetbrains.plugins.javaFX.JavaFxMainMethodRunConfigurationProvider`
   *       From JavaFX docx: "The main() method is not required for JavaFX applications when the JAR file for the application is created with the JavaFX Packager tool..."
   */
  def hasMainMethodFromProvidersOnly(c: PsiClass): Boolean = {
    val mainMethodProviders = JavaMainMethodProvider.EP_NAME.getExtensions
    val foundProvider = mainMethodProviders.find { provider =>
      provider.isApplicable(c) && provider.hasMainMethod(c)
    }
    foundProvider.nonEmpty
  }
}
