package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.{PsiElement, PsiTypeParameterListOwner}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement.NameId
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.PsiClassFake
import org.jetbrains.plugins.scala.lang.psi.types.api
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaLanguage}

class DummyLightTypeParam(override val name: String)(implicit pc: ProjectContext)
  extends LightElement(pc, ScalaLanguage.INSTANCE) with ScTypeParam with PsiClassFake {

  override def getIndex: Int = 0

  override def isCovariant: Boolean = false

  override def isContravariant: Boolean = false

  override def typeParameters: Seq[ScTypeParam] = Seq.empty

  override def hasTypeParameters = false

  override def getContainingFileName: String = ScalaBundle.message("no.containing.file")

  override def typeParameterText: String = name

  override val typeParamId: Long = params.freshTypeParamId(this)

  override def isHigherKindedTypeParameter: Boolean = false

  override def lowerBound: TypeResult = Right(api.Nothing)

  override def upperBound: TypeResult = Right(api.Any)

  override def toString: String = name

  override def owner: ScTypeParametersOwner = notSupported

  override def nameId: NameId.NonAnonymous = new NameId.NonAnonymous {
    override def name: Some[String] = Some(DummyLightTypeParam.this.name)
    override def explicitName: Option[String] = Some(DummyLightTypeParam.this.name)
    override def forcedName: String = DummyLightTypeParam.this.name
    override def forHighlighting: PsiElement = DummyLightTypeParam.this
    override def explicitIdentifier: Option[PsiElement] = None
    override def place: Option[PsiElement] = None
    override def isElement(element: PsiElement): Boolean = false
    override def prepareToReplace(): PsiElement = notSupported
  }

  override protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T = notSupported

  override protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] = notSupported

  override def getOwner: PsiTypeParameterListOwner = notSupported

  private def notSupported: Nothing = throw new UnsupportedOperationException("Operation on light existential type parameter")
}
