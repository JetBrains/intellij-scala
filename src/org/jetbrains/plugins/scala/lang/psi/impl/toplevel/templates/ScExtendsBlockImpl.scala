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
import result.{TypingContext, Success}
import stubs.ScExtendsBlockStub
import api.toplevel.typedef.{ScMember, ScTypeDefinition}
import collection.Seq
import api.base.types._
import caches.CachesUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ExpectedTypes, ScNewTemplateDefinition}
import com.intellij.psi.util.PsiModificationTracker

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
      new CachesUtil.MyProvider(this, (expr: ScExtendsBlock) => superTypesInner)
      (PsiModificationTracker.MODIFICATION_COUNT))
  }

  def isScalaObject: Boolean = {
    getParentByStub match {
      case clazz: PsiClass =>
        clazz.getQualifiedName == "scala.ScalaObject"
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
      case Some(parents: ScTemplateParents) => {
        val parentSupers: Seq[ScType] = parents.superTypes
        val noInferValueType = getParentByStub.isInstanceOf[ScNewTemplateDefinition] && parentSupers.length == 1
        parentSupers foreach {t => addType(if (noInferValueType) t else t.inferValueType)}
      }
      case _ =>
    }
    if (isUnderCaseClass) {
      val prod = scalaProduct
      if (prod != null) buffer += prod
    }
    if (!isScalaObject) {
      val obj = scalaObject
      if (obj != null) buffer += obj
    }
    buffer.toList
  }

  private def scalaProduct: ScType = {
    val sp = ScalaPsiManager.instance(getProject).getCachedClass(getResolveScope, "scala.Product")
    if (sp != null) ScType.designator(sp) else null
  }

  private def scalaObject: ScType = {
    val so = ScalaPsiManager.instance(getProject).getCachedClass(getResolveScope, "scala.ScalaObject")
    if (so != null) ScType.designator(so) else null
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

  def supers = {
    val buf = new ArrayBuffer[PsiClass]
    for (t <- superTypes) {
      ScType.extractClass(t) match {
        case Some(c) => buf += c
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