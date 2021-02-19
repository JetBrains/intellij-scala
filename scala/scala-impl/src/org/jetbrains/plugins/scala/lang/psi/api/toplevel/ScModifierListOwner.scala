package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import org.jetbrains.plugins.scala.lang.psi.api._


import com.intellij.psi._
import org.jetbrains.plugins.scala.caches.ModTracker
import org.jetbrains.plugins.scala.extensions.{StubBasedExt, ToNullSafe}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.adapters.PsiModifierListOwnerAdapter
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotationsHolder, _}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.macroAnnotations.Cached

/**
* @author ilyas
*/

trait ScModifierListOwnerBase extends ScalaPsiElementBase with ScAnnotationsHolderBase with PsiModifierListOwnerAdapter { this: ScModifierListOwner =>

  @Cached(ModTracker.anyScalaPsiChange, this)
  override def getModifierList: ScModifierList = {
    val child = this.stubOrPsiChild(ScalaElementType.MODIFIERS)
    child.getOrElse(ScalaPsiElementFactory.createEmptyModifierList(this))
  }

  override def hasAnnotation(fqn: String): Boolean = super[ScAnnotationsHolderBase].hasAnnotation(fqn)

  override def hasModifierProperty(name: String): Boolean = hasModifierPropertyInner(name)

  // TODO This method is, in fact, ...Java, as it interprets the absence of 'private' / 'protected' as the presence of 'public'
  final def hasModifierPropertyScala(name: String): Boolean = {
    name != PsiModifier.PUBLIC && hasModifierPropertyInner(name)
  }

  private def hasModifierPropertyInner(name: String): Boolean =
    getModifierList.nullSafe.exists {
      _.hasModifierProperty(name)
    }
}