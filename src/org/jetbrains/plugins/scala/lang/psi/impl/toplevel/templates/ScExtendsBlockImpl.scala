package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package templates


import _root_.scala.collection.mutable.ListBuffer
import api.base.types.{ScSimpleTypeElement, ScParameterizedTypeElement}
import api.expr.ScNewTemplateDefinition
import api.toplevel.{ScEarlyDefinitions}
import caches.CachesUtil
import com.intellij.lang.ASTNode
import com.intellij.psi.impl.source.tree.SharedImplUtil
import com.intellij.psi.util.{PsiModificationTracker}
import com.intellij.psi.{JavaPsiFacade, PsiElement, PsiClass}
import com.intellij.util.{ArrayFactory}
import parser.ScalaElementTypes
import api.toplevel.templates._
import psi.types._
import _root_.scala.collection.mutable.ArrayBuffer
import result.Success
import stubs.{ScExtendsBlockStub}
import api.toplevel.typedef.{ScMember, ScTypeDefinition, ScObject}

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
      val array = stub.getChildrenByType(ScalaElementTypes.TEMPLATE_BODY, new ArrayFactory[ScTemplateBody] {
        def create(count: Int): Array[ScTemplateBody] = new Array[ScTemplateBody](count)
      })
      if (array.length == 0) {
        return None
      } else {
        return Some(array.apply(0))
      }
    } else findChild(classOf[ScTemplateBody])
  }

  def empty = getNode.getFirstChildNode == null

  def selfType = (wrap(selfTypeElement) flatMap {
    ste => wrap(ste.typeElement) flatMap {
      te => te.cachedType
    }
  }) match {
    case Success(t, _) => Some(t)
    case _ => None
  }

  def superTypes: List[ScType] = {
    CachesUtil.get(
      this, CachesUtil.SUPER_TYPES_KEY,
      new CachesUtil.MyProvider(this, {eb: ScExtendsBlockImpl => eb.superTypesInner})
        (PsiModificationTracker.MODIFICATION_COUNT)
    )
  }

  private def superTypesInner: List[ScType] = {
    val buffer = new ListBuffer[ScType]
    def addType(t: ScType): Unit = t match {
      case ScCompoundType(comps, _, _) => comps.foreach{addType _}
      case _ => buffer += t
    }
    templateParents match {
      case None => getParentByStub match {
        case obj: ScObject => buffer += AnyRef
        case _ => {
          val so = scalaObject()
          if (so != null) buffer += so
          if (isUnderCaseClass) {
            val prod = scalaProduct()
            if (prod != null) buffer += prod
          }
        }
      }
      case Some(parents) => {
        parents.superTypes foreach {t => addType(t)}//typeElements foreach {typeElement => addType(typeElement.cachedType)}
      }
    }
    buffer.toList
  }

  private def scalaObject(): ScType = {
    val so = JavaPsiFacade.getInstance(getProject).findClass("scala.ScalaObject")
    if (so != null) new ScDesignatorType(so) else null
  }

  private def scalaProduct(): ScType = {
    val so = JavaPsiFacade.getInstance(getProject).findClass("scala.Product")
    if (so != null) new ScDesignatorType(so) else null
  }

  def isAnonymousClass: Boolean = {
    getParent match {
      case _: ScNewTemplateDefinition =>
      case _ => return false
    }
    templateBody match {
      case Some(x) => return true
      case None => return false
    }
  }

  def supers() = {
    val buf = new ArrayBuffer[PsiClass]
    for (t <- superTypes) {
      ScType.extractClassType(t) match {
        case Some((c, _)) => buf += c
        case None =>
      }
    }

    buf.toArray[PsiClass]
  }

  def directSupersNames: Seq[String] = {
    templateParents match {
      case None => Seq.empty
      case Some(parents) => {
        val res = new ArrayBuffer[String]
        val pars = parents.typeElements
        for (par <- pars) {
          par match {
            case _: ScSimpleTypeElement => res += par.getLastChild.getText()
            case x: ScParameterizedTypeElement => res += x.typeElement.getLastChild.getText()
            case _ =>
          }
        }
        res += "Object"
        res += "ScalaObject"
        if (isUnderCaseClass) res += "Product"
        res.toSeq
      }
    }
  }

  def members() = {
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

  def nameId() = null

  def aliases() = templateBody match {
    case None => Seq.empty
    case Some(body) => body.aliases
  }

  def functions() = templateBody match {
    case None => Seq.empty
    case Some(body) => body.functions
  }

  def selfTypeElement = templateBody flatMap {body => body.selfTypeElement}

  def templateParents: Option[ScTemplateParents] = {
    val stub = getStub
    if (stub != null) {

      val array = stub.getChildrenByType(TokenSets.TEMPLATE_PARENTS, new ArrayFactory[ScTemplateParents] {
        def create(count: Int): Array[ScTemplateParents] = new Array[ScTemplateParents](count)
      })
      if (array.length == 0) None
      else Some(array.apply(0))
    } else findChild(classOf[ScTemplateParents])
  }

  def earlyDefinitions: Option[ScEarlyDefinitions] = {
    val stub = getStub
    if (stub != null) {
      val array = stub.getChildrenByType(ScalaElementTypes.EARLY_DEFINITIONS, new ArrayFactory[ScEarlyDefinitions] {
        def create(count: Int): Array[ScEarlyDefinitions] = new Array[ScEarlyDefinitions](count)
      })
      if (array.length == 0) None
      else Some(array.apply(0))
    } else findChild(classOf[ScEarlyDefinitions])
  }

  def isUnderCaseClass: Boolean = getParentByStub match {
    case td: ScTypeDefinition if td.isCase => true
    case _ => false
  }

  override def getParent(): PsiElement = {
    val p = super.getParent
    p match {
      case _: ScTypeDefinition => return p
      case _ => return SharedImplUtil.getParent(getNode)
    }
  }
}