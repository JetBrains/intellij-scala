package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import com.intellij.lang.ASTNode
import com.intellij.lang.java.lexer.JavaLexer
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.PsiUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.getCompanionModule
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.SignatureNodes
import org.jetbrains.plugins.scala.lang.psi.light.{EmptyPrivateConstructor, LightUtil, PsiClassWrapper}
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.types.PhysicalSignature
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}

import scala.collection.mutable.ArrayBuffer

/**
 * @author Alexander Podkhalyuzin
 * Date: 20.02.2008
 */
class ScObjectImpl protected (stub: ScTemplateDefinitionStub, node: ASTNode)
  extends ScTypeDefinitionImpl(stub, ScalaElementTypes.OBJECT_DEFINITION, node) with ScObject with ScTemplateDefinition {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScTemplateDefinitionStub) = this(stub, null)

  override def additionalJavaNames: Array[String] = {
    fakeCompanionClass match {
      case Some(c) => Array(c.getName)
      case _ => Array.empty
    }
  }

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
  override def hasModifierProperty(name: String): Boolean = {
    if (name == "final") return true
    super[ScTypeDefinitionImpl].hasModifierProperty(name)
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
    if (isPackageObject) {
      import org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl._
      startPackageObjectProcessing()
      try {
        super[ScTemplateDefinition].processDeclarations(processor, state, lastParent, place)
      } catch {
        case _: DoNotProcessPackageObjectException => true //do nothing, just let's move on
      } finally {
        stopPackageObjectProcessing()
      }
    } else {
      super[ScTemplateDefinition].processDeclarations(processor, state, lastParent, place)
    }
  }

  override protected def syntheticMethodsWithOverrideImpl: Seq[PsiMethod] = {
    val res = if (isSyntheticObject) Seq.empty
    else getCompanionModule(this) match {
      case Some(c: ScClass) if c.isCase =>
        val res = new ArrayBuffer[PsiMethod]
        c.getSyntheticMethodsText.foreach(s => {
          try {
            val method = ScalaPsiElementFactory.createMethodWithContext(s, c.getContext, c)
            method.setSynthetic(this)
            method.setSyntheticCaseClass(c)
            res += method
          }
          catch {
            case p: ProcessCanceledException => throw p
            case _: Exception => //do not add methods with wrong signature
          }
        })
        res
      case _ => Seq.empty
    }
    res ++ super.syntheticMethodsWithOverrideImpl
  }

  override protected def syntheticMethodsNoOverrideImpl: Seq[PsiMethod] = SyntheticMembersInjector.inject(this, withOverride = false)

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

    for (synthetic <- syntheticMethodsNoOverride) {
      this.processPsiMethodsForNode(new SignatureNodes.Node(new PhysicalSignature(synthetic, ScSubstitutor.empty),
        ScSubstitutor.empty),
        isStatic = false, isInterface = isInterface)(res += _)
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
    val text = if (isSynthetic) {
      val syntheticText = getCompanionModule(this) match {
        case Some(c: ScClass) if c.isCase => c.getSyntheticMethodsText.mkString("\n")
        case _ => ""
      }
      val str = tree.toString()
      if (str.lastIndexOf("}") == -1)
        s"$str {\n $syntheticText\n }"
      else
        str.replaceAll("}$", s"\n$syntheticText\n}")
    } else tree.toString()

    ScalaPsiElementFactory.createObjectWithContext(text, getContext, this).
      setDesugared(actualElement = this)
  }

  override def desugaredElement: Option[ScTemplateDefinition] = {
    import scala.meta.intellij.psiExt._
    import scala.meta.{Defn, Term}

    if (isDesugared) return None
    val (expansion, isSynthetic) = this.getMetaExpansion match {
      case Right(tree: Defn.Object) =>
        Some(tree) -> false
      case _ => fakeCompanionClassOrCompanionClass match {
        case ah: ScAnnotationsHolder => ah.getMetaExpansion match {
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
