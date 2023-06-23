package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import com.intellij.psi._
import org.jetbrains.plugins.scala.caches.{ModTracker, cached}
import org.jetbrains.plugins.scala.extensions.{StubBasedExt, ToNullSafe}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.adapters.PsiModifierListOwnerAdapter
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

trait ScModifierListOwner extends ScalaPsiElement with ScAnnotationsHolder with PsiModifierListOwnerAdapter {
  override def getModifierList: ScModifierList = _getModifierList()

  private val _getModifierList = cached("getModifierList", ModTracker.anyScalaPsiChange, () => {
    val child = this.stubOrPsiChild(ScalaElementType.MODIFIERS)
    child.getOrElse(ScalaPsiElementFactory.createEmptyModifierList(this))
  })

  override def hasAnnotation(fqn: String): Boolean = super[ScAnnotationsHolder].hasAnnotation(fqn)

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
