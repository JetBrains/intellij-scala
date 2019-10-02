package org.jetbrains.plugins.scala
package lang
package navigation

import com.intellij.psi.{PsiNamedElement, PsiQualifiedNamedElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}

abstract class GoToTestBase extends base.ScalaLightCodeInsightFixtureTestAdapter {

  protected final def hasExpectedName(actual: Any, expectedName: String) = actual match {
    case namedElement: PsiNamedElement =>
      val actualName = namedElement match {
        case namedElement: PsiQualifiedNamedElement => namedElement.getQualifiedName
        case _ => namedElement.getName
      }

      expectedName == actualName
    case _ => false
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
