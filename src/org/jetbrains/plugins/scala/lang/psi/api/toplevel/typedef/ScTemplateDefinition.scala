package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import java.util

import com.intellij.execution.junit.JUnitUtil
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
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement.ElementScope
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.isLineTerminator
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.light.ScFunctionWrapper
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScThisType
import org.jetbrains.plugins.scala.lang.psi.types.result.{TypeResult, Typeable, TypingContext}
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor
import org.jetbrains.plugins.scala.macroAnnotations.{CachedInsidePsiElement, ModCount}

import scala.collection.JavaConverters._

/**
 * @author ven
 */
trait ScTemplateDefinition extends ScNamedElement with PsiClass with Typeable {
  import com.intellij.psi.PsiMethod
  def qualifiedName: String = null

  def additionalJavaNames: Array[String] = Array.empty

  def extendsBlock: ScExtendsBlock = this.stubOrPsiChild(ScalaElementTypes.EXTENDS_BLOCK).orNull

  def innerExtendsListTypes: Array[PsiClassType] = {
    val eb = extendsBlock
    if (eb != null) {
      val tp = eb.templateParents

      implicit val elementScope = ElementScope(getProject)
      tp match {
        case Some(tp1) => (for (te <- tp1.allTypeElements;
                                t = te.getType(TypingContext.empty).getOrAny;
                                asPsi = t.toPsiType()
                                if asPsi.isInstanceOf[PsiClassType]) yield asPsi.asInstanceOf[PsiClassType]).toArray[PsiClassType]
        case _ => PsiClassType.EMPTY_ARRAY
      }
    } else PsiClassType.EMPTY_ARRAY
  }

  def showAsInheritor: Boolean = extendsBlock.templateBody.isDefined

  override def findMethodBySignature(patternMethod: PsiMethod, checkBases: Boolean): PsiMethod = {
    PsiClassImplUtil.findMethodBySignature(this, patternMethod, checkBases)
  }

