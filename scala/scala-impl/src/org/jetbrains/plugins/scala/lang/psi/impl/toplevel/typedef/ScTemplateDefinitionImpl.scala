package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import java.{util => ju}

import com.intellij.execution.junit.JUnitUtil
import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.{Key, Pair => JBPair}
import com.intellij.psi._
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.impl.{PsiClassImplUtil, PsiSuperMethodImplUtil}
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.scope.processor.MethodsProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.{PsiTreeUtil, PsiUtil}
import org.jetbrains.plugins.scala.caches.{ModTracker, ScalaShortNamesCacheManager}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.isLineTerminator
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypeParametersOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createBodyFromMember, createNewLineNode, createWhitespace}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.light.ScFunctionWrapper
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScThisType
import org.jetbrains.plugins.scala.lang.psi.types.{PhysicalMethodSignature, ScalaType, TermSignature, TypeSignature}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, CachedInUserData}
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.mutable
import scala.jdk.CollectionConverters._

abstract class ScTemplateDefinitionImpl[T <: ScTemplateDefinition] private[impl] (
  stub:      ScTemplateDefinitionStub[T],
  nodeType:  ScTemplateDefinitionElementType[T],
  node:      ASTNode,
  debugName: String // TODO to be moved to ScalaStubBasedElementImpl eventually
) extends ScalaStubBasedElementImpl(stub, nodeType, node)
    with PsiClassFake
    with ScTopLevelStubBasedElement[T, ScTemplateDefinitionStub[T]]
    with ScTemplateDefinition {

  import PsiTreeUtil.isContextAncestor
  import ScTemplateDefinitionImpl._
  import TypeDefinitionMembers._

  override def originalElement: Option[ScTemplateDefinition] = Option(getUserData(originalElemKey))

  override def setOriginal(actualElement: ScTypeDefinition): this.type = {
    putUserData(originalElemKey, actualElement)
    members.foreach { member =>
      member.syntheticNavigationElement = actualElement
      member.syntheticContainingClass = actualElement
    }
    this
  }

  protected def targetTokenType: ScalaTokenType

  override final def toString: String = debugName + extensions.ifReadAllowed(s": $name")("")

  override final def targetToken: LeafPsiElement = findChildByType[LeafPsiElement](targetTokenType)

  override final def allTypeSignatures: Iterator[TypeSignature] =
    getTypes(this).allSignatures

  override final def allVals: Iterator[TermSignature] =
    allSignatures.filter(isValSignature)

  override final def allMethods: Iterator[PhysicalMethodSignature] =
    allSignatures.filter(_.isInstanceOf[PhysicalMethodSignature])
      .map(_.asInstanceOf[PhysicalMethodSignature])

  override final def allSignatures: Iterator[TermSignature] =
    getSignatures(this).allSignatures

  override def getAllMethods: Array[PsiMethod] = {
    val names = mutable.HashSet.empty[String]
    val result = mutable.ArrayBuffer(getConstructors.toSeq: _*)

    allSignatures.foreach { signature =>
      this.processWrappersForSignature(
        signature,
        isStatic = false,
        isInterface = isInterface(signature.namedElement)
      )(result.+=(_), names.+=(_))
    }

    for {
      companion <- ScalaPsiUtil.getCompanionModule(this).iterator
      if addFromCompanion(companion)

      signature <- companion.allSignatures
    } this.processWrappersForSignature(
      signature,
      isStatic = true,
      isInterface = false
    )(method => if (!names.contains(method.getName)) result += method)


    result.toArray
  }

  override def allFunctionsByName(name: String): Iterator[PsiMethod] = {
    TypeDefinitionMembers.getSignatures(this).forName(name)
      .iterator
      .collect {
        case p: PhysicalMethodSignature => p.method
      }
  }

  override def allTermsByName(name: String): Seq[PsiNamedElement] = {
    TypeDefinitionMembers.getSignatures(this).forName(name)
      .iterator
      .collect {
        case s: TermSignature => s.namedElement
      }
      .toSeq
  }


  protected def isInterface(namedElement: PsiNamedElement): Boolean = namedElement match {
    case definition: ScTypedDefinition => definition.isAbstractMember
    case _ => false
  }

  protected def addFromCompanion(companion: ScTypeDefinition): Boolean = false

  override def physicalExtendsBlock: ScExtendsBlock = this.stubOrPsiChild(ScalaElementType.EXTENDS_BLOCK).orNull

  override def extendsBlock: ScExtendsBlock = {
    desugaredElement.map(_.extendsBlock)
      .getOrElse(physicalExtendsBlock)
  }

  override def desugaredElement: Option[ScTemplateDefinition] = {
    def mayBeDesugared = getContainingFile match {
      case sf: ScalaFile => !sf.isCompiled && sf.isValid && sf.isMetaEnabled
      case _             => false
    }

    if (!isDesugared && mayBeDesugared) desugaredInner
    else None
  }

  protected def desugaredInner: Option[ScTemplateDefinition] = None

  override final def getAllFields: Array[PsiField] =
    PsiClassImplUtil.getAllFields(this)

  override final def getAllInnerClasses: Array[PsiClass] =
    PsiClassImplUtil.getAllInnerClasses(this)

  override def findFieldByName(name: String, checkBases: Boolean): PsiField =
    PsiClassImplUtil.findFieldByName(this, name, checkBases)

  override final def findMethodBySignature(patternMethod: PsiMethod, checkBases: Boolean): PsiMethod =
    PsiClassImplUtil.findMethodBySignature(this, patternMethod, checkBases)

  override final def findMethodsBySignature(patternMethod: PsiMethod, checkBases: Boolean): Array[PsiMethod] =
    PsiClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases)

  override final def findMethodsByName(name: String, checkBases: Boolean): Array[PsiMethod] = name match {
    case "main" | JUnitUtil.SUITE_METHOD_NAME => // these methods may be searched from EDT, search them without building a whole type hierarchy

      val inThisClass = allFunctionsByName(name)

      val files = this.allSupers.flatMap {
        _.containingVirtualFile
      }.asJava

      val scope = GlobalSearchScope.filesScope(getProject, files)
      val inBaseClasses = ScalaShortNamesCacheManager.getInstance(getProject)
        .methodsByName(name)(scope)
        .filter { method =>
          isInheritor(method.containingClass, checkDeep = true)
        }

      (inThisClass ++ inBaseClasses).toArray

    case _ =>
      PsiClassImplUtil.findMethodsByName(this, name, checkBases)
  }

  override final def findMethodsAndTheirSubstitutorsByName(name: String, checkBases: Boolean): ju.List[JBPair[PsiMethod, PsiSubstitutor]] = {

    //the reordering is a hack to enable 'go to test location' for junit test methods defined in traits
    PsiClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases)
      .asScala
      .sortBy { myPair =>
        myPair.first match {
          //          case ScFunctionWrapper(_: ScFunctionDeclaration) => 1
          case wrapper@ScFunctionWrapper(delegate: ScFunctionDefinition) => wrapper.containingClass match {
            case myClass: ScTemplateDefinition if myClass.membersWithSynthetic.contains(delegate) => 0
            case _ => 1
          }
          case _ => 1
        }
      }.asJava
  }

  override final def getAllMethodsAndTheirSubstitutors: ju.List[JBPair[PsiMethod, PsiSubstitutor]] =
    PsiClassImplUtil.getAllWithSubstitutorsByMap(this, PsiClassImplUtil.MemberType.METHOD)

  override final def findInnerClassByName(name: String, checkBases: Boolean): PsiClass =
    PsiClassImplUtil.findInnerByName(this, name, checkBases)

  @CachedInUserData(this, ModTracker.libraryAware(this))
  override final def getVisibleSignatures: ju.Collection[HierarchicalMethodSignature] =
    PsiSuperMethodImplUtil.getVisibleSignatures(this)

  override final def isInheritor(baseClass: PsiClass, checkDeep: Boolean): Boolean =
    Path(baseClass) match {
      case Path.JavaObject => true // These doesn't appear in the superTypes at the moment, so special case required.
      case Path(_, _, kind) if kind.isFinal => false
      case _ if DumbService.getInstance(getProject).isDumb => false
      case path => (if (checkDeep) superPathsDeep else superPaths).contains(path)
    }

  @Cached(ModTracker.physicalPsiChange(getProject), this)
  private def superPaths: Set[Path] =
    supers.map(Path.apply).toSet

  @Cached(ModTracker.physicalPsiChange(getProject), this)
  private def superPathsDeep: Set[Path] = {
    val collected = mutable.Set.empty[Path]

    def dfs(clazz: PsiClass): Unit = {
      val path = Path(clazz)

      if (collected.add(path)) {
        clazz match {
          case definition: ScTemplateDefinition =>
            val supersIterator = definition.supers.iterator
            while (supersIterator.hasNext) {
              dfs(supersIterator.next())
            }
          case _ =>
            val supersIterator = clazz.getSuperTypes.iterator
            while (supersIterator.hasNext) {
              supersIterator.next().resolveGenerics.getElement match {
                case null =>
                case next => dfs(next)
              }
            }
        }
      }
    }

    dfs(this)

    collected.remove(Path(this))
    collected.toSet
  }

  override def processDeclarations(processor: PsiScopeProcessor,
                                   oldState: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean =
    processDeclarationsImpl(processor, oldState, lastParent, place)

  protected final def processDeclarationsImpl(processor: PsiScopeProcessor,
                                              oldState: ResolveState,
                                              lastParent: PsiElement,
                                              place: PsiElement): Boolean = processor match {
    case _: BaseProcessor =>
      extendsBlock.templateBody match {
        case Some(ancestor) if isContextAncestor(ancestor, place, false) && lastParent != null => true
        case _ => processDeclarationsForTemplateBody(processor, oldState, lastParent, place)
      }
    case _ =>
      val languageLevel = processor match {
        case methodProcessor: MethodsProcessor => methodProcessor.getLanguageLevel
        case _ => PsiUtil.getLanguageLevel(getProject)
      }
      PsiClassImplUtil.processDeclarationsInClass(
        this,
        processor,
        oldState,
        null,
        this.lastChildStub.orNull,
        place,
        languageLevel,
        false
      )
  }

  def processDeclarationsForTemplateBody(processor: PsiScopeProcessor,
                                         oldState: ResolveState,
                                         lastParent: PsiElement,
                                         place: PsiElement): Boolean = {
    if (DumbService.getInstance(getProject).isDumb) return true
    //exception cases
    this match {
      case s: ScTypeParametersOwner => s.typeParametersClause match {
        case Some(tpc) if isContextAncestor(tpc, place, false) => return true
        case _ =>
      }
      case _ =>
    }

    // Process selftype reference
    selfTypeElement match {
      case Some(se) if se.name != "_" => if (!processor.execute(se, oldState)) return false
      case _ =>
    }

    val fromType =
      if (ScalaPsiUtil.isPlaceTdAncestor(this, place)) ScThisType(this)
      else ScalaType.designator(this)

    val state = oldState.withFromType(fromType)

    val eb = extendsBlock
    eb.templateParents match {
      case Some(p) if isContextAncestor(p, place, false) =>
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
          case Some(ed) if isContextAncestor(ed, place, true) =>
          case _ =>
            extendsBlock match {
              case e: ScExtendsBlock if e != null =>
                val isUnderExtendsBlock =
                  if (!this.isSynthetic) isContextAncestor(e, place, true)
                  else                   isContextAncestor(this, place, true)

                if (isUnderExtendsBlock ||
                  ScalaPsiUtil.isSyntheticContextAncestor(e, place) ||
                  !isContextAncestor(this, place, true)) {
                  this match {
                    case t: ScTypeDefinition if selfTypeElement.isDefined &&
                      !isContextAncestor(selfTypeElement.get, place, true) &&
                      isContextAncestor(e.templateBody.orNull, place, true) &&
                      processor.isInstanceOf[BaseProcessor] && !t.isInstanceOf[ScObject] =>
                      selfTypeElement match {
                        case Some(_) => processor.asInstanceOf[BaseProcessor].processType(ScThisType(t), place, state)
                        case _ =>
                          if (!processClassDeclarations(this, processor, state, lastParent, place)) return false
                      }
                    case _ =>
                      if (!processClassDeclarations(this, processor, state, lastParent, place)) return false
                  }
                }
              case _ =>
            }
        }
        true
    }
  }

  override def selfTypeElement: Option[ScSelfTypeElement] = {
    val qual = qualifiedName
    if (qual != null && (qual == "scala.Predef" || qual == "scala")) return None
    extendsBlock.selfTypeElement
  }


  override def isScriptFileClass: Boolean = getContainingFile match {
    case file: ScalaFile => file.isScriptFile
    case _ => false
  }

  override def addMember(member: ScMember, anchor: Option[PsiElement]): ScMember = {
    implicit val projectContext: ProjectContext = member.projectContext
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

  override def deleteMember(member: ScMember): Unit = {
    member.getParent.getNode.removeChild(member.getNode)
  }

  def innerExtendsListTypes: Array[PsiClassType] = {
    val eb = extendsBlock
    if (eb != null) {
      val tp = eb.templateParents

      implicit val elementScope: ElementScope = ElementScope(getProject)
      tp match {
        case Some(tp1) => (for (te <- tp1.allTypeElements;
                                t = te.`type`().getOrAny;
                                asPsi = t.toPsiType
                                if asPsi.isInstanceOf[PsiClassType]) yield asPsi.asInstanceOf[PsiClassType]).toArray[PsiClassType]
        case _ => PsiClassType.EMPTY_ARRAY
      }
    } else PsiClassType.EMPTY_ARRAY
  }
}

