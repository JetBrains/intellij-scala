package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package templates

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.JavaArrayFactoryUtil.ScTemplateParentsFactory
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.TokenSets.TEMPLATE_PARENTS
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector
import org.jetbrains.plugins.scala.lang.psi.stubs.ScExtendsBlockStub
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, AnyVal}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types.{ScCompoundType, api, _}
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, CachedInsidePsiElement, ModCount}

import scala.annotation.tailrec
import scala.collection.Seq
import scala.collection.mutable.ListBuffer

/**
  * @author AlexanderPodkhalyuzin
  *         Date: 20.02.2008
  */
class ScExtendsBlockImpl private(stub: ScExtendsBlockStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, EXTENDS_BLOCK, node) with ScExtendsBlock {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScExtendsBlockStub) = this(stub, null)

  override def toString: String = "ExtendsBlock"

  def templateBody: Option[ScTemplateBody] = {
    def childStubTemplate(stub: ScExtendsBlockStub) =
      Option(stub.findChildStubByType(TEMPLATE_BODY))
        .map(_.getPsi)

    def lastChildTemplateBody = getLastChild match {
      case tb: ScTemplateBody => Some(tb)
      case _ => None
    }

    byPsiOrStub(lastChildTemplateBody)(childStubTemplate)
  }

  def empty: Boolean = getNode.getFirstChildNode == null

  def selfType: Option[ScType] =
    selfTypeElement.flatMap {
      _.typeElement
    }.flatMap {
      _.getType().toOption
    }

  @CachedInsidePsiElement(this, ModCount.getBlockModificationCount)
  def superTypes: List[ScType] = {
    val buffer = new ListBuffer[ScType]

    def addType(t: ScType) {
      t match {
        case ScCompoundType(comps, _, _) => comps.foreach(addType)
        case _ => buffer += t
      }
    }

    templateParents match {
      case Some(parents: ScTemplateParents) => parents.superTypes.foreach(addType)
      case _ => syntheticTypeElements.map(_.getType(TypingContext.empty).getOrAny).foreach(addType)
    }

    if (isUnderCaseClass) {
      val prod = scalaProduct
      if (prod != null) buffer += prod
      val ser = scalaSerializable
      if (ser != null) buffer += ser
    }

    if (!isScalaObject) {
      val obj = scalaObject
      if (obj != null && !obj.element.asInstanceOf[PsiClass].isDeprecated) buffer += obj
    }

    def extract(scType: ScType): Boolean = {
      scType.extractClass(getProject) match {
        case Some(_: ScObject) => true
        case Some(_: ScTrait) => false
        case Some(_: ScClass) => true
        case Some(c: PsiClass) if !c.isInterface => true
        case _ => false
      }
    }

    val findResult = buffer.find {
      case AnyVal | api.AnyRef | Any => true
      case t => extract(t)
    }
    findResult match {
      case Some(AnyVal) => //do nothing
      case res@(Some(api.AnyRef) | Some(Any)) =>
        buffer -= res.get
        if (javaObject != null)
          buffer += javaObject
      case Some(_) => //do nothing
      case _ =>
        if (javaObject != null)
          buffer += javaObject
    }
    buffer.toList
  }

  def isScalaObject: Boolean = {
    getParentByStub match {
      case clazz: PsiClass =>
        clazz.qualifiedName == "scala.ScalaObject"
      case _ => false
    }
  }

  private def scalaProductClass: PsiClass =
    ScalaPsiManager.instance(getProject).getCachedClass(getResolveScope, "scala.Product").orNull

  private def scalaSerializableClass: PsiClass =
    ScalaPsiManager.instance(getProject).getCachedClass(getResolveScope, "scala.Serializable").orNull

  private def scalaObjectClass: PsiClass =
    ScalaPsiManager.instance(getProject).getCachedClass(getResolveScope, "scala.ScalaObject").orNull

  private def javaObjectClass: PsiClass =
    ScalaPsiManager.instance(getProject).getCachedClass(getResolveScope, "java.lang.Object").orNull

  private def scalaProduct: ScType = {
    val sp = scalaProductClass
    if (sp != null) ScalaType.designator(sp) else null
  }

  private def scalaSerializable: ScType = {
    val sp = scalaSerializableClass
    if (sp != null) ScalaType.designator(sp) else null
  }

  private def scalaObject: ScDesignatorType = {
    val so = scalaObjectClass
    if (so != null) ScDesignatorType(so) else null
  }

  private def javaObject: ScDesignatorType = {
    val so = javaObjectClass
    if (so != null) ScDesignatorType(so) else null
  }

  def isAnonymousClass: Boolean =
    getParent match {
      case _: ScNewTemplateDefinition => templateBody.isDefined
      case _ => false
    }

  @Cached(false, ModCount.getBlockModificationCount, this)
  def syntheticTypeElements: Seq[ScTypeElement] = {
    if (templateParents.nonEmpty) return Seq.empty //will be handled separately
    getContext match {
      case td: ScTypeDefinition => SyntheticMembersInjector.injectSupers(td)
      case _ => Seq.empty
    }
  }

  @CachedInsidePsiElement(this, ModCount.getBlockModificationCount)
  def supers: Seq[PsiClass] = {
    val buffer = new ListBuffer[PsiClass]

    def addClass(t: PsiClass) {
      buffer += t
    }

    templateParents match {
      case Some(parents: ScTemplateParents) => parents.supers foreach { t => addClass(t) }
      case _ => ScTemplateParents.extractSupers(syntheticTypeElements, getProject) foreach { t => addClass(t) }
    }
    if (isUnderCaseClass) {
      val prod = scalaProductClass
      if (prod != null) buffer += prod
      val ser = scalaSerializableClass
      if (ser != null) buffer += ser
    }
    if (!isScalaObject) {
      val obj = scalaObjectClass
      if (obj != null && !obj.isDeprecated) buffer += obj
    }
    buffer.find {
      case _: ScSyntheticClass => true
      case _: ScObject => true
      case _: ScTrait => false
      case _: ScClass => true
      case c: PsiClass if !c.isInterface => true
      case _ => false
    } match {
      case Some(s: ScSyntheticClass) if AnyVal.asClass(getProject).contains(s) => //do nothing
      case Some(s: ScSyntheticClass) if api.AnyRef.asClass(getProject).contains(s) ||
        Any.asClass(getProject).contains(s) =>
        buffer -= s
        if (javaObjectClass != null)
          buffer += javaObjectClass
      case Some(_: PsiClass) => //do nothing
      case _ =>
        if (javaObjectClass != null)
          buffer += javaObjectClass
    }
    buffer
  }

  def directSupersNames: Seq[String] = {
    @tailrec
    def process(te: ScTypeElement, acc: Vector[String]): Vector[String] = {
      te match {
        case simpleType: ScSimpleTypeElement =>
          simpleType.reference match {
            case Some(ref) => acc :+ ref.refName
            case _ => acc
          }
        case infixType: ScReferenceableInfixTypeElement =>
          acc :+ infixType.reference.refName
        case x: ScParameterizedTypeElement =>
          x.typeElement match {
            case scType: ScTypeElement => process(scType, acc)
            case _ => acc
          }
        case x: ScParenthesisedTypeElement =>
          x.typeElement match {
            case Some(typeElement) => process(typeElement, acc)
            case None => acc
          }
        case _ => acc
      }
    }

    def default(res: Seq[String]): Seq[String] =
      res ++ Seq("Object", "ScalaObject")

    def productSerializable(res: Seq[String])(underCaseClass: Boolean): Seq[String] =
      res ++ (if (underCaseClass) Seq("Product", "Serializable") else Seq.empty)

    def search = productSerializable _ compose default

    templateParents match {
      case None => Seq.empty
      case Some(parents) =>
        val parentElements: Seq[ScTypeElement] = parents.allTypeElements.toIndexedSeq
        val results: Seq[String] = parentElements flatMap (process(_, Vector[String]()))
        search(results)(isUnderCaseClass).toBuffer
    }
  }

  def members: Seq[ScMember] = {
    templateBodies.flatMap {
      _.members
    } ++ earlyDefinitions.toSeq.flatMap {
      _.members
    }
  }

  def typeDefinitions: Seq[ScTypeDefinition] =
    templateBodies.flatMap {
      _.typeDefinitions
    }

  def nameId = null

  def aliases: Seq[ScTypeAlias] =
    templateBodies.flatMap {
      _.aliases
    }

  def functions: Seq[ScFunction] =
    templateBodies.flatMap {
      _.functions
    }

  def selfTypeElement: Option[ScSelfTypeElement] =
    templateBody.flatMap {
      _.selfTypeElement
    }

  def templateParents: Option[ScTemplateParents] =
    getStubOrPsiChildren(TEMPLATE_PARENTS, ScTemplateParentsFactory).headOption

  def earlyDefinitions: Option[ScEarlyDefinitions] =
    this.stubOrPsiChild(EARLY_DEFINITIONS)

  override def addEarlyDefinitions(): ScEarlyDefinitions = {
    earlyDefinitions.getOrElse {
      val text = "class A extends {} with B {}"
      val templDef = ScalaPsiElementFactory.createTemplateDefinitionFromText(text, getParentByStub.getContext, getParentByStub)
      val extBlock = templDef.extendsBlock
      val kExtends = extBlock.children.find(_.getNode.getElementType == ScalaTokenTypes.kEXTENDS).get
      val kWith = extBlock.children.find(_.getNode.getElementType == ScalaTokenTypes.kWITH).get
      val firstElem = if (templateParents.isEmpty) kExtends else kExtends.getNextSibling
      val anchor = if (templateParents.isEmpty) getFirstChild else templateParents.get
      this.addRangeBefore(firstElem, kWith, anchor)

      earlyDefinitions.get
    }
  }

  def isUnderCaseClass: Boolean = getParentByStub match {
    case td: ScTypeDefinition if td.isCase => true
    case _ => false
  }

  private def templateBodies = templateBody.toSeq
}
