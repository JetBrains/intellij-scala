package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package templates


import collection.mutable.ListBuffer
import api.toplevel.ScEarlyDefinitions
import com.intellij.lang.ASTNode
import com.intellij.psi.impl.source.tree.SharedImplUtil
import com.intellij.psi.{PsiElement, PsiClass}
import parser.ScalaElementTypes
import api.toplevel.templates._
import psi.types._
import _root_.scala.collection.mutable.ArrayBuffer
import result.Success
import result.{TypingContext, Success}
import stubs.ScExtendsBlockStub
import api.toplevel.typedef._
import collection.Seq
import api.base.types._
import caches.CachesUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import com.intellij.psi.util.PsiModificationTracker
import extensions.toPsiClassExt
import synthetic.ScSyntheticClass
import psi.types.ScDesignatorType
import scala.Some
import psi.types.ScCompoundType

/**
 * @author AlexanderPodkhalyuzin
 * Date: 20.02.2008
 */
class ScExtendsBlockImpl extends ScalaStubBasedElementImpl[ScExtendsBlock] with ScExtendsBlock {
  def this(node: ASTNode) = {this (); setNode(node)}

  def this(stub: ScExtendsBlockStub) = {this (); setStub(stub); setNode(null)}

  override def toString: String = "ExtendsBlock"

  def templateBody: Option[ScTemplateBody] = {
    val stub = getStub
    if (stub != null) {
      val templ = stub.findChildStubByType(ScalaElementTypes.TEMPLATE_BODY)
      if (templ == null) None else Some(templ.getPsi)
    } else {
      getLastChild match {
        case tb: ScTemplateBody => Some(tb)
        case _ => None
      }
    }
  }

  def empty = getNode.getFirstChildNode == null

  def selfType = {
    val res = (wrap(selfTypeElement) flatMap {
      ste => wrap(ste.typeElement) flatMap {
        te => te.getType(TypingContext.empty)
      }
    }) match {
      case Success(t, _) => Some(t)
      case _ => None
    }
    res
  }

  def superTypes: List[ScType] = {
    CachesUtil.get(this, CachesUtil.EXTENDS_BLOCK_SUPER_TYPES_KEY,
      new CachesUtil.MyProvider(this, (expr: ScExtendsBlockImpl) => expr.superTypesInner)
      (PsiModificationTracker.MODIFICATION_COUNT))
  }

  def isScalaObject: Boolean = {
    getParentByStub match {
      case clazz: PsiClass =>
        clazz.qualifiedName == "scala.ScalaObject"
      case _ => false
    }
  }

  private def superTypesInner: List[ScType] = {
    val buffer = new ListBuffer[ScType]
    def addType(t: ScType) {
      t match {
        case ScCompoundType(comps, _, _, _) => comps.foreach {addType _}
        case _ => buffer += t
      }
    }
    templateParents match {
      case Some(parents: ScTemplateParents) => parents.superTypes foreach {t => addType(t)}
      case _ =>
    }
    if (isUnderCaseClass) {
      val prod = scalaProduct
      if (prod != null) buffer += prod
    }
    if (!isScalaObject) {
      val obj = scalaObject
      if (obj != null && !obj.element.asInstanceOf[PsiClass].isDeprecated) buffer += obj
    }
    if (!getContext.isInstanceOf[ScTrait]) {
      val findResult = buffer.find {
        case AnyVal | AnyRef | Any => true
        case t =>
          ScType.extractClass(t, Some(getProject)) match {
            case Some(o: ScObject) => true
            case Some(t: ScTrait) => false
            case Some(c: ScClass) => true
            case Some(c: PsiClass) if !c.isInterface => true
            case _ => false
          }
      }
      findResult match {
        case Some(AnyVal) => //do nothing
        case res@(Some(AnyRef) | Some(Any)) =>
          buffer -= res.get
          if (javaObject != null)
            buffer += javaObject
        case Some(_) => //do nothing
        case _ =>
          if (javaObject != null)
            buffer += javaObject
      }
    }
    buffer.toList
  }
  
  private def supersInner: Seq[PsiClass] = {
    val buffer = new ListBuffer[PsiClass]
    def addClass(t: PsiClass) {
      buffer += t
    }
    templateParents match {
      case Some(parents: ScTemplateParents) => parents.supers foreach {t => addClass(t)}
      case _ =>
    }
    if (isUnderCaseClass) {
      val prod = scalaProductClass
      if (prod != null) buffer += prod
    }
    if (!isScalaObject) {
      val obj = scalaObjectClass
      if (obj != null && !obj.isDeprecated) buffer += obj
    }
    if (!getContext.isInstanceOf[ScTrait]) {
      buffer.find {
        case s: ScSyntheticClass => true
        case o: ScObject => true
        case t: ScTrait => false
        case c: ScClass => true
        case c: PsiClass if !c.isInterface => true
        case _ => false
      } match {
        case Some(s: ScSyntheticClass) if Some(s) == AnyVal.asClass(getProject) => //do nothing
        case Some(s: ScSyntheticClass) if Some(s) == AnyRef.asClass(getProject) ||
          Some(s) == Any.asClass(getProject) =>
          buffer -= s
          if (javaObjectClass != null)
            buffer += javaObjectClass
        case Some(clazz: PsiClass) => //do nothing
        case _ =>
          if (javaObjectClass != null)
            buffer += javaObjectClass
      }
    }
    buffer.toSeq
  }
  
