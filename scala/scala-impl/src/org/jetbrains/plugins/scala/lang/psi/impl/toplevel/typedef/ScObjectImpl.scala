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
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.PsiUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.getCompanionModule
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.light.{EmptyPrivateConstructor, LightUtil, PsiClassWrapper}
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}

import scala.collection.mutable.ArrayBuffer
import scala.util.control.ControlThrowable

/**
 * @author Alexander Podkhalyuzin
 * Date: 20.02.2008
 */
class ScObjectImpl(stub: ScTemplateDefinitionStub[ScObject],
                   nodeType: ScTemplateDefinitionElementType[ScObject],
                   node: ASTNode)
  extends ScTypeDefinitionImpl(stub, nodeType, node)
    with ScObject with ScTemplateDefinition {

  override def additionalClassJavaName: Option[String] =
    if (baseCompanionModule.isEmpty) Option(getName).map(_.stripSuffix("$")) else None

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
    if (isSyntheticObject) getContext.getContainingFile
    else super.getContainingFile
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def toString: String = (if (isPackageObject) "ScPackageObject: " else "ScObject: ") + ifReadAllowed(name)("")

  override def getName: String = {
    if (isPackageObject) return "package$"
    super.getName + "$"
  }

  // TODO Should be unified, see ScModifierListOwner
  override def hasModifierProperty(name: String): Boolean = name match {
    case PsiModifier.FINAL => true
    case _ => super[ScTypeDefinitionImpl].hasModifierProperty(name)
  }

  override def isObject : Boolean = true

  override def isPackageObject: Boolean = byStubOrPsi(_.isPackageObject) {
    findChildByType(ScalaTokenTypes.kPACKAGE) != null || name == "`package`"
  }


  def hasPackageKeyword: Boolean = findChildByType[PsiElement](ScalaTokenTypes.kPACKAGE) != null

  override def isCase: Boolean = hasModifierProperty("case")

  override def processDeclarationsForTemplateBody(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    if (DumbService.getInstance(getProject).isDumb) return true
    if (!super[ScTemplateDefinition].processDeclarationsForTemplateBody(processor, state, lastParent, place)) return false
    if (isPackageObject && name != "`package`") {
      val newState = state.put(BaseProcessor.FROM_TYPE_KEY, null)
      val qual = qualifiedName
      val facade = JavaPsiFacade.getInstance(getProject)
      val pack = facade.findPackage(qual) //do not wrap into ScPackage to avoid SOE
      if (pack != null && !ResolveUtils.packageProcessDeclarations(pack, processor, newState, lastParent, place))
        return false
    }
    true
  }

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    def processDeclarations() = super[ScTemplateDefinition].processDeclarations(processor, state, lastParent, place)

    if (isPackageObject) ScObjectImpl.withFlag {
      processDeclarations()
    } else processDeclarations()
  }

  @Cached(ModCount.getBlockModificationCount, this)
  def fakeCompanionClass: Option[PsiClass] = getCompanionModule(this) match {
    case Some(_) => None
    case None =>
      val qualName = Option(getQualifiedName).map(_.stripSuffix("$"))
      val name = Option(getName).map(_.stripSuffix("$"))
      qualName.map(qn => new PsiClassWrapper(this, qn, name.getOrElse(qn)))
  }

  def fakeCompanionClassOrCompanionClass: PsiClass = fakeCompanionClass match {
    case Some(clazz) => clazz
    case _ => getCompanionModule(this).get
  }

  @Cached(ModCount.getBlockModificationCount, this)
  private def getModuleField: Option[PsiField] = {
    if (getQualifiedName.split('.').exists(JavaLexer.isKeyword(_, PsiUtil.getLanguageLevel(this.getProject)))) None
    else {
      Some(LightUtil.createLightField("public final static " + getQualifiedName + " MODULE$", this))
    }
  }

  override def psiFields: Array[PsiField] = {
    getModuleField.toArray
  }

  override def findFieldByName(name: String, checkBases: Boolean): PsiField = {
    name match {
      case "MODULE$" => getModuleField.orNull
      case _ => null
    }
  }

  override def psiInnerClasses: Array[PsiClass] = Array.empty

  override def psiMethods: Array[PsiMethod] = {
    getAllMethods.filter(_.containingClass == this)
  }

  override def getAllMethods: Array[PsiMethod] = {
    val res = new ArrayBuffer[PsiMethod]()
    res ++= getConstructors
    TypeDefinitionMembers.SignatureNodes.forAllSignatureNodes(this) { node =>
      val isInterface = node.info.namedElement match {
        case t: ScTypedDefinition if t.isAbstractMember => true
        case _ => false
      }
      this.processPsiMethodsForNode(node, isStatic = false, isInterface = isInterface)(res += _)
    }
    res.toArray
  }

  @Cached(ModCount.getBlockModificationCount, this)
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

  @Cached(ModCount.getBlockModificationCount, this)
  private def cachedDesugared(tree: scala.meta.Defn.Object, isSynthetic: Boolean): ScTemplateDefinition = {
    val text = tree.toString()

    ScalaPsiElementFactory.createObjectWithContext(text, getContext, this).
      setDesugared(actualElement = this)
  }

  override def desugaredElement: Option[ScTemplateDefinition] = {
    import scala.meta.intellij.psi._
    import scala.meta.{Defn, Term}

    if (DumbService.isDumb(getProject) || isDesugared) return None

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

    expansion.map(cachedDesugared(_, isSynthetic))
  }
}

object ScObjectImpl {

  private[this] val processing = new ThreadLocal[Long] {
    override def initialValue(): Long = 0
  }

  private class DoNotProcessPackageObjectException extends ControlThrowable

  def checkPackageObject(): Unit = processing.get match {
    case 0 =>
    case _ => throw new DoNotProcessPackageObjectException
  }

  private def withFlag(action: => Boolean) =
    try {
      processing.set(processing.get + 1)
      action
    } catch {
      case _: DoNotProcessPackageObjectException => true // do nothing, just let's move on
    } finally {
      processing.set(processing.get - 1)
    }
}
