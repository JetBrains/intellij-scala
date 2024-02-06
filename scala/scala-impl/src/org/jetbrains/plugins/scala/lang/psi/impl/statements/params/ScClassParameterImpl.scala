package org.jetbrains.plugins.scala.lang.psi.impl.statements
package params

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt, ifReadAllowed}
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumCase
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScEnum}
import org.jetbrains.plugins.scala.lang.psi.impl.canNotBeOverridden
import org.jetbrains.plugins.scala.lang.psi.stubs.ScParameterStub

import javax.swing.Icon

final class ScClassParameterImpl private(stub: ScParameterStub, node: ASTNode)
  extends ScParameterImpl(stub, ScalaElementType.CLASS_PARAM, node) with ScClassParameter {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScParameterStub) = this(stub, null)

  override def toString: String = "ClassParameter: " + ifReadAllowed(name)("")

  override def isVal: Boolean = byStubOrPsi(_.isVal)(findChildByType(ScalaTokenTypes.kVAL) != null)

  override def isVar: Boolean = byStubOrPsi(_.isVar)(findChildByType(ScalaTokenTypes.kVAR) != null)

  /**
   * @note `implicit` modifier can belong both to all parameter clauses or to a single parameter, examples {{{
   *         class MyClass1(implicit c1: String, c2: Int) //implicit belongs to entire clause
   *         class MyClass1(c1: String, implicit val c2: String) //implicit belongs to a single parameter
   *       }}}
   *       NOTE: this is only actual for `implicit` modifier but not for `using`
   */
  override def isImplicitParameter: Boolean = super.isImplicitParameter || getModifierList.isImplicit

  override def isPrivateThis: Boolean = {
    if (!isClassMember) return true
    getModifierList.accessModifier match {
      case Some(am) =>
        am.isThis && am.isPrivate
      case _ => false
    }
  }

  override def isCaseClassVal: Boolean = containingClass match {
    case c: ScClass if c.isCase =>
      val isInPrimaryConstructorFirstParamSection = c.constructor
        .exists(_.effectiveFirstParameterSection.contains(this))

      isInPrimaryConstructorFirstParamSection
    case _ => false
  }

  override def isEnumVal: Boolean = containingClass match {
    case _: ScEnum => true
    case _ => false
  }

  override def isEnumCaseVal: Boolean = containingClass match {
    case _: ScEnumCase => true
    case _ => false
  }

  /** @see [[org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariable.isStable]] */
  override def isStable: Boolean = {
    byStubOrPsi(_.isStable) {
      val isVarParam = findChildByType(ScalaTokenTypes.kVAR) != null
      !isVarParam || {
        //SCL-19477
        if (this.isInScala3File)
          typeElement.exists(_.isSingleton)
        else
          false
      }
    }
  }

  override def getOriginalElement: PsiElement = {
    val ccontainingClass = containingClass
    if (ccontainingClass == null) return this
    val originalClass: PsiClass = ccontainingClass.getOriginalElement.asInstanceOf[PsiClass]
    if (ccontainingClass eq originalClass) return this
    if (!originalClass.is[ScClass]) return this
    val c = originalClass.asInstanceOf[ScClass]
    val iterator = c.parameters.iterator
    while (iterator.hasNext) {
      val param = iterator.next()
      if (param.name == name) return param
    }
    this
  }

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitClassParameter(this)
  }

  override protected def baseIcon: Icon =
    if (isVar) Icons.FIELD_VAR
    else if (isVal || isCaseClassVal) Icons.FIELD_VAL
    else Icons.PARAMETER


  override def isEffectivelyFinal: Boolean = canNotBeOverridden(this)
}
