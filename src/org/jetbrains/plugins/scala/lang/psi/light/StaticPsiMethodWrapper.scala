package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.psi.{PsiModifierList, PsiClass, PsiMethod}
import com.intellij.openapi.util.Key
import collection.immutable.HashMap
import com.intellij.psi.impl.light.{LightModifierList, LightMethod}
import org.jetbrains.plugins.scala.ScalaFileType

/**
 * @author Alefas
 * @since 27.02.12
 */
class StaticPsiMethodWrapper private (val method: PsiMethod, containingClass: PsiClass)
  extends LightMethodAdapter(method.getManager, method, containingClass) with LightScalaMethod {
  setNavigationElement(method)

  override def hasModifierProperty(name: String): Boolean = {
    name match {
      case "static" => true
      case _ => super.hasModifierProperty(name)
    }
  }

  override def getModifierList: PsiModifierList = new LightModifierList(getManager, ScalaFileType.SCALA_LANGUAGE) {
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
