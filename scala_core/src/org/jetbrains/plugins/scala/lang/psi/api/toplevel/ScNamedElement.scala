package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import com.intellij.navigation.{ItemPresentation, NavigationItem}
import com.intellij.openapi.editor.colors.TextAttributesKey
import impl.toplevel.synthetic.JavaIdentifier
import impl.ScalaPsiElementFactory
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import icons.Icons
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import psi.ScalaPsiElement
import statements.{ScFunction, ScValue, ScTypeAlias, ScVariable}
import typedef._

trait ScNamedElement extends ScalaPsiElement with PsiNameIdentifierOwner with NavigatablePsiElement {

  def name() : String = nameId.getText

  override def getName = name

  def nameId() : PsiElement

  override def getNameIdentifier: PsiIdentifier = new JavaIdentifier(nameId)

  override def setName(name: String): PsiElement = {
    val id = nameId.getNode
    val parent = id.getTreeParent
    parent.replaceChild(id, ScalaPsiElementFactory.createIdentifier(name, getManager))
    return this
  }

  override def getPresentation: ItemPresentation = {
    val clazz = PsiTreeUtil.getParentOfType(this, classOf[ScTypeDefinition], true)
    var parent: PsiElement = this
    while (parent != null && !(parent.isInstanceOf[ScMember])) parent = parent.getParent
    return new ItemPresentation {
      def getPresentableText(): String = name
      def getTextAttributesKey(): TextAttributesKey = null
      def getLocationString(): String = if (clazz != null)"(" + clazz.getQualifiedName + ")" else ""
      override def getIcon(open: Boolean) = parent match {case mem: ScMember => mem.getIcon(0) case _ => null}
    }
  }

  override def getIcon(flags: Int) = ScalaPsiUtil.nameContext(this) match {case null => null case x => x.getIcon(flags)}
}