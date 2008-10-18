package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates

import api.statements.{ScValue, ScVariable}
import api.toplevel.typedef.ScObject
import api.expr.ScNewTemplateDefinition
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

class ScExtendsBlockImpl(node: ASTNode) extends ScalaStubBasedElementImpl[ScExtendsBlock](node) with ScExtendsBlock with PsiClassFake {

  def this(stub : ScExtendsBlockStub) = {
    this(DummyASTNode)
    setStub(stub)
    setNode(null)
  }

  override def toString: String = "ExtendsBlock"

  def templateBody: Option[ScTemplateBody] = findChild(classOf[ScTemplateBody])

  def empty = getNode.getFirstChildNode == null

  def superTypes(): Seq[ScType] = {
    val buffer = new ArrayBuffer[ScType]
    templateParents match {
      case None => getParent match {
        case obj : ScObject => buffer += AnyRef
        case _ => {
          val so = scalaObject()
          if (so != null) buffer += so
        }
      }
      case Some(parents) => {
        parents match {
          case classParents: ScClassParents =>
            classParents.constructor match {
              case None => ()
              case Some(c) => buffer += c.typeElement.getType
            }
          case _ =>
        }
        buffer ++= (parents.typeElements map {
          typeElement => typeElement.getType
        }).toArray
      }
    }
    buffer.toArray
  }

  private def scalaObject() = {
    val so = JavaPsiFacade.getInstance(getProject).findClass("scala.ScalaObject")
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

  def supers() ={
    val buf = new ArrayBuffer[PsiClass]
    for (t <- superTypes) {
      ScType.extractClassType(t) match {
        case Some((c, _)) => buf += c
        case None =>
      }
    }

    buf.toArray
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

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement) : Boolean = templateParents match {
    case Some(p) if (PsiTreeUtil.isAncestor(p, place, true)) => {
      earlyDefinitions match {
        case Some(ed) => for (m <- ed.members) {
          m match {
            case _var: ScVariable => for (declared <- _var.declaredElements) {
              if (!processor.execute(declared, state)) return false
            }
            case _val: ScValue => for (declared <- _val.declaredElements) {
              if (!processor.execute(declared, state)) return false
            }
          }
        }
        case None =>
      }
      true
    }
    case _ =>
      earlyDefinitions match {
        case Some(ed) if PsiTreeUtil.isAncestor(ed, place, true) =>
        case _ => if (!TypeDefinitionMembers.processDeclarations(this, processor, state, lastParent, place)) return false
      }

      true
  }

  def allTypes = TypeDefinitionMembers.getTypes(this).values.map{ n => (n.info, n.substitutor) }
  def allVals = TypeDefinitionMembers.getVals(this).values.map{ n => (n.info, n.substitutor) }
  def allMethods = TypeDefinitionMembers.getMethods(this).values.map{ n => n.info }

  def nameId() = null

  def aliases() = templateBody match {
    case None => Seq.empty
    case Some(body) => body.aliases
  }

  def functions() = templateBody match {
    case None => Seq.empty
    case Some(body) => body.functions
  }

  override def name(): String = "<anonymous>"

  override def setName(name: String): PsiElement = throw new IncorrectOperationException("Should not set name for anonymous")
}