package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ArrayFactory
import psi.impl.ScalaPsiElementFactory
import psi.impl.toplevel.typedef.TypeDefinitionMembers
import parser.ScalaElementTypes
import statements.{ScFunction, ScValue, ScTypeAlias, ScVariable}
import templates.ScExtendsBlock
import com.intellij.openapi.progress.ProgressManager
import types.result.{TypingContext, TypeResult}
import lang.resolve.processor.BaseProcessor
import statements.params.ScClassParameter
import java.util.ArrayList
import types._
import psi.impl.toplevel.synthetic.ScSyntheticFunction
import fake.FakePsiMethod
import lexer.ScalaTokenTypes
import com.intellij.psi._
import base.types.ScSelfTypeElement
import search.GlobalSearchScope
import caches.CachesUtil
import com.intellij.openapi.project.{DumbServiceImpl, DumbService}

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
          return stub.findChildStubByType(ScalaElementTypes.EXTENDS_BLOCK).getPsi
        }
      }
      case _ =>
    }
    assert(getLastChild.isInstanceOf[ScExtendsBlock], "Class hasn't extends block: " + this.getText)
    getLastChild.asInstanceOf[ScExtendsBlock]
  }

  def refs = {
    extendsBlock.templateParents.toSeq.flatMap(_.typeElements).map { refElement =>
      val tuple: Option[(PsiClass, ScSubstitutor)] = refElement.getType(TypingContext.empty).toOption.flatMap(
        ScType.extractClassType(_, Some(getProject)))
      (refElement, tuple)
    }
  }

  def innerExtendsListTypes = {
    val eb = extendsBlock
    if (eb != null) {
      val tp = eb.templateParents
      tp match {
        case Some(tp1) => (for (te <- tp1.typeElements;
                                t = te.getType(TypingContext.empty).getOrAny;
                                asPsi = ScType.toPsi(t, getProject, GlobalSearchScope.allScope(getProject));
                                if asPsi.isInstanceOf[PsiClassType]) yield asPsi.asInstanceOf[PsiClassType]).toArray[PsiClassType]
        case _ => PsiClassType.EMPTY_ARRAY
      }
    } else PsiClassType.EMPTY_ARRAY
  }

  def showAsInheritor: Boolean = {
    isInstanceOf[ScTypeDefinition] || extendsBlock.templateBody != None
  }

  def getType(ctx: TypingContext): TypeResult[ScType]

  def getTypeWithProjections(ctx: TypingContext, thisProjections: Boolean = false): TypeResult[ScType]

  def members: Seq[ScMember] = extendsBlock.members
  def syntheticMembers: Seq[PsiMethod] = Seq.empty
  def functions: Seq[ScFunction] = extendsBlock.functions
  def aliases: Seq[ScTypeAlias] = extendsBlock.aliases

  def typeDefinitions: Seq[ScTypeDefinition] = extendsBlock.typeDefinitions

  def selfTypeElement: Option[ScSelfTypeElement] = {
    val qual = getQualifiedName
    if (qual != null && (qual == "scala.Predef" || qual == "scala")) return None
    extendsBlock.selfTypeElement
  }

  def selfType = extendsBlock.selfType

  def superTypes: List[ScType] = extendsBlock.superTypes
  def supers: Seq[PsiClass] = extendsBlock.supers

  def allTypeAliases = TypeDefinitionMembers.getTypes(this).forAll()._1.values.flatMap(n => n.map {
    case (_, n) => (n.info, n.substitutor)
  })

  def allVals = TypeDefinitionMembers.getSignatures(this).forAll()._1.values.flatMap(n => n.filter{
    case (_, n) => !n.info.isInstanceOf[PhysicalSignature] &&
      (n.info.namedElement match {
        case Some(v) => ScalaPsiUtil.nameContext(v) match {
          case _: ScVariable => v.getName == n.info.name
          case _ => true
        }
        case None => false
      })}).map { case (_, n) => (n.info.namedElement.get, n.substitutor) }

  def allMethods: Iterable[PhysicalSignature] =
    TypeDefinitionMembers.getSignatures(this).forAll()._1.values.flatMap(_.filter {
      case (_, n) => n.info.isInstanceOf[PhysicalSignature]}).
      map { case (_, n) => n.info.asInstanceOf[PhysicalSignature] } ++
      syntheticMembers.map(new PhysicalSignature(_, ScSubstitutor.empty))

  def allSignatures = TypeDefinitionMembers.getSignatures(this).forAll()._1.values.flatMap(_.map { case (_, n) => n.info })

  def isScriptFileClass = getContainingFile match {case file: ScalaFile => file.isScriptFile() case _ => false}

  override def processDeclarations(processor: PsiScopeProcessor,
                                  oldState: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement) : Boolean = {
    if (DumbServiceImpl.getInstance(getProject).isDumb) return true
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
      else ScType.designator(this))
    val eb = extendsBlock
    eb.templateParents match {
        case Some(p) if (PsiTreeUtil.isContextAncestor(p, place, true)) => {
          eb.earlyDefinitions match {
            case Some(ed) => for (m <- ed.members) {
              ProgressManager.checkCanceled()
              m match {
                case _var: ScVariable => for (declared <- _var.declaredElements) {
                  ProgressManager.checkCanceled()
                  if (!processor.execute(declared, state)) return false
                }
                case _val: ScValue => for (declared <- _val.declaredElements) {
                  ProgressManager.checkCanceled()
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
                  if (PsiTreeUtil.isContextAncestor(e, place, true) || !PsiTreeUtil.isContextAncestor(this, place, true)) {
                    if (!TypeDefinitionMembers.processDeclarations(this, processor, state, lastParent, place)) return false
                  }
                  selfTypeElement match {
                    case Some(ste) if (!PsiTreeUtil.isContextAncestor(ste, place, true)) &&
                      PsiTreeUtil.isContextAncestor(e.templateBody.getOrElse(null), place, true) => ste.typeElement match {
                      case Some(t) => (processor, place) match {
                        case (b : BaseProcessor, s: ScalaPsiElement) => {
                          if (!b.processType(t.getType(TypingContext.empty).getOrAny, s, state)) return false
                        }
                        case _ =>
                      }
                      case None =>
                    }
                    case _ =>
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
        val before = anchor match {
          case Some(anchor) => anchor.getNode
          case None => {
            val last = body.getNode.getLastChildNode
            if (ScalaPsiUtil.isLineTerminator(last.getTreePrev.getPsi)) {
              last.getTreePrev
            } else {
              last
            }
          }
        }
        if (ScalaPsiUtil.isLineTerminator(before.getPsi))
          body.getNode.addChild(ScalaPsiElementFactory.createNewLineNode(member.getManager), before)
        body.getNode.addChild(member.getNode, before)
        if (!ScalaPsiUtil.isLineTerminator(before.getPsi))
          body.getNode.addChild(ScalaPsiElementFactory.createNewLineNode(member.getManager), before)
        else
          body.getNode.replaceChild(before, ScalaPsiElementFactory.createNewLineNode(member.getManager))
      }
      case None => {
        extendsBlock.getNode.addChild(ScalaPsiElementFactory.createBodyFromMember(member, member.getManager).getNode)
        return members(0)
      }
    }
    member
  }

  def deleteMember(member: ScMember) {
    member.getParent.getNode.removeChild(member.getNode)
  }

  def functionsByName(name: String): Seq[PsiMethod] = {
    (for ((p: PhysicalSignature, _) <- TypeDefinitionMembers.getSignatures(this).forName(name)._1) yield p.method).
            toSeq ++ syntheticMembers.filter(_.getName == name)
  }

  //Java sources uses this method. Really it's not very useful. Parameter checkBases ignored
  override def findMethodsAndTheirSubstitutorsByName
      (name: String, checkBases: Boolean): java.util.List[com.intellij.openapi.util.Pair[PsiMethod, PsiSubstitutor]] = {
    import com.intellij.openapi.util.Pair
    val res = new ArrayList[Pair[PsiMethod, PsiSubstitutor]]()
    for {(p: PhysicalSignature, _) <- TypeDefinitionMembers.getSignatures(this).forName(name)._1
         substitutor = p.substitutor
         method = p.method
         if method.getContainingClass == this
    } {
      res.add(new Pair[PsiMethod, PsiSubstitutor](method, ScalaPsiUtil.getPsiSubstitutor(substitutor, getProject, getResolveScope)))
    }
    res
  }

  def isInheritor(baseClass: PsiClass, deep: Boolean): Boolean = {
    val visited: _root_.java.util.Set[PsiClass] = new _root_.java.util.HashSet[PsiClass]
    val baseQualifiedName = baseClass.getQualifiedName
    val baseName = baseClass.getName
    def isInheritorInner(base: PsiClass, drv: PsiClass, deep: Boolean): Boolean = {
      ProgressManager.checkCanceled()
      if (!visited.contains(drv)) {
        visited.add(drv)

        drv match {
          case drg: ScTemplateDefinition =>
            val supers = drg.superTypes
            val supersIterator = supers.iterator
            while (supersIterator.hasNext) {
              val t = supersIterator.next()
              ScType.extractClass(t) match {
                case Some(c) => {
                  val value = baseClass match {
                    case _: ScTrait if c.isInstanceOf[ScTrait] => true
                    case _: ScClass if c.isInstanceOf[ScClass] => true
                    case _ if !c.isInstanceOf[ScTemplateDefinition] => true
                    case _ => false
                  }
                  if (value && c.getName == baseName && c.getQualifiedName == baseQualifiedName && value) return true
                  if (deep && isInheritorInner(base, c, deep)) return true
                }
                case _ =>
              }
            }
          case _ =>
            val supers = drv.getSuperTypes
            val supersIterator = supers.iterator
            while (supersIterator.hasNext) {
              val psiT = supersIterator.next()
              val c = psiT.resolveGenerics.getElement
              if (c != null) {
                if (c.getName == baseName && c.getQualifiedName == baseQualifiedName) return true
                if (deep && isInheritorInner(base, c, deep)) return true
              }
            }
        }
      }
      false
    }
    if (baseClass == null || DumbService.getInstance(baseClass.getProject).isDumb) return false //to prevent failing during indexes

    // This doesn't appear in the superTypes at the moment, so special case required.
    if (baseQualifiedName == "java.lang.Object" || baseQualifiedName == "scala.ScalaObject") return true

    isInheritorInner(baseClass, this, deep)
  }
}

object ScTemplateDefinition {
  object ExtendsBlock {
    def unapply(definition: ScTemplateDefinition): Some[ScExtendsBlock] = Some(definition.extendsBlock)
  }
}