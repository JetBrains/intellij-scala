package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import java.util

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi._
import com.intellij.psi.impl.PsiClassImplUtil.MemberType
import com.intellij.psi.impl.{PsiClassImplUtil, PsiSuperMethodImplUtil}
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.scope.processor.MethodsProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.{PsiTreeUtil, PsiUtil}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.light.ScFunctionWrapper
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result.{TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor
import org.jetbrains.plugins.scala.macroAnnotations.{CachedInsidePsiElement, ModCount}

/**
 * @author ven
 */
trait ScTemplateDefinition extends ScNamedElement with PsiClass {
  import com.intellij.psi.PsiMethod
  def qualifiedName: String = null

  def additionalJavaNames: Array[String] = Array.empty

  def extendsBlock: ScExtendsBlock = {
    this match {
      case st: ScalaStubBasedElementImpl[_] =>
        val stub = st.getStub
        if (stub != null) {
          return stub.findChildStubByType(ScalaElementTypes.EXTENDS_BLOCK).getPsi
        }
      case _ =>
    }
    assert(getLastChild.isInstanceOf[ScExtendsBlock], "Class hasn't extends block: " + this.getText)
    getLastChild.asInstanceOf[ScExtendsBlock]
  }

  def refs = {
    extendsBlock.templateParents.toSeq.flatMap(_.typeElements).map { refElement =>
      val tuple: Option[(PsiClass, ScSubstitutor)] = refElement.getType(TypingContext.empty).toOption.flatMap {
        _.extractClassType(getProject)
      }
      (refElement, tuple)
    }
  }

  def innerExtendsListTypes = {
    val eb = extendsBlock
    if (eb != null) {
      val tp = eb.templateParents
      tp match {
        case Some(tp1) => (for (te <- tp1.allTypeElements;
                                t = te.getType(TypingContext.empty).getOrAny;
                                asPsi = t.toPsiType(getProject, GlobalSearchScope.allScope(getProject))
                                if asPsi.isInstanceOf[PsiClassType]) yield asPsi.asInstanceOf[PsiClassType]).toArray[PsiClassType]
        case _ => PsiClassType.EMPTY_ARRAY
      }
    } else PsiClassType.EMPTY_ARRAY
  }

  def showAsInheritor: Boolean = {
    isInstanceOf[ScTypeDefinition] || extendsBlock.templateBody.isDefined
  }

  override def findMethodBySignature(patternMethod: PsiMethod, checkBases: Boolean): PsiMethod = {
    PsiClassImplUtil.findMethodBySignature(this, patternMethod, checkBases)
  }

  override def findMethodsBySignature(patternMethod: PsiMethod, checkBases: Boolean): Array[PsiMethod] = {
    PsiClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases)
  }

  override def findMethodsByName(name: String, checkBases: Boolean): Array[PsiMethod] = {
    PsiClassImplUtil.findMethodsByName(this, name, checkBases)
  }

  override def findFieldByName(name: String, checkBases: Boolean): PsiField = {
    PsiClassImplUtil.findFieldByName(this, name, checkBases)
  }

  override def findInnerClassByName(name: String, checkBases: Boolean): PsiClass = {
    PsiClassImplUtil.findInnerByName(this, name, checkBases)
  }

  import java.util.{Collection => JCollection, List => JList}

  import com.intellij.openapi.util.{Pair => IPair}

  def getAllFields: Array[PsiField] = {
    PsiClassImplUtil.getAllFields(this)
  }

  override def findMethodsAndTheirSubstitutorsByName(name: String,
                                                     checkBases: Boolean): JList[IPair[PsiMethod, PsiSubstitutor]] = {
    //the reordering is a hack to enable 'go to test location' for junit test methods defined in traits
    import scala.collection.JavaConversions._
    PsiClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases).toList.sortBy(myPair =>
      myPair.first match {
        case wrapper: ScFunctionWrapper if wrapper.function.isInstanceOf[ScFunctionDeclaration] => 1
        case wrapper: ScFunctionWrapper if wrapper.function.isInstanceOf[ScFunctionDefinition] => wrapper.containingClass match {
          case myClass: ScTemplateDefinition if myClass.members.contains(wrapper.function) => 0
          case _ => 1
        }
        case _ => 1
      })
  }

  override def getAllMethodsAndTheirSubstitutors: JList[IPair[PsiMethod, PsiSubstitutor]] = {
    PsiClassImplUtil.getAllWithSubstitutorsByMap(this, MemberType.METHOD)
  }

  override def getVisibleSignatures: JCollection[HierarchicalMethodSignature] = {
    PsiSuperMethodImplUtil.getVisibleSignatures(this)
  }

  def getType(ctx: TypingContext): TypeResult[ScType]

  def getTypeWithProjections(ctx: TypingContext, thisProjections: Boolean = false): TypeResult[ScType]

  def members: Seq[ScMember] = extendsBlock.members
  def functions: Seq[ScFunction] = extendsBlock.functions
  def aliases: Seq[ScTypeAlias] = extendsBlock.aliases

  @CachedInsidePsiElement(this, ModCount.getBlockModificationCount)
  def syntheticMethodsWithOverride: Seq[PsiMethod] = syntheticMethodsWithOverrideImpl

  /**
   * Implement it carefully to avoid recursion.
   */
  protected def syntheticMethodsWithOverrideImpl: Seq[PsiMethod] = Seq.empty

  def allSynthetics: Seq[PsiMethod] = syntheticMethodsNoOverride ++ syntheticMethodsWithOverride

  @CachedInsidePsiElement(this, ModCount.getBlockModificationCount)
  def syntheticMethodsNoOverride: Seq[PsiMethod] = syntheticMethodsNoOverrideImpl

  protected def syntheticMethodsNoOverrideImpl: Seq[PsiMethod] = Seq.empty

  def typeDefinitions: Seq[ScTypeDefinition] = extendsBlock.typeDefinitions

  @CachedInsidePsiElement(this, ModCount.getBlockModificationCount)
  def syntheticTypeDefinitions: Seq[ScTypeDefinition] = syntheticTypeDefinitionsImpl

  def syntheticTypeDefinitionsImpl: Seq[ScTypeDefinition] = Seq.empty

  def selfTypeElement: Option[ScSelfTypeElement] = {
    val qual = qualifiedName
    if (qual != null && (qual == "scala.Predef" || qual == "scala")) return None
    extendsBlock.selfTypeElement
  }

  def selfType = extendsBlock.selfType

  def superTypes: List[ScType] = extendsBlock.superTypes
  def supers: Seq[PsiClass] = extendsBlock.supers

  def allTypeAliases = TypeDefinitionMembers.getTypes(this).allFirstSeq().flatMap(n => n.map {
    case (_, x) => (x.info, x.substitutor)
  }) ++ syntheticTypeDefinitions.filter(!_.isObject).map((_, ScSubstitutor.empty))

  def allTypeAliasesIncludingSelfType = {
    selfType match {
      case Some(selfType) =>
        val clazzType = getTypeWithProjections(TypingContext.empty).getOrAny
        selfType.glb(clazzType) match {
          case c: ScCompoundType =>
            TypeDefinitionMembers.getTypes(c, Some(clazzType), this).allFirstSeq().
              flatMap(_.map { case (_, n) => (n.info, n.substitutor) })
          case _ =>
            allTypeAliases
        }
      case _ =>
        allTypeAliases
    }
  }

  def allVals = TypeDefinitionMembers.getSignatures(this).allFirstSeq().flatMap(n => n.filter{
    case (_, x) => !x.info.isInstanceOf[PhysicalSignature] &&
      (x.info.namedElement match {
        case v =>
          ScalaPsiUtil.nameContext(v) match {
            case _: ScVariable => v.name == x.info.name
            case _: ScValue => v.name == x.info.name
            case _ => true
          }
      })}).map { case (_, n) => (n.info.namedElement, n.substitutor) }

  def allValsIncludingSelfType = {
    selfType match {
      case Some(selfType) =>
        val clazzType = getTypeWithProjections(TypingContext.empty).getOrAny
        selfType.glb(clazzType) match {
          case c: ScCompoundType =>
            TypeDefinitionMembers.getSignatures(c, Some(clazzType), this).allFirstSeq().flatMap(n => n.filter{
              case (_, x) => !x.info.isInstanceOf[PhysicalSignature] &&
                (x.info.namedElement match {
                  case v =>
                    ScalaPsiUtil.nameContext(v) match {
                      case _: ScVariable => v.name == x.info.name
                      case _: ScValue => v.name == x.info.name
                      case _ => true
                    }
                })}).map { case (_, n) => (n.info.namedElement, n.substitutor) }
          case _ =>
            allVals
        }
      case _ =>
        allVals
    }
  }

  def allMethods: Iterable[PhysicalSignature] =
    TypeDefinitionMembers.getSignatures(this).allFirstSeq().flatMap(_.filter {
      case (_, n) => n.info.isInstanceOf[PhysicalSignature]}).
      map { case (_, n) => n.info.asInstanceOf[PhysicalSignature] } ++
      syntheticMethodsNoOverride.map(new PhysicalSignature(_, ScSubstitutor.empty))

  def allMethodsIncludingSelfType: Iterable[PhysicalSignature] = {
    selfType match {
      case Some(selfType) =>
        val clazzType = getTypeWithProjections(TypingContext.empty).getOrAny
        selfType.glb(clazzType) match {
          case c: ScCompoundType =>
            TypeDefinitionMembers.getSignatures(c, Some(clazzType), this).allFirstSeq().flatMap(_.filter {
              case (_, n) => n.info.isInstanceOf[PhysicalSignature]}).
              map { case (_, n) => n.info.asInstanceOf[PhysicalSignature] } ++
              syntheticMethodsNoOverride.map(new PhysicalSignature(_, ScSubstitutor.empty))
          case _ =>
            allMethods
        }
      case _ =>
        allMethods
    }
  }

  def allSignatures = TypeDefinitionMembers.getSignatures(this).allFirstSeq().flatMap(_.map { case (_, n) => n.info })

  def allSignaturesIncludingSelfType = {
    selfType match {
      case Some(selfType) =>
        val clazzType = getTypeWithProjections(TypingContext.empty).getOrAny
        selfType.glb(clazzType) match {
          case c: ScCompoundType =>
            TypeDefinitionMembers.getSignatures(c, Some(clazzType), this).allFirstSeq().
              flatMap(_.map { case (_, n) => n.info })
          case _ =>
            allSignatures
        }
      case _ =>
       allSignatures
    }
  }

  def isScriptFileClass = getContainingFile match {case file: ScalaFile => file.isScriptFile(false) case _ => false}

  def processDeclarations(processor: PsiScopeProcessor,
                          oldState: ResolveState,
                          lastParent: PsiElement,
                          place: PsiElement) : Boolean = {
    if (!processor.isInstanceOf[BaseProcessor]) {
      val lastChild = this match {
        case s: ScalaStubBasedElementImpl[_] => s.getLastChildStub
        case _ => this.getLastChild
      }
      val languageLevel: LanguageLevel =
        processor match {
          case methodProcessor: MethodsProcessor => methodProcessor.getLanguageLevel
          case _ => PsiUtil.getLanguageLevel(place)
        }
      return PsiClassImplUtil.processDeclarationsInClass(this, processor, oldState, null, lastChild, place, languageLevel, false)
    }
    if (extendsBlock.templateBody.isDefined &&
      PsiTreeUtil.isContextAncestor(extendsBlock.templateBody.get, place, false) && lastParent != null) return true
    processDeclarationsForTemplateBody(processor, oldState, lastParent, place)
  }

  def processDeclarationsForTemplateBody(processor: PsiScopeProcessor,
                                         oldState: ResolveState,
                                         lastParent: PsiElement,
                                         place: PsiElement): Boolean = {
    if (DumbService.getInstance(getProject).isDumb) return true
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
      case Some(se) if se.name != "_" => if (!processor.execute(se, state)) return false
      case _ =>
    }
    state = state.put(BaseProcessor.FROM_TYPE_KEY,
      if (ScalaPsiUtil.isPlaceTdAncestor(this, place)) ScThisType(this)
      else ScalaType.designator(this))
    val eb = extendsBlock
    eb.templateParents match {
        case Some(p) if PsiTreeUtil.isContextAncestor(p, place, false) =>
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
        case _ =>
          eb.earlyDefinitions match {
            case Some(ed) if PsiTreeUtil.isContextAncestor(ed, place, true) =>
            case _ =>
              extendsBlock match {
                case e: ScExtendsBlock if e != null =>
                  if (PsiTreeUtil.isContextAncestor(e, place, true) || !PsiTreeUtil.isContextAncestor(this, place, true)) {
                    this match {
                      case t: ScTypeDefinition if selfTypeElement != None &&
                        !PsiTreeUtil.isContextAncestor(selfTypeElement.get, place, true) &&
                        PsiTreeUtil.isContextAncestor(e.templateBody.orNull, place, true) &&
                        processor.isInstanceOf[BaseProcessor] && !t.isInstanceOf[ScObject] =>
                          selfTypeElement match {
                            case Some(_) => processor.asInstanceOf[BaseProcessor].processType(ScThisType(t), place, state)
                            case _ =>
                              if (!TypeDefinitionMembers.processDeclarations(this, processor, state, lastParent, place)) {
                                return false
                              }
                          }
                      case _ =>
                        if (!TypeDefinitionMembers.processDeclarations(this, processor, state, lastParent, place)) return false
                    }
                  }
                case _ =>
              }
          }
          true
      }
  }

  def addMember(member: ScMember, anchor: Option[PsiElement]): ScMember = {
    extendsBlock.templateBody match {
      case Some(body) =>
        val before = anchor match {
          case Some(a) => a.getNode
          case None =>
            val last = body.getNode.getLastChildNode
            if (ScalaPsiUtil.isLineTerminator(last.getTreePrev.getPsi)) {
              last.getTreePrev
            } else {
              last
            }
        }
        if (ScalaPsiUtil.isLineTerminator(before.getPsi))
          body.getNode.addChild(ScalaPsiElementFactory.createNewLineNode(member.getManager), before)
        body.getNode.addChild(member.getNode, before)
        if (!ScalaPsiUtil.isLineTerminator(before.getPsi))
          body.getNode.addChild(ScalaPsiElementFactory.createNewLineNode(member.getManager), before)
        else
          body.getNode.replaceChild(before, ScalaPsiElementFactory.createNewLineNode(member.getManager))
      case None =>
        val eBlockNode: ASTNode = extendsBlock.getNode
        eBlockNode.addChild(ScalaPsiElementFactory.createWhitespace(member.getManager).getNode)
        eBlockNode.addChild(ScalaPsiElementFactory.createBodyFromMember(member, member.getManager).getNode)
        return members(0)
    }
    member
  }

  def deleteMember(member: ScMember) {
    member.getParent.getNode.removeChild(member.getNode)
  }

  def functionsByName(name: String): Seq[PsiMethod] = {
    (for ((p: PhysicalSignature, _) <- TypeDefinitionMembers.getSignatures(this).forName(name)._1) yield p.method).
             ++(syntheticMethodsNoOverride.filter(_.name == name))
  }

  override def isInheritor(baseClass: PsiClass, deep: Boolean): Boolean = {
    if (baseClass == null) return false

    val visited: util.Set[PsiClass] = new util.HashSet[PsiClass]
    val baseQualifiedName = baseClass.qualifiedName
    val baseName = baseClass.name
    def isInheritorInner(base: PsiClass, drv: PsiClass, deep: Boolean): Boolean = {
      ProgressManager.checkCanceled()
      if (!visited.contains(drv)) {
        visited.add(drv)

        drv match {
          case drg: ScTemplateDefinition =>
            val supersIterator = drg.supers.iterator
            while (supersIterator.hasNext) {
              val c = supersIterator.next()
              val value = baseClass match {
                case _: ScTrait if c.isInstanceOf[ScTrait] => true
                case _: ScClass if c.isInstanceOf[ScClass] => true
                case _ if !c.isInstanceOf[ScTemplateDefinition] => true
                case _ => false
              }
              if (value && c.name == baseName && c.qualifiedName == baseQualifiedName && value) return true
              if (deep && isInheritorInner(base, c, deep)) return true
            }
          case _ =>
            val supers = drv.getSuperTypes
            val supersIterator = supers.iterator
            while (supersIterator.hasNext) {
              val psiT = supersIterator.next()
              val c = psiT.resolveGenerics.getElement
              if (c != null) {
                if (c.name == baseName && c.qualifiedName == baseQualifiedName) return true
                if (deep && isInheritorInner(base, c, deep)) return true
              }
            }
        }
      }
      false
    }
    if (baseClass == null || DumbService.getInstance(baseClass.getProject).isDumb) return false //to prevent failing during indexes

    // This doesn't appear in the superTypes at the moment, so special case required.
    if (baseQualifiedName == "java.lang.Object") return true
    if (baseQualifiedName == "scala.ScalaObject" && !baseClass.isDeprecated) return true

    isInheritorInner(baseClass, this, deep)
  }
}

object ScTemplateDefinition {
  object ExtendsBlock {
    def unapply(definition: ScTemplateDefinition): Some[ScExtendsBlock] = Some(definition.extendsBlock)
  }
}