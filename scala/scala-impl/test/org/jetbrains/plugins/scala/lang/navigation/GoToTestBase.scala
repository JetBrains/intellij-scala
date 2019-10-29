package org.jetbrains.plugins.scala
package lang
package navigation

import com.intellij.psi.{PsiClass, PsiNamedElement, PsiPackage}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiNamedElementExt}

abstract class GoToTestBase extends base.ScalaLightCodeInsightFixtureTestAdapter {

  protected final def actualName(actual: Any) = actual match {
    case pack: PsiPackage              => pack.getQualifiedName
    case clazz: PsiClass               => clazz.qualifiedName
    case namedElement: PsiNamedElement => namedElement.name
    case _                             => actual.toString
  }


  protected final def isClass(any: Any) = any.isInstanceOf[ScClass]

  protected final def isTrait(any: Any) = any.isInstanceOf[ScTrait]

  protected final def isObject(any: Any) = any.isInstanceOf[ScObject]

  protected final def isPackageObject(any: Any) = any match {
    case scObject: ScObject => scObject.isPackageObject
    case _ => false
  }

  protected final def isFunction(any: Any) = any.isInstanceOf[ScFunctionDefinition]
}
