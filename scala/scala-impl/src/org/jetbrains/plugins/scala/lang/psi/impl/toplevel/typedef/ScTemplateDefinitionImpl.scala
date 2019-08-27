package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import java.{util => ju}

import com.intellij.execution.junit.JUnitUtil.SUITE_METHOD_NAME
import com.intellij.lang.ASTNode
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.{Pair => JBPair}
import com.intellij.psi.impl.{PsiClassImplUtil, PsiSuperMethodImplUtil}
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.scope.processor.MethodsProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.{PsiTreeUtil, PsiUtil}
import com.intellij.psi.{HierarchicalMethodSignature, PsiClass, PsiElement, PsiField, PsiMethod, PsiSubstitutor, ResolveState}
import org.jetbrains.plugins.scala.caches.{CachesUtil, ScalaShortNamesCacheManager}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTemplateDefinition, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.light.ScFunctionWrapper
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType
import org.jetbrains.plugins.scala.lang.psi.types.TermSignature
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, CachedInUserData, ModCount}

import scala.collection.{JavaConverters, mutable}

abstract class ScTemplateDefinitionImpl[T <: ScTemplateDefinition](stub: ScTemplateDefinitionStub[T],
                                                                   nodeType: ScTemplateDefinitionElementType[T],
                                                                   node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, nodeType, node)
    with ScTemplateDefinition
    with PsiClassFake {

  override def getAllMethods: Array[PsiMethod] = {
    val result = mutable.ArrayBuffer(getConstructors: _*)

    allSignatures.foreach { signature =>
      this.processWrappersForSignature(signature, isStatic = false, isInterface = isInterface(signature))(result.+=)
    }

    result.toArray
  }

  override final def getAllFields: Array[PsiField] =
    PsiClassImplUtil.getAllFields(this)

  override def findFieldByName(name: String, checkBases: Boolean): PsiField =
    PsiClassImplUtil.findFieldByName(this, name, checkBases)

  override final def findMethodBySignature(patternMethod: PsiMethod, checkBases: Boolean): PsiMethod =
    PsiClassImplUtil.findMethodBySignature(this, patternMethod, checkBases)

  override final def findMethodsBySignature(patternMethod: PsiMethod, checkBases: Boolean): Array[PsiMethod] =
    PsiClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases)

  override final def getAllMethodsAndTheirSubstitutors: ju.List[JBPair[PsiMethod, PsiSubstitutor]] =
    PsiClassImplUtil.getAllWithSubstitutorsByMap(this, PsiClassImplUtil.MemberType.METHOD)

  //the reordering is a hack to enable 'go to test location' for junit test methods defined in traits
  override final def findMethodsAndTheirSubstitutorsByName(name: String,
                                                           checkBases: Boolean): ju.List[JBPair[PsiMethod, PsiSubstitutor]] = {
    import JavaConverters._
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

  override final def findInnerClassByName(name: String, checkBases: Boolean): PsiClass =
    PsiClassImplUtil.findInnerByName(this, name, checkBases)

  @CachedInUserData(this, CachesUtil.libraryAwareModTracker(this))
  override final def getVisibleSignatures: ju.Collection[HierarchicalMethodSignature] =
    PsiSuperMethodImplUtil.getVisibleSignatures(this)

  override final def findMethodsByName(name: String, checkBases: Boolean): Array[PsiMethod] = name match {
    case "main" | SUITE_METHOD_NAME => // these methods may be searched from EDT, search them without building a whole type hierarchy
      val inThisClass = allFunctionsByName(name)

      import JavaConverters._
      val files = this.allSupers.flatMap(_.containingVirtualFile).asJava
      val scope = GlobalSearchScope.filesScope(getProject, files)
      val inBaseClasses = ScalaShortNamesCacheManager.getInstance(getProject)
        .methodsByName(name)(scope)
        .filter { method =>
          isInheritor(method.containingClass, checkDeep = true)
        }

      (inThisClass ++ inBaseClasses).toArray
    case _ => PsiClassImplUtil.findMethodsByName(this, name, checkBases)
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
        case Some(ancestor) if PsiTreeUtil.isContextAncestor(ancestor, place, false) && lastParent != null => true
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

  import ScTemplateDefinitionImpl.{Kind, Path}

  override final def isInheritor(baseClass: PsiClass, checkDeep: Boolean): Boolean = Path.of(baseClass) match {
    case Path.JavaObject => true // These doesn't appear in the superTypes at the moment, so special case required.
    case Path(_, _, kind) if kind.isFinal => false
    case path if checkDeep => superPathsDeep.contains(path)
    case path => superPaths.contains(path)
  }

  protected def isInterface(signature: TermSignature): Boolean = signature.namedElement match {
    case definition: ScTypedDefinition if definition.isAbstractMember => true
    case _ => false
  }

  @Cached(ModCount.getModificationCount, this)
  private def superPathsDeep: Set[Path] =
    if (DumbService.getInstance(getProject).isDumb)
      Set.empty //to prevent failing during indexes
    else {
      val collected = mutable.Set.empty[Path]

      def addForClass(c: PsiClass): Unit =
        if (collected.add(Path.of(c))) {
          c match {
            case td: ScTemplateDefinition =>
              val supersIterator = td.supers.iterator
              while (supersIterator.hasNext) {
                addForClass(supersIterator.next())
              }
            case other =>
              val supersIterator = other.getSuperTypes.iterator
              while (supersIterator.hasNext) {
                val psiT = supersIterator.next()
                val next = psiT.resolveGenerics.getElement
                if (next != null) {
                  addForClass(next)
                }
              }
          }
        }

      addForClass(this)

      (collected - cachedPath).toSet
    }

  @Cached(ModCount.getModificationCount, this)
  private def superPaths: Set[Path] =
    if (DumbService.getInstance(getProject).isDumb)
      Set.empty //to prevent failing during indexes
    else
      supers.map(Path.of).toSet

  @Cached(ModCount.getModificationCount, this)
  private def cachedPath: Path = {
    val kind = this match {
      case _: ScTrait => Kind.ScTrait
      case _: ScClass => Kind.ScClass
      case _: ScObject => Kind.ScObject
      case _: ScNewTemplateDefinition => Kind.ScNewTd
      case _ => Kind.NonScala
    }
    Path(name, Option(qualifiedName), kind)
  }
}

object ScTemplateDefinitionImpl {

  sealed abstract class Kind(val isFinal: Boolean)

  object Kind {
    object ScClass extends Kind(false)
    object ScTrait extends Kind(false)
    object ScObject extends Kind(true)
    object ScNewTd extends Kind(true)
    object SyntheticFinal extends Kind(true)
    object NonScala extends Kind(false)
  }

  case class Path(name: String, qName: Option[String], kind: Kind)

  object Path {

    def of(c: PsiClass): Path = c match {
      case td: ScTemplateDefinitionImpl[_] =>
        td.cachedPath
      case s: ScSyntheticClass if s.className != "AnyRef" && s.className != "AnyVal" =>
        Path(c.name, Option(c.qualifiedName), Kind.SyntheticFinal)
      case _: ScSyntheticClass =>
        Path(c.name, Option(c.qualifiedName), Kind.ScClass)
      case _ =>
        Path(c.name, Option(c.qualifiedName), Kind.NonScala)
    }

    val JavaObject = Path("Object", Some("java.lang.Object"), Kind.NonScala)
  }
}