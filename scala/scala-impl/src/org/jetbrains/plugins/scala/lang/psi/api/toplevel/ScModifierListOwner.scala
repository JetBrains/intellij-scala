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
  /**
   * @note despite `getModifierList` is @Nullable in `PsiModifierListOwner.getModifierList`
   *       a lot of code in Scala Plugin assumes that it's not-null.
   *       That's why it creates a synthetic empty modifier list in exceptional case, when there are no modifiers<br>
   *
   *       Ideally, we should patch all our places, which we don't check getModifierList for null and simply return null from ScModifierListOwner#getModifierList
   *       But before that, it would be nice to have SCL-8749 in order we don't break anything during the refactoring.
   */
  override def getModifierList: ScModifierList = _getModifierList()

  private val _getModifierList = cached("getModifierList", ModTracker.anyScalaPsiChange, () => {
    val child = this.stubOrPsiChild(ScalaElementType.MODIFIERS)
    child.getOrElse(ScalaPsiElementFactory.createEmptyModifierList(this))
  })

  override def hasAnnotation(fqn: String): Boolean = super[ScAnnotationsHolder].hasAnnotation(fqn)

  override def hasModifierProperty(name: String): Boolean = hasModifierPropertyInner(name)

  // TODO This method is, in fact, ...Java, as it interprets the absence of 'private' / 'protected' as the presence of 'public'
  def hasModifierPropertyScala(name: String): Boolean = {
    name != PsiModifier.PUBLIC && hasModifierPropertyInner(name)
  }

  private def hasModifierPropertyInner(name: String): Boolean =
    getModifierList.nullSafe.exists {
      _.hasModifierProperty(name)
    }
}

object ScModifierListOwner {
  object accessModifier {
    def unapply(owner: ScModifierListOwner): Option[ScAccessModifier] = owner.getModifierList.accessModifier
  }
}
