package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import com.intellij.lang.ASTNode
import com.intellij.lang.java.lexer.JavaLexer
import com.intellij.openapi.project.{DumbService, DumbServiceImpl, Project}
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.impl.light.LightField
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.{PsiModificationTracker, PsiUtil}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.light.{EmptyPrivateConstructor, PsiClassWrapper}
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
 * @author Alexander Podkhalyuzin
 * Date: 20.02.2008
 */
class ScObjectImpl extends ScTypeDefinitionImpl with ScObject with ScTemplateDefinition {
  override def additionalJavaNames: Array[String] = {
    fakeCompanionClass match {
      case Some(c) => Array(c.getName)
      case _ => Array.empty
    }
  }

  override def getNavigationElement: PsiElement = {
    if (isSyntheticObject) {
      ScalaPsiUtil.getCompanionModule(this) match {
        case Some(clazz) => return clazz.getNavigationElement
        case _ =>
      }
    }
    super.getNavigationElement
  }

  override def getContainingFile: PsiFile = {
    if (isSyntheticObject) {
      ScalaPsiUtil.getCompanionModule(this) match {
        case Some(clazz) => return clazz.getContainingFile
        case _ =>
      }
    }
    super.getContainingFile
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  def this(node: ASTNode) = {this (); setNode(node)}

  def this(stub: ScTemplateDefinitionStub) = {this (); setStub(stub); setNode(null)}

  override def toString: String = (if (isPackageObject) "ScPackageObject: " else "ScObject: ") + name

  override def getIconInner = if (isPackageObject) Icons.PACKAGE_OBJECT else Icons.OBJECT

  override def getName: String = {
    if (isPackageObject) return "package$"
    super.getName + "$"
  }

  override def hasModifierProperty(name: String): Boolean = {
    if (name == "final") return true
    super[ScTypeDefinitionImpl].hasModifierProperty(name)
  }

  override def isObject : Boolean = true

  override def isPackageObject: Boolean = {
    val stub = getStub
    if (stub != null) {
      stub.asInstanceOf[ScTemplateDefinitionStub].isPackageObject
    } else findChildByType[PsiElement](ScalaTokenTypes.kPACKAGE) != null || name == "`package`"
  }

  def hasPackageKeyword: Boolean = findChildByType[PsiElement](ScalaTokenTypes.kPACKAGE) != null

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
        case ignore: DoNotProcessPackageObjectException => true //do nothing, just let's move on
      } finally {
        stopPackageObjectProcessing()
      }
    } else {
      super[ScTemplateDefinition].processDeclarations(processor, state, lastParent, place)
    }
  }

  def objectSyntheticMembers: Seq[PsiMethod] = {
    import org.jetbrains.plugins.scala.caches.CachesUtil._
    get(this, OBJECT_SYNTHETIC_MEMBERS_KEY, new MyProvider[ScObjectImpl, Seq[PsiMethod]](this, obj => {
      obj.objectSyntheticMembersImpl
    })(PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT))
  }

  protected def objectSyntheticMembersImpl: Seq[PsiMethod] = {
    (if (isSyntheticObject) Seq.empty
    else ScalaPsiUtil.getCompanionModule(this) match {
      case Some(c: ScClass) if c.isCase =>
        val res = new ArrayBuffer[PsiMethod]
        c.getSyntheticMethodsText.foreach(s => {
          try {
            val method = ScalaPsiElementFactory.createMethodWithContext(s, c.getContext, c)
            method.setSynthetic(this)
            method.syntheticCaseClass = Some(c)
            res += method
          }
          catch {
            case e: Exception => //do not add methods with wrong signature
          }
        })
        res.toSeq
      case _ => Seq.empty
    }) ++: SyntheticMembersInjector.inject(this)
  }

  def fakeCompanionClass: Option[PsiClass] = {
    ScalaPsiUtil.getCompanionModule(this) match {
      case Some(module) => None
      case None => Some(new PsiClassWrapper(this, getQualifiedName.substring(0, getQualifiedName.length() - 1),
       getName.substring(0, getName.length() - 1)))
    }
  }

  def fakeCompanionClassOrCompanionClass: PsiClass = {
    fakeCompanionClass match {
      case Some(clazz) => clazz
      case _ =>
        ScalaPsiUtil.getCompanionModule(this).get
    }
  }

  @volatile
  private var moduleField: Option[PsiField] = null
  @volatile
  private var moduleFieldModCount: Long = 0L

  private def getModuleField: Option[PsiField] = {
    val count = getManager.getModificationTracker.getOutOfCodeBlockModificationCount
    if (moduleField != null && moduleFieldModCount == count) return moduleField
    val fieldOption =
      if (getQualifiedName.split('.').exists(JavaLexer.isKeyword(_, PsiUtil.getLanguageLevel(this)))) None else {
        val field: LightField = new LightField(getManager, JavaPsiFacade.getInstance(getProject).getElementFactory.createFieldFromText(
          "public final static " + getQualifiedName + " MODULE$", this
        ), this)
        field.setNavigationElement(this)
        Some(field)
      }
    moduleField = fieldOption
    moduleFieldModCount = count
    fieldOption
  }

  override def getFields: Array[PsiField] = {
    getModuleField.toArray
  }

  override def findFieldByName(name: String, checkBases: Boolean): PsiField = {
    name match {
      case "MODULE$" => getModuleField.orNull
      case _ => null
    }
  }

  override def getInnerClasses: Array[PsiClass] = Array.empty

  override def getMethods: Array[PsiMethod] = {
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

  @volatile
  private var emptyObjectConstructor: EmptyPrivateConstructor = null
  @volatile
  private var emptyObjectConstructorModCount: Long = 0L

  override def getConstructors: Array[PsiMethod] = {
    val curModCount = getManager.getModificationTracker.getOutOfCodeBlockModificationCount
    if (emptyObjectConstructor != null && emptyObjectConstructorModCount == curModCount) {
      return Array(emptyObjectConstructor)
    }
    val res = new EmptyPrivateConstructor(this)
    emptyObjectConstructorModCount = curModCount
    emptyObjectConstructor = res
    Array(res)
  }

  override def isPhysical: Boolean = {
    if (isSyntheticObject) false
    else super.isPhysical
  }

  override def getTextRange: TextRange = {
    if (isSyntheticObject) null
    else super.getTextRange
  }

  override def getInterfaces: Array[PsiClass] = {
    getSupers.filter(_.isInterface)
  }

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
}
