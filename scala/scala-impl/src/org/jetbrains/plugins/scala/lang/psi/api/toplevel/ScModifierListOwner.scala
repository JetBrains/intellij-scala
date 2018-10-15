package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions.{StubBasedExt, ToNullSafe}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.adapters.PsiModifierListOwnerAdapter
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}

/**
* @author ilyas
*/

trait ScModifierListOwner extends ScalaPsiElement with ScAnnotationsHolder with PsiModifierListOwnerAdapter {

  @Cached(ModCount.anyScalaPsiModificationCount, this)
  override def getModifierList: ScModifierList = {
    val child = this.stubOrPsiChild(ScalaElementTypes.MODIFIERS)
    child.getOrElse(ScalaPsiElementFactory.createEmptyModifierList(this))
  }

  override def hasAnnotation(fqn: String): Boolean = super[ScAnnotationsHolder].hasAnnotation(fqn)

  def hasModifierProperty(name: String): Boolean = hasModifierPropertyInner(name)

  // TODO This method is, in fact, ...Java, as it interprets the absence of 'private' / 'protected' as the presence of 'public'
  // TODO We need to implement this in the hasModifierProperty itself.
  // TODO Also, we should probably do that at the level of ScModifierList.
  final def hasModifierPropertyScala(name: String): Boolean = {
    import PsiModifier._
    name match {
      case PUBLIC =>
        !hasModifierPropertyInner(PRIVATE) &&
          !hasModifierPropertyInner(PROTECTED)
      case _ => hasModifierPropertyInner(name)
    }
  }

  private def hasModifierPropertyInner(name: String): Boolean =
    getModifierList.nullSafe.exists {
      _.hasModifierProperty(name)
    }
}