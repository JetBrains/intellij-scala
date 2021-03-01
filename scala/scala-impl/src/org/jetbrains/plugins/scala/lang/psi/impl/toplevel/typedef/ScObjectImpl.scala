package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import com.intellij.lang.ASTNode
import com.intellij.lang.java.lexer.JavaLexer
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiModifier._
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.PsiUtil
import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.getCompanionModule
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScObjectImpl.moduleFieldName
import org.jetbrains.plugins.scala.lang.psi.light.{EmptyPrivateConstructor, PsiClassWrapper, ScLightField}
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, CachedWithRecursionGuard}

/**
 * @author Alexander Podkhalyuzin
 * Date: 20.02.2008
 */
class ScObjectImpl(
  stub:      ScTemplateDefinitionStub[ScObject],
  nodeType:  ScTemplateDefinitionElementType[ScObject],
  node:      ASTNode,
  debugName: String
) extends ScTypeDefinitionImpl(stub, nodeType, node, debugName)
    with ScObject {

  override protected def targetTokenType: ScalaTokenType = ScalaTokenType.ObjectKeyword

  override def additionalClassJavaName: Option[String] =
    if (baseCompanion.isEmpty) Option(getName).map(_.stripSuffix("$")) else None

  override def getNavigationElement: PsiElement = {
    if (isSyntheticObject) {
      getCompanionModule(this) match {
        case Some(clazz) => return clazz.getNavigationElement
        case _ =>
      }
    }
    super.getNavigationElement
  }

  override def getContainingFile: PsiFile = {
    if (isSyntheticObject) syntheticNavigationElement.getContainingFile
    else super.getContainingFile
  }

  override def getName: String =
    (if (isPackageObject) "package" else super.getName) + "$"

  //noinspection TypeAnnotation
  override protected final def baseIcon =
    if (isPackageObject) Icons.PACKAGE_OBJECT else Icons.OBJECT

  // TODO Should be unified, see ScModifierListOwner
  override def hasModifierProperty(name: String): Boolean = name match {
    case FINAL => true
    case _ => super[ScTypeDefinitionImpl].hasModifierProperty(name)
  }

  override def isObject : Boolean = true

  override def isPackageObject: Boolean = byStubOrPsi(_.isPackageObject) {
    findChildByType(ScalaTokenTypes.kPACKAGE) != null || name == "`package`"
  }

  override def hasPackageKeyword: Boolean = findChildByType[PsiElement](ScalaTokenTypes.kPACKAGE) != null

  override def isCase: Boolean = hasModifierProperty("case")

  override def processDeclarationsForTemplateBody(
    processor:  PsiScopeProcessor,
    state:      ResolveState,
    lastParent: PsiElement,
    place:      PsiElement
  ): Boolean =
    if (DumbService.getInstance(getProject).isDumb) true
    else if (!super.processDeclarationsForTemplateBody(processor, state, lastParent, place)) false
    else if (isPackageObject && name != "`package`") {
      JavaPsiFacade.getInstance(getProject)
        // do not wrap into ScPackage to avoid SOE
        .findPackage(qualifiedName) match {
        case null => true
        case pack =>
          val newState = state.withFromType(None)

          ScPackageImpl.packageProcessDeclarations(pack)(processor, newState, lastParent, place)(ScalaPsiManager.instance)
      }
    } else true

  override def processDeclarations(
    processor:  PsiScopeProcessor,
    state:      ResolveState,
    lastParent: PsiElement,
    place:      PsiElement
  ): Boolean = processDeclarationsImpl(processor, state, lastParent, place)

  @Cached(BlockModificationTracker(this), this)
  override def fakeCompanionClass: Option[PsiClass] = getCompanionModule(this) match {
    case Some(_) => None
    case None =>
      val qualName = Option(getQualifiedName).map(_.stripSuffix("$"))
      val name = Option(getName).map(_.stripSuffix("$"))
      name.map(new PsiClassWrapper(this, qualName.orNull, _))
  }

  override def fakeCompanionClassOrCompanionClass: PsiClass = fakeCompanionClass match {
    case Some(clazz) => clazz
    case _ => getCompanionModule(this).get
  }

  @Cached(BlockModificationTracker(this), this)
  private def getModuleField: Option[PsiField] = {
    def hasJavaKeywords(qName: String) =
      qName.split('.').exists(JavaLexer.isKeyword(_, PsiUtil.getLanguageLevel(this.getProject)))

    if (Option(getQualifiedName).forall(hasJavaKeywords))
      None
    else
      Some(ScLightField(moduleFieldName, ScDesignatorType(this), this, PUBLIC, FINAL, STATIC))
  }

  override def psiFields: Array[PsiField] = {
    getModuleField.toArray
  }

  override def findFieldByName(name: String, checkBases: Boolean): PsiField = {
    name match {
      case `moduleFieldName` => getModuleField.orNull
      case _ => null
    }
  }

  override def psiInnerClasses: Array[PsiClass] = Array.empty

  @Cached(BlockModificationTracker(this), this)
  override def getConstructors: Array[PsiMethod] = Array(new EmptyPrivateConstructor(this))

  override def isPhysical: Boolean = {
    if (isSyntheticObject) false
    else super.isPhysical
  }

  override def getTextRange: TextRange = {
    if (isSyntheticObject) getNavigationElement.getTextRange
    else super.getTextRange
  }

  override def getInterfaces: Array[PsiClass] = {
    getSupers.filter(_.isInterface)
  }

  @CachedWithRecursionGuard(this, None, BlockModificationTracker(this))
  override protected def desugaredInner: Option[ScTemplateDefinition] = {
    def toPsi(tree: scala.meta.Defn.Object, isSynthetic: Boolean): ScTemplateDefinition = {
      val text = tree.toString()

      ScalaPsiElementFactory.createObjectWithContext(text, getContext, this)
        .setOriginal(actualElement = this)
    }

    import scala.meta.intellij.psi._
    import scala.meta.{Defn, Term}

    val (expansion, isSynthetic) = this.metaExpand match {
      case Right(tree: Defn.Object) =>
        Some(tree) -> false
      case _ => fakeCompanionClassOrCompanionClass match {
        case ah: ScAnnotationsHolder => ah.metaExpand match {
          case Right(Term.Block(Seq(_, obj: Defn.Object))) =>
            Some(obj) -> true
          case _ => None -> false
        }
        case _ => None -> false
      }
    }

    expansion.map(toPsi(_, isSynthetic))
  }
}

object ScObjectImpl {
  private val moduleFieldName: String = "MODULE$"
}
