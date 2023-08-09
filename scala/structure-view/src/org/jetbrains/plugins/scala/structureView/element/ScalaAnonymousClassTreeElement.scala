package org.jetbrains.plugins.scala.structureView.element

import com.intellij.navigation.LocationPresentation
import com.intellij.psi.PsiElement
import com.intellij.ui.{IconManager, PlatformIcons}
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt

import javax.swing.Icon

/**
 * Created by analogy with [[com.intellij.ide.structureView.impl.java.JavaAnonymousClassTreeElement]]
 */
final class ScalaAnonymousClassTreeElement(definition: ScNewTemplateDefinition)
  extends AbstractTreeElementDelegatingChildrenToPsi(definition)
    with LocationPresentation {

  override def getIcon(open: Boolean): Icon = IconManager.getInstance.getPlatformIcon(PlatformIcons.AnonymousClass)

  private var myName: String = _
  private var myBaseName: String = _

  override def getPresentableText: String = {
    if (myName == null) {
      myName = ScalaAnonymousClassNameHelper.getPresentationName(definition).orNull
    }

    if (myName != null)
      myName
    else
      "Anonymous"
  }

  override def getLocationString: String = {
    if (myBaseName == null) {
      val constructorInvocation = definition.firstConstructorInvocation
      val clazz = constructorInvocation.flatMap(_.typeElement.`type`().toOption).flatMap(_.extractClass)
      myBaseName = clazz.map(_.name).getOrElse("")
    }
    myBaseName
  }

  override def getLocationPrefix: String = LocationPresentation.DEFAULT_LOCATION_PREFIX

  override def getLocationSuffix: String = LocationPresentation.DEFAULT_LOCATION_SUFFIX

  override protected def children: Seq[PsiElement] = TypeDefinition.childrenOf(definition)

  override def isAlwaysLeaf: Boolean = false

  override def isAlwaysShowsPlus: Boolean = true
}
