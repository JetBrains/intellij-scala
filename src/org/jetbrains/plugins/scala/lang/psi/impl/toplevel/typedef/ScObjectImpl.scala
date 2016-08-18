package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import com.intellij.lang.ASTNode
import com.intellij.lang.java.lexer.JavaLexer
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.impl.light.LightField
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.SignatureNodes
import org.jetbrains.plugins.scala.lang.psi.light.{EmptyPrivateConstructor, PsiClassWrapper}
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScObjectDefinitionElementType
import org.jetbrains.plugins.scala.lang.psi.types.{PhysicalSignature, ScSubstitutor}
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

/**
 * @author Alexander Podkhalyuzin
 * Date: 20.02.2008
 */
class ScObjectImpl protected (stub: StubElement[ScTemplateDefinition], nodeType: IElementType, node: ASTNode)
  extends ScTypeDefinitionImpl(stub, nodeType, node) with ScObject with ScTemplateDefinition {

  def this(node: ASTNode) =
    this(null, null, node)

  def this(stub: ScTemplateDefinitionStub, elementType: ScObjectDefinitionElementType) =
    this(stub, elementType, null)

  override def additionalJavaNames: Array[String] =
    fakeCompanionClass.map(_.getName).toArray

  override def getNavigationElement: PsiElement =
    companionModule.map {
      _.getNavigationElement
    }.getOrElse(super.getNavigationElement)

  override def getContainingFile: PsiFile =
    companionModule.map {
      _.getContainingFile
    }.getOrElse(super.getContainingFile)

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def toString: String =
    s"Sc${if (isPackageObject) "Package" else ""}Object: $name"

  override def getIconInner =
    if (isPackageObject) Icons.PACKAGE_OBJECT else Icons.OBJECT

  override def getName: String =
    s"${if (isPackageObject) "package" else super.getName}$$"

  override def hasModifierProperty(name: String): Boolean = {
    if (name == "final") return true
    super[ScTypeDefinitionImpl].hasModifierProperty(name)
  }

  override def isPackageObject: Boolean =
    Option(getStub).collect {
      case stub: ScTemplateDefinitionStub => stub.isPackageObject
    }.getOrElse(hasPackageKeyword || name == "`package`")

  def hasPackageKeyword: Boolean =
    findChildByType[PsiElement](ScalaTokenTypes.kPACKAGE) != null

  override def isCase = hasModifierProperty("case")

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
    (isSyntheticObject match {
      case true => None
      case _ => ScalaPsiUtil.getBaseCompanionModule(this)
    }).toSeq.collect {
      case clazz: ScClass if clazz.isCase => clazz
    }.flatMap { clazz =>
      def createMethod(text: String): Option[PsiMethod] = Try {
        val method = ScalaPsiElementFactory.createMethodWithContext(text, clazz.getContext, clazz)
        method.setSynthetic(this)
        method.syntheticCaseClass = Some(clazz)
        method
      }.toOption

      clazz.getSyntheticMethodsText.flatMap(createMethod)
    } ++ super.syntheticMethodsWithOverrideImpl
  }

  override protected def syntheticMethodsNoOverrideImpl: Seq[PsiMethod] = SyntheticMembersInjector.inject(this, withOverride = false)

  @Cached(synchronized = false, ModCount.getBlockModificationCount, this)
  def fakeCompanionClass: Option[PsiClass] = {
    val clazz = ScalaPsiUtil.getBaseCompanionModule(this) match {
      case None => new PsiClassWrapper(this, getQualifiedName.dropRight(1), getName.dropRight(1))
      case _ => null
    }
    Option(clazz)
  }

  def fakeCompanionClassOrCompanionClass: PsiClass =
    fakeCompanionClass.orElse(ScalaPsiUtil.getBaseCompanionModule(this)).get

  @Cached(synchronized = false, ModCount.getBlockModificationCount, this)
  private def getModuleField: Option[PsiField] = {
    if (getQualifiedName.split('.').exists(JavaLexer.isKeyword(_, PsiUtil.getLanguageLevel(this)))) None
    else {
      val field: LightField = new LightField(getManager,
        JavaPsiFacade.getInstance(getProject).getElementFactory.createFieldFromText(s"public final static $getQualifiedName MODULE$$", this),
        this)
      field.setNavigationElement(this)
      Some(field)
    }
  }

  override def getFields: Array[PsiField] = getModuleField.toArray

  override def findFieldByName(name: String, checkBases: Boolean): PsiField = name match {
    case "MODULE$" => getModuleField.orNull
    case _ => null
  }

  override def getInnerClasses: Array[PsiClass] = Array.empty

  override def getMethods: Array[PsiMethod] = getAllMethods.filter(_.containingClass == this)

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

  @Cached(synchronized = false, ModCount.getBlockModificationCount, this)
  override def getConstructors: Array[PsiMethod] =
    Array(new EmptyPrivateConstructor(this))

  override def isPhysical: Boolean = {
    if (isSyntheticObject) false
    else super.isPhysical
  }

  override def getTextRange: TextRange = {
    if (isSyntheticObject) null
    else super.getTextRange
  }

  override def getInterfaces: Array[PsiClass] = getSupers.filter(_.isInterface)

  private val hardParameterlessSignatures: mutable.WeakHashMap[Project, TypeDefinitionMembers.ParameterlessNodes.Map] =
    new mutable.WeakHashMap[Project, TypeDefinitionMembers.ParameterlessNodes.Map]
  def getHardParameterlessSignatures: TypeDefinitionMembers.ParameterlessNodes.Map = {
    hardParameterlessSignatures.getOrElseUpdate(getProject, TypeDefinitionMembers.ParameterlessNodes.build(this))
  }

  private val hardTypes: mutable.WeakHashMap[Project, TypeDefinitionMembers.TypeNodes.Map] =
    new mutable.WeakHashMap[Project, TypeDefinitionMembers.TypeNodes.Map]
  def getHardTypes: TypeDefinitionMembers.TypeNodes.Map = {
    hardTypes.getOrElseUpdate(getProject, TypeDefinitionMembers.TypeNodes.build(this))
  }

  private val hardSignatures: mutable.WeakHashMap[Project, TypeDefinitionMembers.SignatureNodes.Map] =
    new mutable.WeakHashMap[Project, TypeDefinitionMembers.SignatureNodes.Map]
  def getHardSignatures: TypeDefinitionMembers.SignatureNodes.Map = {
    hardSignatures.getOrElseUpdate(getProject, TypeDefinitionMembers.SignatureNodes.build(this))
  }

  private def companionModule: Option[ScTypeDefinition] =
    if (isSyntheticObject) ScalaPsiUtil.getBaseCompanionModule(this) else None
}
