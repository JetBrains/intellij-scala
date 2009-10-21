package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, ResolveState, PsiClass}
import com.intellij.util.ArrayFactory
import resolve.BaseProcessor
import impl.ScalaPsiElementFactory
import impl.toplevel.typedef.TypeDefinitionMembers
import parser.ScalaElementTypes
import statements.{ScFunction, ScValue, ScTypeAlias, ScVariable}
import templates.ScExtendsBlock
import types.{ScType, ScSubstitutor}
import org.jetbrains.plugins.scala.lang.psi.types.Any
import com.intellij.openapi.progress.ProgressManager

/**
 * @author ven
 */
trait ScTemplateDefinition extends ScNamedElement with PsiClass {
  import com.intellij.psi.PsiMethod
  def extendsBlock: ScExtendsBlock = {
    this match {
      case st: ScalaStubBasedElementImpl[_] => {
        val stub = st.getStub
        if (stub != null) {
          val array = stub.getChildrenByType(ScalaElementTypes.EXTENDS_BLOCK, new ArrayFactory[ScExtendsBlock] {
            def create(count: Int): Array[ScExtendsBlock] = new Array[ScExtendsBlock](count)
          })
          if (array.length == 0) {
            return null
          } else {
            return array.apply(0)
          }
        }
      }
      case _ =>
    }
    findChildByClassScala(classOf[ScExtendsBlock])
  }

  def getType : ScType

  def members(): Seq[ScMember] = extendsBlock.members
  def functions(): Seq[ScFunction] = extendsBlock.functions
  def aliases(): Seq[ScTypeAlias] = extendsBlock.aliases

  def typeDefinitions(): Seq[ScTypeDefinition] = extendsBlock.typeDefinitions

  def selfTypeElement = extendsBlock.selfTypeElement

  def selfType = extendsBlock.selfType

  def superTypes(): List[ScType] = extendsBlock.superTypes
  def supers(): Seq[PsiClass] = extendsBlock.supers

  def allTypeAliases = TypeDefinitionMembers.getTypes(this).values.map{ n => (n.info, n.substitutor) }
  def allVals = TypeDefinitionMembers.getVals(this).values.map{ n => (n.info, n.substitutor) }
  def allMethods = TypeDefinitionMembers.getMethods(this).values.map{ n => n.info }
  def allSignatures = TypeDefinitionMembers.getSignatures(this).values.map{ n => n.info }

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement) : Boolean = {
    // Process selftype reference
    selfTypeElement match {
      case Some(se) => if (!processor.execute(se, state)) return false
      case None =>
    }

    val eb = extendsBlock
    eb.templateParents match {
        case Some(p) if (PsiTreeUtil.isContextAncestor(p, place, true)) => {
          eb.earlyDefinitions match {
            case Some(ed) => for (m <- ed.members) {
              ProgressManager.getInstance.checkCanceled
              m match {
                case _var: ScVariable => for (declared <- _var.declaredElements) {
                  ProgressManager.getInstance.checkCanceled
                  if (!processor.execute(declared, state)) return false
                }
                case _val: ScValue => for (declared <- _val.declaredElements) {
                  ProgressManager.getInstance.checkCanceled
                  if (!processor.execute(declared, state)) return false
                }
              }
            }
            case None =>
          }
          true
        }
        case _ =>
          eb.earlyDefinitions match {
            case Some(ed) if PsiTreeUtil.isContextAncestor(ed, place, true) =>
            case _ => selfTypeElement match {
              case Some(ste) if (!PsiTreeUtil.isContextAncestor(ste, place, true)) => ste.typeElement match {
                case Some(t) => (processor, place) match {   //todo rewrite for all PsiElements and processors
                  case (b : BaseProcessor, s: ScalaPsiElement) => {
                    if (!b.processType(t.cachedType.unwrap(Any), s, state)) return false
                  }
                  case _ =>
                }
                case None =>
              }
              case _ =>
            }
            extendsBlock match {
              case e: ScExtendsBlock if e != null => {
                if (PsiTreeUtil.isContextAncestor(e, place, true) || !PsiTreeUtil.isContextAncestor(this, place, true)) {
                  if (!TypeDefinitionMembers.processDeclarations(this, processor, state, lastParent, place)) return false
                }
              }
              case _ => true
            }
          }
          true
      }
  }

  def addMember(member: ScMember, anchor: Option[PsiElement]): ScMember = {
    extendsBlock.templateBody match {
      case Some(body) => {
        val before = anchor match {case Some(anchor) => anchor.getNode; case None => body.getNode.getLastChildNode}
        if (ScalaPsiUtil.isLineTerminator(before.getPsi))
          body.getNode.addChild(ScalaPsiElementFactory.createNewLineNode(member.getManager), before)
        body.getNode.addChild(member.getNode, before)
        if (!ScalaPsiUtil.isLineTerminator(before.getPsi))
          body.getNode.addChild(ScalaPsiElementFactory.createNewLineNode(member.getManager), before)
      }
      case None => {
        extendsBlock.getNode.addChild(ScalaPsiElementFactory.createBodyFromMember(member, member.getManager).getNode)
        return members.apply(0)
      }
    }
    return member
  }

  def deleteMember(member: ScMember): Unit = {
    member.getParent.getNode.removeChild(member.getNode)
  }
}