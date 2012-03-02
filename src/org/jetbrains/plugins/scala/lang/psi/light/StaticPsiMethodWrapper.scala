package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.psi.impl.light.LightMethod
import com.intellij.psi.{PsiModifierList, PsiClass, PsiMethod}
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.LightModifierList
import java.util.{HashSet => JHashSet}
import com.intellij.openapi.util.Key
import collection.immutable.HashMap

/**
 * @author Alefas
 * @since 27.02.12
 */
class StaticPsiMethodWrapper private (val method: PsiMethod, containingClass: PsiClass)
  extends LightMethod(method.getManager, method, containingClass) {
  setNavigationElement(method)

  override def hasModifierProperty(name: String): Boolean = {
    name match {
      case "static" => true
      case _ => super.hasModifierProperty(name)
    }
  }

  override def getModifierList: PsiModifierList = new LightModifierList(getManager, new JHashSet[String]) {
    override def hasModifierProperty(name: String): Boolean = {
      name match {
        case "static" => true
        case _ => super.hasModifierProperty(name)
      }
    }

    override def hasExplicitModifier(name: String): Boolean = {
      name match {
        case "static" => true
        case _ => super.hasModifierProperty(name)
      }
    }
  }
}

object StaticPsiMethodWrapper {
  private val KEY: Key[HashMap[PsiClass, (StaticPsiMethodWrapper, Long)]] = Key.create("static.psi.method.wrapper.key")

  def getWrapper(method: PsiMethod, containingClass: PsiClass): StaticPsiMethodWrapper = {
    var data = method.getUserData(KEY)
    if (data == null) {
      data = new HashMap()
      method.putUserData(KEY, data)
    }
    val count = method.getManager.getModificationTracker.getOutOfCodeBlockModificationCount
    var res = data.get(containingClass).getOrElse(null)
    if (res != null && res._2 == count) return res._1
    res = (new StaticPsiMethodWrapper(method, containingClass), count)
    data += ((containingClass, res))
    method.putUserData(KEY, data)
    res._1
  }
}