object ScTemplateDefinitionImpl {

  private val originalElemKey: Key[ScTemplateDefinition] = Key.create("ScTemplateDefinition.originalElem")

  sealed abstract class Kind(val isFinal: Boolean)

  object Kind {
    object Class extends Kind(false)
    object Trait extends Kind(false)
    object Object extends Kind(true)
    object NewTd extends Kind(true)
    object SyntheticFinal extends Kind(true)
    object NonScala extends Kind(false)
  }

  case class Path(name: String, qualifiedName: Option[String], kind: Kind)

  object Path {

    val JavaObject: Path = Path(
      CommonClassNames.JAVA_LANG_OBJECT_SHORT,
      Some(CommonClassNames.JAVA_LANG_OBJECT),
      Kind.NonScala
    )

    def apply(clazz: PsiClass): Path = {
      import Kind._
      val kind = clazz match {
        case _: ScTrait => Trait
        case _: ScClass => Class
        case _: ScObject => Object
        case _: ScNewTemplateDefinition => NewTd
        case synthetic: ScSyntheticClass =>
          synthetic.className match {
            case "AnyRef" | "AnyVal" => Class
            case _ => SyntheticFinal
          }
        case _ => NonScala
      }

      Path(clazz.name, Option(clazz.qualifiedName), kind)
    }
  }
}