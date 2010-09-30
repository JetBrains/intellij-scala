package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ArrayFactory
import impl.ScalaPsiElementFactory
import impl.toplevel.typedef.TypeDefinitionMembers
import parser.ScalaElementTypes
import statements.{ScFunction, ScValue, ScTypeAlias, ScVariable}
import templates.ScExtendsBlock
import com.intellij.openapi.progress.ProgressManager
import types.result.{TypingContext, TypeResult}
import resolve.processor.BaseProcessor
import statements.params.ScClassParameter
import java.util.ArrayList
import com.intellij.psi.{PsiSubstitutor, PsiElement, ResolveState, PsiClass}
import types._
import impl.toplevel.synthetic.ScSyntheticFunction
import fake.FakePsiMethod

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
    getLastChild.asInstanceOf[ScExtendsBlock]
  }

  def showAsInheritor: Boolean = {
    isInstanceOf[ScTypeDefinition] || extendsBlock.templateBody != None
  }

  def getType(ctx: TypingContext): TypeResult[ScType]

  def getTypeWithProjections(ctx: TypingContext, thisProjections: Boolean = false): TypeResult[ScType]

  def members(): Seq[ScMember] = extendsBlock.members
  def syntheticMembers(): Seq[FakePsiMethod] = Seq.empty
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

  def isScriptFileClass = getContainingFile match {case file: ScalaFile => file.isScriptFile() case _ => false}

  override def processDeclarations(processor: PsiScopeProcessor,
                                  oldState: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement) : Boolean = {
    var state = oldState
    //exception cases
    this match {
      case s: ScTypeParametersOwner => s.typeParametersClause match {
        case Some(tpc) if PsiTreeUtil.isContextAncestor(tpc, place, false) => return true
        case _ =>
      }
      case _ =>
    }

    // Process selftype reference
    selfTypeElement match {
      case Some(se) if se.getName != "_" => if (!processor.execute(se, state)) return false
      case _ =>
    }
    state = state.put(BaseProcessor.FROM_TYPE_KEY,
      if (ScalaPsiUtil.isPlaceTdAncestor(this, place)) ScThisType(this)
      else ScDesignatorType(this))
    val eb = extendsBlock
    eb.templateParents match {
        case Some(p) if (PsiTreeUtil.isContextAncestor(p, place, true)) => {
          eb.earlyDefinitions match {
            case Some(ed) => for (m <- ed.members) {
              ProgressManager.checkCanceled
              m match {
                case _var: ScVariable => for (declared <- _var.declaredElements) {
                  ProgressManager.checkCanceled
                  if (!processor.execute(declared, state)) return false
                }
                case _val: ScValue => for (declared <- _val.declaredElements) {
                  ProgressManager.checkCanceled
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
            case _ =>
              extendsBlock match {
                case e: ScExtendsBlock if e != null => {
                  selfTypeElement match {
                    case Some(ste) if (!PsiTreeUtil.isContextAncestor(ste, place, true)) &&
                            PsiTreeUtil.isContextAncestor(e.templateBody.getOrElse(null), place, true) => ste.typeElement match {
                      case Some(t) => (processor, place) match {   //todo rewrite for all PsiElements and processors
                        case (b : BaseProcessor, s: ScalaPsiElement) => {
                          if (!b.processType(t.getType(TypingContext.empty).getOrElse(Any), s, state)) return false
                        }
                        case _ =>
                      }
                      case None =>
                    }
                    case _ =>
                  }
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

  def functionsByName(name: String) =
    for ((_, n) <- TypeDefinitionMembers.getMethods(this) if n.info.method.getName == name) yield n.info.method

  //Java sources uses this method. Really it's not very useful. Parameter checkBases ignored
  override def findMethodsAndTheirSubstitutorsByName
      (name: String, checkBases: Boolean): java.util.List[com.intellij.openapi.util.Pair[PsiMethod, PsiSubstitutor]] = {
    import com.intellij.openapi.util.Pair
    val functions = functionsByName(name).filter(_.getContainingClass == this)
    val res = new ArrayList[Pair[PsiMethod, PsiSubstitutor]]()
    for {(_, n) <- TypeDefinitionMembers.getMethods(this)
         substitutor = n.info.substitutor
         method = n.info.method
         if method.getName == name &&
                 method.getContainingClass == this
    } {
      res.add(new Pair[PsiMethod, PsiSubstitutor](method, ScalaPsiUtil.getPsiSubstitutor(substitutor, getProject, getResolveScope)))
    }
    res
  }
}
