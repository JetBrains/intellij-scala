package org.jetbrains.plugins.scala.lang.psi.impl.statements

import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScModifierList}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScEnumCases}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScEnum

import javax.swing.Icon

trait ScEnumCaseImpl extends ScEnumCase {
  abstract override def getContext: PsiElement = ScalaPsiUtil.getCompanionModule(enumParent).get.extendsBlock

  override def isCase: Boolean = true

  override def annotations: Seq[ScAnnotation] = enumCases.annotations

  override def hasModifierProperty(name: String): Boolean = name == "final" || super.hasModifierProperty(name)

  override def hasModifierPropertyScala(name: String): Boolean = name == "final" || super.hasModifierPropertyScala(name)

  override def getModifierList: ScModifierList = enumCases.getModifierList

  override def enumParent: ScEnum = stubOrPsiParentOfType(classOf[ScEnum])

  override def enumCases: ScEnumCases = parentByStub.asInstanceOf[ScEnumCases]

  override def supers: Seq[PsiClass] = if (extendsBlock.templateParents.nonEmpty) super.supers else Seq(enumParent)

  override protected def baseIcon: Icon = Icons.ENUM

  override def isLocal: Boolean = false

  override def delete(): Unit = {
    val enumCasesElements = enumCases.declaredElements
    val isOnlyCaseInEnumCases = enumCasesElements.size == 1
    val isRightmostInEnumCases = enumCasesElements.lastOption.contains(this)

    def findStart(): Option[PsiElement] = this.prevSiblings.takeWhile { e =>
      (isRightmostInEnumCases && (e.isWhitespace || e.elementType == ScalaTokenTypes.tCOMMA)) ||
        (isOnlyCaseInEnumCases && e.elementType == ScalaTokenTypes.kCASE)
    }.to(Iterable).lastOption

    def findEnd(): Option[PsiElement] = this.nextSiblings.takeWhile { e =>
      !isRightmostInEnumCases && (e.isWhitespace || e.elementType == ScalaTokenTypes.tCOMMA)
    }.to(Iterable).lastOption

    enumCases.deleteChildRange(findStart().getOrElse(this), findEnd().getOrElse(this))
  }

  // Workaround for https://github.com/scala/bug/issues/3564

  protected def parentByStub: PsiElement

  protected def stubOrPsiParentOfType[E <: PsiElement](aClass: Class[E]): E
}