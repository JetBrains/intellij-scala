package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import java.lang.String
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import impl.light.LightField
import psi.stubs.ScTemplateDefinitionStub
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import collection.mutable.ArrayBuffer
import api.ScalaElementVisitor
import caches.CachesUtil
import util.PsiModificationTracker
import lang.resolve.ResolveUtils
import com.intellij.openapi.project.{Project, DumbServiceImpl}
import com.intellij.openapi.util.TextRange
import api.toplevel.ScTypedDefinition
import light.{EmptyPrivateConstructor, PsiClassWrapper}
import api.statements._
import params.ScClassParameter
import types.ScType
import extensions.toPsiMemberExt
import collection.mutable

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

  override def toString: String = if (isPackageObject) "ScPackageObject" else "ScObject"

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
    } else findChildByType(ScalaTokenTypes.kPACKAGE) != null || name == "`package`"
  }

  def hasPackageKeyword: Boolean = findChildByType(ScalaTokenTypes.kPACKAGE) != null

  override def isCase = hasModifierProperty("case")

  override def processDeclarationsForTemplateBody(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    if (DumbServiceImpl.getInstance(getProject).isDumb) return true
    if (!super[ScTemplateDefinition].processDeclarationsForTemplateBody(processor, state, lastParent, place)) return false
    if (isPackageObject && name != "`package`") {
      val qual = qualifiedName
      val facade = JavaPsiFacade.getInstance(getProject)
      val pack = facade.findPackage(qual) //do not wrap into ScPackage to avoid SOE
      if (pack != null && !ResolveUtils.packageProcessDeclarations(pack, processor, state, lastParent, place))
        return false
    }
    true
  }

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    super[ScTemplateDefinition].processDeclarations(processor, state, lastParent, place)
  }

  def objectSyntheticMembers: Seq[PsiMethod] = {
    import CachesUtil._
    get(this, OBJECT_SYNTHETIC_MEMBERS_KEY, new MyProvider[ScObjectImpl, Seq[PsiMethod]](this, obj => {
      obj.objectSyntheticMembersImpl
    })(PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT))
  }

  private def objectSyntheticMembersImpl: Seq[PsiMethod] = {
    if (isSyntheticObject) return Seq.empty
    ScalaPsiUtil.getCompanionModule(this) match {
      case Some(c: ScClass) if c.isCase =>
        val res = new ArrayBuffer[PsiMethod]
        val texts = c.getSyntheticMethodsText
        Seq(texts._1, texts._2).foreach(s => {
          try {
            val method = ScalaPsiElementFactory.createMethodWithContext(s, c.getContext, c)
            method.setSynthetic(this)
            res += method
          }
          catch {
            case e: Exception => //do not add methods with wrong signature
          }
        })
        res.toSeq
      case _ => Seq.empty
    }
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
  private var moduleField: PsiField = null
  @volatile
  private var moduleFieldModCount: Long = 0L

  private def getModuleField: PsiField = {
    val count = getManager.getModificationTracker.getOutOfCodeBlockModificationCount
    if (moduleField != null && moduleFieldModCount == count) return moduleField
    val field = new LightField(getManager, JavaPsiFacade.getInstance(getProject).getElementFactory.createFieldFromText(
      "public final static " + getQualifiedName + " MODULE$", this
    ), this)
    field.setNavigationElement(this)
    moduleField = field
    moduleFieldModCount = count
    field
  }

  override def getFields: Array[PsiField] = {
    Array(getModuleField)
  }

  override def findFieldByName(name: String, checkBases: Boolean): PsiField = {
    name match {
      case "MODULE$" => getModuleField
      case _ => null
    }
  }

  import org.jetbrains.plugins.scala.lang.psi.light.PsiTypedDefinitionWrapper.DefinitionRole._

  override def getInnerClasses: Array[PsiClass] = Array.empty

  override def getMethods: Array[PsiMethod] = {
    getAllMethods.filter(_.containingClass == this)
  }

  override def getAllMethods: Array[PsiMethod] = {
    val res = new ArrayBuffer[PsiMethod]()
    res ++= getConstructors
    val linearization = MixinNodes.linearization(this).flatMap(tp => ScType.extractClass(tp, Some(getProject)))
    def getClazz(t: ScTrait): PsiClass = {
      var index = linearization.indexWhere(_ == t)
      while (index >= 0) {
        val clazz = linearization(index)
        if (!clazz.isInterface) return clazz
        index -= 1
      }
      this
    }
    val signatures = TypeDefinitionMembers.getSignatures(this).forAll()._1.valuesIterator
    while (signatures.hasNext) {
      val signature = signatures.next()
      signature.foreach {
        case (t, node) => node.info.namedElement match {
          case Some(fun: ScFunction) if !fun.isConstructor && fun.containingClass.isInstanceOf[ScTrait] &&
            fun.isInstanceOf[ScFunctionDefinition] =>
            res += fun.getFunctionWrapper(isStatic = false, isInterface = false, cClass = Some(getClazz(fun.containingClass.asInstanceOf[ScTrait])))
          case Some(fun: ScFunction) if !fun.isConstructor =>
            res += fun.getFunctionWrapper(isStatic = false, isInterface = fun.isInstanceOf[ScFunctionDeclaration])
          case Some(method: PsiMethod) if !method.isConstructor => res += method
          case Some(t: ScTypedDefinition) if t.isVal || t.isVar ||
            (t.isInstanceOf[ScClassParameter] && t.asInstanceOf[ScClassParameter].isCaseClassVal) =>
            val (isInterface, cClass) = t.nameContext match {
              case m: ScMember =>
                val isConcrete = m.isInstanceOf[ScPatternDefinition] || m.isInstanceOf[ScVariableDefinition] || m.isInstanceOf[ScClassParameter]
                (!isConcrete, m.containingClass match {
                  case t: ScTrait =>
                    if (isConcrete) {
                      Some(getClazz(t))
                    } else None
                case _ => None
                })
              case _ => (false, None)
            }
            val nodeName = node.info.name
            if (nodeName == t.name) {
              res += t.getTypedDefinitionWrapper(isStatic = false, isInterface = isInterface, role = SIMPLE_ROLE, cClass = cClass)
              if (t.isVar) {
                res += t.getTypedDefinitionWrapper(isStatic = false, isInterface = isInterface, role = EQ, cClass = cClass)
              }
            }
            t.nameContext match {
              case s: ScAnnotationsHolder =>
                val beanProperty = ScalaPsiUtil.isBeanProperty(s)
                val booleanBeanProperty = ScalaPsiUtil.isBooleanBeanProperty(s)
                if (beanProperty) {
                  if (nodeName == "get" + t.name.capitalize) {
                    res += t.getTypedDefinitionWrapper(isStatic = false, isInterface = isInterface, role = GETTER, cClass = cClass)
                  }
                  if (t.isVar && nodeName == "set" + t.name.capitalize) {
                    res += t.getTypedDefinitionWrapper(isStatic = false, isInterface = isInterface, role = SETTER, cClass = cClass)
                  }
                } else if (booleanBeanProperty) {
                  if (nodeName == "is" + t.name.capitalize) {
                    res += t.getTypedDefinitionWrapper(isStatic = false, isInterface = isInterface, role = IS_GETTER, cClass = cClass)
                  }
                  if (t.isVar && nodeName == "set" + t.name.capitalize) {
                    res += t.getTypedDefinitionWrapper(isStatic = false, isInterface = isInterface, role = SETTER, cClass = cClass)
                  }
                }
              case _ =>
            }
          case _ =>
        }
      }
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