  override def findMethodsBySignature(patternMethod: PsiMethod, checkBases: Boolean): Array[PsiMethod] = {
    PsiClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases)
  }

  override def findMethodsByName(name: String, checkBases: Boolean): Array[PsiMethod] = {
    val toSearchWithIndices = Set("main", JUnitUtil.SUITE_METHOD_NAME) //these methods may be searched from EDT, search them without building a whole type hierarchy

    def withIndices(): Array[PsiMethod] = {
      val inThisClass = functionsByName(name)

      val files = this.allSupers.flatMap(_.containingVirtualFile).asJava
      val scope = GlobalSearchScope.filesScope(getProject, files)
      val manager = ScalaShortNamesCacheManager.getInstance(getProject)
      val candidates = manager.getMethodsByName(name, scope)
      val inBaseClasses = candidates.filter(m => this.isInheritor(m.containingClass, deep = true))

      (inThisClass ++ inBaseClasses).toArray
    }

    if (toSearchWithIndices.contains(name)) withIndices()
    else PsiClassImplUtil.findMethodsByName(this, name, checkBases)
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
        case wrapper: ScFunctionWrapper if wrapper.delegate.isInstanceOf[ScFunctionDeclaration] => 1
        case wrapper: ScFunctionWrapper if wrapper.delegate.isInstanceOf[ScFunctionDefinition] => wrapper.containingClass match {
          case myClass: ScTemplateDefinition if myClass.members.contains(wrapper.delegate) => 0
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

  def getTypeWithProjections(ctx: TypingContext, thisProjections: Boolean = false): TypeResult[ScType]

  def members: Seq[ScMember] = extendsBlock.members ++ syntheticMembers
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

  protected def syntheticTypeDefinitionsImpl: Seq[ScTypeDefinition] = Seq.empty

  @CachedInsidePsiElement(this, ModCount.getBlockModificationCount)
  def syntheticMembers: Seq[ScMember] = syntheticMembersImpl

  protected def syntheticMembersImpl: Seq[ScMember] = Seq.empty

  def selfTypeElement: Option[ScSelfTypeElement] = {
    val qual = qualifiedName
    if (qual != null && (qual == "scala.Predef" || qual == "scala")) return None
    extendsBlock.selfTypeElement
  }

  def selfType: Option[ScType] = extendsBlock.selfType

  def superTypes: List[ScType] = extendsBlock.superTypes
  def supers: Seq[PsiClass] = extendsBlock.supers

  def allTypeAliases: Seq[(PsiNamedElement, ScSubstitutor)] = TypeDefinitionMembers.getTypes(this).allFirstSeq().flatMap(n => n.map {
    case (_, x) => (x.info, x.substitutor)
  }) ++ syntheticTypeDefinitions.filter(!_.isObject).map((_, ScSubstitutor.empty))

  def allTypeAliasesIncludingSelfType: Seq[(PsiNamedElement, ScSubstitutor)] = {
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

  def allVals: Seq[(PsiNamedElement, ScSubstitutor)] = TypeDefinitionMembers.getSignatures(this).allFirstSeq().flatMap(n => n.filter{
    case (_, x) => !x.info.isInstanceOf[PhysicalSignature] &&
      (x.info.namedElement match {
        case v =>
          ScalaPsiUtil.nameContext(v) match {
            case _: ScVariable => v.name == x.info.name
            case _: ScValue => v.name == x.info.name
            case _ => true
          }
      })}).map { case (_, n) => (n.info.namedElement, n.substitutor) }

  def allValsIncludingSelfType: Seq[(PsiNamedElement, ScSubstitutor)] = {
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

  def allSignatures: Seq[Signature] = TypeDefinitionMembers.getSignatures(this).allFirstSeq().flatMap(_.map { case (_, n) => n.info })

  def allSignaturesIncludingSelfType: Seq[Signature] = {
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

  def isScriptFileClass: Boolean = getContainingFile match {
    case file: ScalaFile => file.isScriptFile
    case _ => false
  }

  def processDeclarations(processor: PsiScopeProcessor,
                          oldState: ResolveState,
                          lastParent: PsiElement,
                          place: PsiElement) : Boolean = {
    if (!processor.isInstanceOf[BaseProcessor]) {
      val lastChild = this.lastChildStub.orNull
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
                      case t: ScTypeDefinition if selfTypeElement.isDefined &&
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
    implicit val manager = member.getManager
    extendsBlock.templateBody.map {
      _.getNode
    }.map { node =>
      val beforeNode = anchor.map {
        _.getNode
      }.getOrElse {
        val last = node.getLastChildNode
        last.getTreePrev match {
          case result if isLineTerminator(result.getPsi) => result
          case _ => last
        }
      }

      val before = beforeNode.getPsi
      if (isLineTerminator(before))
        node.addChild(createNewLineNode(), beforeNode)
      node.addChild(member.getNode, beforeNode)

      val newLineNode = createNewLineNode()
      if (isLineTerminator(before)) {
        node.replaceChild(beforeNode, newLineNode)
      } else {
        node.addChild(newLineNode, beforeNode)
      }

      member
    }.getOrElse {
      val node = extendsBlock.getNode
      node.addChild(createWhitespace.getNode)
      node.addChild(createBodyFromMember(member.getText).getNode)
      members.head
    }
  }

  def deleteMember(member: ScMember) {
    member.getParent.getNode.removeChild(member.getNode)
  }

  def functionsByName(name: String): Seq[PsiMethod] = {
    (for ((p: PhysicalSignature, _) <- TypeDefinitionMembers.getSignatures(this).forName(name)._1) yield p.method).
             ++(syntheticMethodsNoOverride.filter(_.name == name))
  }

  override def isInheritor(baseClass: PsiClass, deep: Boolean): Boolean = {
    if (baseClass == null || baseClass.isEffectivelyFinal) return false

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
    if (DumbService.getInstance(baseClass.getProject).isDumb) return false //to prevent failing during indexes

    // This doesn't appear in the superTypes at the moment, so special case required.
    if (baseQualifiedName == "java.lang.Object") return true
    if (baseQualifiedName == "scala.ScalaObject" && !baseClass.isDeprecated) return true

    isInheritorInner(baseClass, this, deep)
  }

  def isMetaAnnotatationImpl: Boolean = {
    members.exists(_.getModifierList.findChildrenByType(ScalaTokenTypes.kINLINE).nonEmpty) ||
    members.exists({case ah: ScAnnotationsHolder => ah.hasAnnotation("scala.meta.internal.inline.inline")})
  }
}

object ScTemplateDefinition {
  object ExtendsBlock {
    def unapply(definition: ScTemplateDefinition): Some[ScExtendsBlock] = Some(definition.extendsBlock)
  }
}