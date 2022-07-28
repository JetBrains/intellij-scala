package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.openapi.util.Key
import com.intellij.psi._
import com.intellij.psi.impl.light.{LightMethod, LightModifierList}
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager

import _root_.scala.collection.immutable.HashMap

class StaticPsiMethodWrapper private(val method: PsiMethod, containingClass: PsiClass)
  extends LightMethod(method.getManager, method, containingClass) {

  override def getNavigationElement: PsiElement = method.getNavigationElement

  override def hasModifierProperty(name: String): Boolean = {
    name match {
      case "static" => true
      case _ => super.hasModifierProperty(name)
    }
  }

  override def getModifierList: PsiModifierList = new LightModifierList(getManager, ScalaLanguage.INSTANCE) {
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

  override def isWritable: Boolean = getContainingFile.isWritable

  override def getReturnType: PsiType = method.getReturnType

  override def getReturnTypeElement: PsiTypeElement = method.getReturnTypeElement

  override def getParameterList: PsiParameterList = method.getParameterList

  override def copy(): PsiElement =
    new StaticPsiMethodWrapper(method.copy().asInstanceOf[PsiMethod], containingClass)
}

object StaticPsiMethodWrapper {

  def unapply(wrapper: StaticPsiMethodWrapper): Option[PsiMethod] = Some(wrapper.method)

  private val KEY: Key[HashMap[PsiClass, (StaticPsiMethodWrapper, Long)]] = Key.create("static.psi.method.wrapper.key")

  def getWrapper(method: PsiMethod, containingClass: PsiClass): StaticPsiMethodWrapper = {
    var data = method.getUserData(KEY)
    if (data == null) {
      data = new HashMap()
      method.putUserData(KEY, data)
    }
    val count = ScalaPsiManager.instance(method.getProject).TopLevelModificationTracker.getModificationCount
    var res = data.getOrElse(containingClass, null)
    if (res != null && res._2 == count) return res._1
    res = (new StaticPsiMethodWrapper(method, containingClass), count)
    data += ((containingClass, res))
    method.putUserData(KEY, data)
    res._1
  }
}
