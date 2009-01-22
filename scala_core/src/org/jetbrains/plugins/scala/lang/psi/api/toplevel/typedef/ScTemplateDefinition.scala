package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, ResolveState, PsiClass, PsiNamedElement}
import impl.ScalaPsiElementFactory
import impl.toplevel.typedef.TypeDefinitionMembers
import statements.{ScFunction, ScValue, ScTypeAlias, ScVariable}
import templates.ScExtendsBlock
import types.{ScType, PhysicalSignature, ScSubstitutor}

/**
 * @author ven
 */
trait ScTemplateDefinition extends ScNamedElement with PsiClass {
  def extendsBlock() : ScExtendsBlock = findChildByClass(classOf[ScExtendsBlock])

  def members(): Seq[ScMember] = extendsBlock.members
  def functions(): Seq[ScFunction] = extendsBlock.functions
  def aliases(): Seq[ScTypeAlias] = extendsBlock.aliases

  def typeDefinitions(): Seq[ScTypeDefinition] = extendsBlock.typeDefinitions

  def selfTypeElement = extendsBlock.selfTypeElement

  def selfType = extendsBlock.selfType

  def superTypes(): List[ScType] = extendsBlock.superTypes
  def supers(): Seq[PsiClass] = extendsBlock.supers

  def allTypes = TypeDefinitionMembers.getTypes(this).values.map{ n => (n.info, n.substitutor) }
  def allVals = TypeDefinitionMembers.getVals(this).values.map{ n => (n.info, n.substitutor) }
  def allMethods = TypeDefinitionMembers.getMethods(this).values.map{ n => n.info }
  def allSignatures = TypeDefinitionMembers.getSignatures(this).values.map{ n => n.info }

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement) : Boolean = {
    val eb = extendsBlock
    eb.templateParents match {
        case Some(p) if (PsiTreeUtil.isAncestor(p, place, true)) => {
          eb.earlyDefinitions match {
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
          eb.earlyDefinitions match {
            case Some(ed) if PsiTreeUtil.isAncestor(ed, place, true) =>
            case _ => selfTypeElement match {
              case Some(ste) if (PsiTreeUtil.isAncestor(ste, place, true)) =>
              case _ => extendsBlock match {
                case e : ScExtendsBlock if e != null => {
                  if (PsiTreeUtil.isAncestor(e, place, true) || !PsiTreeUtil.isAncestor(this, place, true)) {
                    if (!TypeDefinitionMembers.processDeclarations(this, processor, state, lastParent, place)) return false
                  }
                }
                case _ => true
              }
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