  private def scalaProductClass: PsiClass =
    ScalaPsiManager.instance(getProject).getCachedClass(getResolveScope, "scala.Product")

  private def scalaObjectClass: PsiClass =
    ScalaPsiManager.instance(getProject).getCachedClass(getResolveScope, "scala.ScalaObject")

  private def javaObjectClass: PsiClass =
    ScalaPsiManager.instance(getProject).getCachedClass(getResolveScope, "java.lang.Object")

  private def scalaProduct: ScType = {
    val sp = scalaProductClass
    if (sp != null) ScType.designator(sp) else null
  }

  private def scalaObject: ScDesignatorType = {
    val so = scalaObjectClass
    if (so != null) ScDesignatorType(so) else null
  }

  private def javaObject: ScDesignatorType = {
    val so = javaObjectClass
    if (so != null) ScDesignatorType(so) else null
  }

  def isAnonymousClass: Boolean = {
    getParent match {
      case _: ScNewTemplateDefinition =>
      case _ => return false
    }
    templateBody match {
      case Some(x) => true
      case None => false
    }
  }

  def supers: Seq[PsiClass] = {
    CachesUtil.get(this, CachesUtil.EXTENDS_BLOCK_SUPERS_KEY,
      new CachesUtil.MyProvider(this, (expr: ScExtendsBlockImpl) => expr.supersInner)
      (PsiModificationTracker.MODIFICATION_COUNT))
  }

  def directSupersNames: Seq[String] = {
    templateParents match {
      case None => Seq.empty
      case Some(parents) => {
        val res = new ArrayBuffer[String]
        val pars = parents.typeElements

        def process(te: ScTypeElement) {
          te match {
            case s: ScSimpleTypeElement =>
              s.reference match {
                case Some(ref) => res += ref.refName
                case _ =>
              }
            case x: ScInfixTypeElement =>
              res += x.ref.refName
            case x: ScParameterizedTypeElement =>
              x.typeElement match {
                case s: ScTypeElement => process(s)
                case _ =>
              }
            case x: ScParenthesisedTypeElement =>
              x.typeElement match {
                case Some(te) => process(te)
                case None =>
              }
            case _ =>
          }
        }
        pars.foreach(process)

        res += "Object"
        res += "ScalaObject"
        if (isUnderCaseClass) res += "Product"
        res.toSeq
      }
    }
  }

  def members = {
    val bodyMembers: Seq[ScMember] = templateBody match {
      case None => Seq.empty
      case Some(body: ScTemplateBody) => body.members
    }
    val earlyMembers = earlyDefinitions match {
      case None => Seq.empty
      case Some(earlyDefs) => earlyDefs.members
    }

    bodyMembers ++ earlyMembers
  }

  def typeDefinitions = templateBody match {
    case None => Seq.empty
    case Some(body) => body.typeDefinitions
  }

  def nameId = null

  def aliases = templateBody match {
    case None => Seq.empty
    case Some(body) => body.aliases
  }

  def functions = templateBody match {
    case None => Seq.empty
    case Some(body) => body.functions
  }

  def selfTypeElement = templateBody flatMap {body => body.selfTypeElement}

  def templateParents: Option[ScTemplateParents] = {
    val stub = getStub
    if (stub != null) {
      val array = stub.getChildrenByType(TokenSets.TEMPLATE_PARENTS, JavaArrayFactoryUtil.ScTemplateParentsFactory)
      array.headOption
    } else findChild(classOf[ScTemplateParents])
  }

  def earlyDefinitions: Option[ScEarlyDefinitions] = {
    val stub = getStub
    if (stub != null) {
      val array = stub.getChildrenByType(ScalaElementTypes.EARLY_DEFINITIONS,
        JavaArrayFactoryUtil.ScEarlyDefinitionsFactory)
      array.headOption
    } else findChild(classOf[ScEarlyDefinitions])
  }

  def isUnderCaseClass: Boolean = getParentByStub match {
    case td: ScTypeDefinition if td.isCase => true
    case _ => false
  }

  override def getParent: PsiElement = {
    val p = super.getParent
    p match {
      case _: ScTypeDefinition => p
      case _ => SharedImplUtil.getParent(getNode)
    }
  }
}