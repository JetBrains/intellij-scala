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
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates.ScExtendsBlockImpl.addIfNotNull
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector
import org.jetbrains.plugins.scala.lang.psi.stubs.ScExtendsBlockStub
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, CachedInUserData, ModCount}
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.Seq
import scala.collection.mutable.ArrayBuffer

/**
  * @author AlexanderPodkhalyuzin
  *         Date: 20.02.2008
  */
class ScExtendsBlockImpl private(stub: ScExtendsBlockStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, EXTENDS_BLOCK, node) with ScExtendsBlock {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScExtendsBlockStub) = this(stub, null)

  override def toString: String = "ExtendsBlock"

  @Cached(ModCount.anyScalaPsiModificationCount, this)
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
      _.`type`().toOption
    }

  @CachedInUserData(this, ModCount.getBlockModificationCount)
  def superTypes: List[ScType] = {
    val buffer = ArrayBuffer.empty[ScType]

    val stdTypes = projectContext.stdTypes
    import stdTypes._

    def addType(t: ScType) {
      t match {
        case ScCompoundType(comps, _, _) => comps.foreach(addType)
        case _ => buffer += t
      }
    }

    templateParents match {
      case Some(parents: ScTemplateParents) => parents.superTypes.foreach(addType)
      case _ => syntheticTypeElements.map(_.`type`().getOrAny).foreach(addType)
    }

    if (isUnderCaseClass) {
      addIfNotNull(scalaProduct, buffer)
      addIfNotNull(scalaSerializable, buffer)
    }

    def extract(scType: ScType): Boolean = {
      scType.extractClass match {
        case Some(_: ScObject) => true
        case Some(_: ScTrait) => false
        case Some(_: ScClass) => true
        case Some(c: PsiClass) if !c.isInterface => true
        case _ => false
      }
    }

    val findResult = buffer.find {
      case AnyVal | AnyRef | Any => true
      case t => extract(t)
    }
    findResult match {
      case Some(AnyVal) => //do nothing
      case res@(Some(AnyRef) | Some(Any)) =>
        buffer -= res.get
        addIfNotNull(javaObject, buffer)
      case Some(_) => //do nothing
      case _ =>
        addIfNotNull(javaObject, buffer)
    }
    buffer.toList
  }

  private def scalaProductClass: PsiClass =
    ScalaPsiManager.instance(getProject).getCachedClass(getResolveScope, "scala.Product").orNull

  private def scalaSerializableClass: PsiClass =
    ScalaPsiManager.instance(getProject).getCachedClass(getResolveScope, "scala.Serializable").orNull

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

  private def javaObject: ScDesignatorType = {
    val so = javaObjectClass
    if (so != null) ScDesignatorType(so) else null
  }

  def isAnonymousClass: Boolean =
    getParent match {
      case _: ScNewTemplateDefinition => templateBody.isDefined
      case _ => false
    }

  @Cached(ModCount.getBlockModificationCount, this)
  def syntheticTypeElements: Seq[ScTypeElement] = {
    if (templateParents.nonEmpty) return Seq.empty //will be handled separately
    getContext match {
      case td: ScTypeDefinition => SyntheticMembersInjector.injectSupers(td)
      case _ => Seq.empty
    }
  }

  @CachedInUserData(this, ModCount.getBlockModificationCount)
  def supers: Seq[PsiClass] = {
    val typeElements = templateParents.fold(syntheticTypeElements) {
      _.allTypeElements
    }

    val buffer = ArrayBuffer[PsiClass]()
    buffer ++= ScExtendsBlockImpl.extractSupers(typeElements)

    if (isUnderCaseClass) {
      addIfNotNull(scalaProductClass, buffer)
      addIfNotNull(scalaSerializableClass, buffer)
    }

    buffer.find {
      case _: ScSyntheticClass => true
      case _: ScObject => true
      case _: ScTrait => false
      case _: ScClass => true
      case c: PsiClass if !c.isInterface => true
      case _ => false
    } match {
      case Some(s: ScSyntheticClass) if s.stdType.isAnyVal => //do nothing
      case Some(s: ScSyntheticClass) if s.stdType.isAnyRef || s.stdType.isAny =>
        buffer -= s
        addIfNotNull(javaObjectClass, buffer)
      case Some(_: PsiClass) => //do nothing
      case _ =>
        addIfNotNull(javaObjectClass, buffer)
    }
    buffer
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


object ScExtendsBlockImpl {

  private def addIfNotNull[T >: Null](t: T, buffer: ArrayBuffer[T]): Unit = {
    if (t != null)
      buffer += t
  }

  private def extractSupers(typeElements: Seq[ScTypeElement])
                           (implicit project: ProjectContext): Seq[PsiClass] =
    typeElements.flatMap { element =>
      def tail(): Option[PsiClass] =
        element.`type`().toOption
          .flatMap(_.extractClass)

      def refTail(reference: ScStableCodeReference): Option[PsiClass] =
        reference.resolveNoConstructor match {
          case Array(head) => head.element match {
            case c: PsiClass => Some(c)
            case ta: ScTypeAliasDefinition =>
              ta.aliasedType.toOption
                .flatMap(_.extractClass)
            case _ => tail()
          }
          case _ => tail()
        }

      val maybeReference = element match {
        case ScSimpleTypeElement(result) => result
        case ScParameterizedTypeElement(ScSimpleTypeElement(result), _) => result
        case _ => None
      }

      maybeReference match {
        case Some(reference) => refTail(reference)
        case _ => tail()
      }
    }
}