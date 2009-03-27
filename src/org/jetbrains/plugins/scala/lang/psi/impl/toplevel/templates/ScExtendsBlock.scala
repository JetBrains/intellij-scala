package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates


import _root_.scala.collection.mutable.ListBuffer
import api.base.types.{ScSimpleTypeElement, ScParameterizedTypeElement, ScSelfTypeElement, ScTypeElement}
import api.statements.{ScValue, ScVariable}
import api.expr.ScNewTemplateDefinition
import api.toplevel.typedef.{ScTypeDefinition, ScObject}
import com.intellij.lang.ASTNode
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{JavaPsiFacade, PsiElement, ResolveState, PsiClass}
import com.intellij.util.IncorrectOperationException
import psi.ScalaPsiElementImpl
import api.toplevel.templates._
import psi.types._
import _root_.scala.collection.mutable.ArrayBuffer
import stubs.elements.wrappers.DummyASTNode
import stubs.ScExtendsBlockStub
import typedef.TypeDefinitionMembers

/**
 * @author AlexanderPodkhalyuzin
 * Date: 20.02.2008
 */

class ScExtendsBlockImpl extends ScalaStubBasedElementImpl[ScExtendsBlock] with ScExtendsBlock {
  def this(node: ASTNode) = {this (); setNode(node)}

  def this(stub: ScExtendsBlockStub) = {this (); setStub(stub); setNode(null)}

  override def toString: String = "ExtendsBlock"

  def templateBody: Option[ScTemplateBody] = findChild(classOf[ScTemplateBody])

  def empty = getNode.getFirstChildNode == null

  def selfType = selfTypeElement match {
    case Some(ste) => ste.typeElement match {
      case Some(te) => Some(te.getType)
      case None => None
    }
    case None => None
  }

  def superTypes(): List[ScType] = {
    val buffer = new ListBuffer[ScType]
    def addType(t: ScType): Unit = t match {
      case ScCompoundType(comps, _, _) => comps.foreach{addType _}
      case _ => buffer += t
    }
    templateParents match {
      case None => getParent match {
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
        parents.typeElements foreach {typeElement => addType(typeElement.getType)}
      }
    }
    selfType match {
      case Some(st) => addType(st)
      case None =>
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

    buf.toArray
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
    val bodyMembers = templateBody match {
      case None => Seq.empty
      case Some(body) => body.members
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

  def selfTypeElement() = templateBody match {
    case None => None
    case Some(body) => body.selfTypeElement
  }
}