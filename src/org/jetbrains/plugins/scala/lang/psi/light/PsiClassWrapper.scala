package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.navigation.ItemPresentation
import com.intellij.psi._
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import impl.light.LightElement
import impl.PsiClassImplUtil
import javax.swing._
import java.util.Collection
import java.util.List
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.light.PsiTypedDefinitionWrapper.DefinitionRole._
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTrait, ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements._

/**
 * @author Alefas
 * @since 10.02.12
 */
class PsiClassWrapper(val definition: ScTemplateDefinition,
                      private var qualName: String,
                      private var name: String) extends LightElement(definition.getManager, definition.getLanguage) with PsiClass {
  def getQualifiedName: String = qualName

  def isInterface: Boolean = false

  def isAnnotationType: Boolean = false

  def isEnum: Boolean = false

  def getExtendsList: PsiReferenceList = null //todo:

  def getImplementsList: PsiReferenceList = null //todo: ?

  def getExtendsListTypes: Array[PsiClassType] = Array.empty

  def getImplementsListTypes: Array[PsiClassType] = Array.empty

  def getSuperClass: PsiClass = null

  def getInterfaces: Array[PsiClass] = Array.empty

  def getSupers: Array[PsiClass] = Array.empty

  def getSuperTypes: Array[PsiClassType] = Array.empty

  def getFields: Array[PsiField] = {
    definition match {
      case o: ScObject => Array.empty
      case _ => definition.getFields //todo:
    }
  }

  def getMethods: Array[PsiMethod] = {
    definition match {
      case o: ScObject =>
        val res = new ArrayBuffer[PsiMethod]()
        val signatures = TypeDefinitionMembers.getSignatures(o).forAll()._1.valuesIterator
        while (signatures.hasNext) {
          val signature = signatures.next()
          signature.foreach {
            case (t, node) =>
              node.info.namedElement match {
                case Some(fun: ScFunction) if !fun.isConstructor => res += fun.getFunctionWrapper(true, false)
                case Some(method: PsiMethod) if !method.isConstructor => {
                  if (method.getContainingClass != null && method.getContainingClass.getQualifiedName != "java.lang.Object") {
                    res += StaticPsiMethodWrapper.getWrapper(method, this)
                  }
                }
                case Some(t: ScTypedDefinition) if t.isVal || t.isVar =>
                  res += t.getTypedDefinitionWrapper(true, false, SIMPLE_ROLE)
                  t.nameContext match {
                    case s: ScAnnotationsHolder =>
                      val beanProperty = s.hasAnnotation("scala.reflect.BeanProperty") != None
                      val booleanBeanProperty = s.hasAnnotation("scala.reflect.BooleanBeanProperty") != None
                      if (beanProperty) {
                        res += t.getTypedDefinitionWrapper(true, false, GETTER)
                        if (t.isVar) {
                          res += t.getTypedDefinitionWrapper(true, false, SETTER)
                        }
                      } else if (booleanBeanProperty) {
                        res += t.getTypedDefinitionWrapper(true, false, IS_GETTER)
                        if (t.isVar) {
                          res += t.getTypedDefinitionWrapper(true, false, SETTER)
                        }
                      }
                    case _ =>
                  }
                case _ =>
              }
          }
        }
        res.toArray
      case t: ScTrait =>
        val res = new ArrayBuffer[PsiMethod]()
        val members = t.members
        members foreach {
          case fun: ScFunctionDefinition => res += fun.getStaticTraitFunctionWrapper(this)
          case definition: ScPatternDefinition => //only getters and setters should be added
            val beanProperty = definition.hasAnnotation("scala.reflect.BeanProperty") != None
            val booleanBeanProperty = definition.hasAnnotation("scala.reflect.BooleanBeanProperty") != None
            if (beanProperty || booleanBeanProperty) {
              for (t <- definition.declaredElements) {
                if (beanProperty) {
                  res += t.getStaticTypedDefinitionWrapper(GETTER, this)
                  if (t.isVar) {
                    res += t.getStaticTypedDefinitionWrapper(SETTER, this)
                  }
                } else if (booleanBeanProperty) {
                  res += t.getStaticTypedDefinitionWrapper(IS_GETTER, this)
                  if (t.isVar) {
                    res += t.getStaticTypedDefinitionWrapper(SETTER, this)
                  }
                }
              }
            }
          case definition: ScVariableDefinition => //only getters and setters should be added
            val beanProperty = definition.hasAnnotation("scala.reflect.BeanProperty") != None
            val booleanBeanProperty = definition.hasAnnotation("scala.reflect.BooleanBeanProperty") != None
            if (beanProperty || booleanBeanProperty) {
              for (t <- definition.declaredElements) {
                if (beanProperty) {
                  res += t.getStaticTypedDefinitionWrapper(GETTER, this)
                  if (t.isVar) {
                    res += t.getStaticTypedDefinitionWrapper(SETTER, this)
                  }
                } else if (booleanBeanProperty) {
                  res += t.getStaticTypedDefinitionWrapper(IS_GETTER, this)
                  if (t.isVar) {
                    res += t.getStaticTypedDefinitionWrapper(SETTER, this)
                  }
                }
              }
            }
          case _ =>
        }
        res.toArray
    }
  }

  @volatile
  private var emptyObjectConstructor: EmptyPrivateConstructor = null
  @volatile
  private var emptyObjectConstructorModCount: Long = 0L

  private def getEmptyConstructor: PsiMethod = {
    val curModCount = getManager.getModificationTracker.getOutOfCodeBlockModificationCount
    if (emptyObjectConstructor != null && emptyObjectConstructorModCount == curModCount) {
      return emptyObjectConstructor
    }
    val res = new EmptyPrivateConstructor(this)
    emptyObjectConstructorModCount = curModCount
    emptyObjectConstructor = res
    res
  }

  def getConstructors: Array[PsiMethod] = {
    Array(getEmptyConstructor)
  }

  def getInnerClasses: Array[PsiClass] = {
    definition match {
      case o: ScObject => Array.empty //todo:
      case _ => definition.getInnerClasses //todo:
    }
  }

  def getInitializers: Array[PsiClassInitializer] = Array.empty

  def getAllFields: Array[PsiField] = {
    PsiClassImplUtil.getAllFields(this)
  }

  def getAllMethods: Array[PsiMethod] = {
    PsiClassImplUtil.getAllMethods(this)
  }

  def getAllInnerClasses: Array[PsiClass] = {
    PsiClassImplUtil.getAllInnerClasses(this)
  }

  def findFieldByName(name: String, checkBases: Boolean): PsiField = {
    PsiClassImplUtil.findFieldByName(this, name, checkBases)
  }

  def findMethodBySignature(patternMethod: PsiMethod, checkBases: Boolean): PsiMethod = {
    PsiClassImplUtil.findMethodBySignature(this, patternMethod, checkBases)
  }

  def findMethodsBySignature(patternMethod: PsiMethod, checkBases: Boolean): Array[PsiMethod] = {
    PsiClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases)
  }

  def findMethodsByName(name: String, checkBases: Boolean): Array[PsiMethod] = {
    PsiClassImplUtil.findMethodsByName(this, name, checkBases)
  }

  def findMethodsAndTheirSubstitutorsByName(name: String, checkBases: Boolean): List[Pair[PsiMethod, PsiSubstitutor]] = {
    PsiClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases)
  }

  def getAllMethodsAndTheirSubstitutors: List[Pair[PsiMethod, PsiSubstitutor]] = {
    PsiClassImplUtil.getAllWithSubstitutorsByMap(this, classOf[PsiMethod])
  }

  def findInnerClassByName(name: String, checkBases: Boolean): PsiClass = {
    definition.findInnerClassByName(name, checkBases)
  }

  def getLBrace: PsiElement = {
    definition.getLBrace
  }

  def getRBrace: PsiElement = {
    definition.getRBrace
  }

  def getNameIdentifier: PsiIdentifier = {
    definition.getNameIdentifier
  }

  def getScope: PsiElement = {
    definition.getScope
  }

  def isInheritor(baseClass: PsiClass, checkDeep: Boolean): Boolean = {
    definition match {
      case o: ScObject =>
        baseClass.getQualifiedName == "java.lang.Object" || baseClass.getQualifiedName == "scala.ScalaObject"
      case _ => false
    }
  }

  def isInheritorDeep(baseClass: PsiClass, classToByPass: PsiClass): Boolean = {
    definition match {
      case o: ScObject =>
        baseClass.getQualifiedName == "java.lang.Object" || baseClass.getQualifiedName == "scala.ScalaObject"
      case _ => false
    }
  }

  def getContainingClass: PsiClass = {
    definition.getContainingClass
  }

  def getVisibleSignatures: Collection[HierarchicalMethodSignature] = {
    definition.getVisibleSignatures
  }

  def setName(name: String): PsiElement = {
    this.name = name
    val packageName = StringUtil.getPackageName(this.qualName)
    this.qualName = if (packageName.isEmpty) name else packageName + "." + name
    this
  }

  override def getName: String = name

  override def copy: PsiElement = {
    new PsiClassWrapper(definition.copy.asInstanceOf[ScTemplateDefinition], qualName, name)
  }

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement, place: PsiElement): Boolean = {
    if (!processor.isInstanceOf[BaseProcessor]) {
      return PsiClassImplUtil.processDeclarationsInClass(this, processor, state, null, lastParent, place, false)
    }
    true
  }

  override def getContainingFile: PsiFile = {
    definition.getContainingFile
  }

  override def isValid: Boolean = definition.isValid

  override def getNextSibling: PsiElement = definition.getNextSibling

  override def getPrevSibling: PsiElement = definition.getPrevSibling

  override def getContext: PsiElement = {
    definition.getContext
  }

  override def getParent: PsiElement = definition.getParent

  override def isPhysical: Boolean = {
    definition.isPhysical
  }

  override def getResolveScope: GlobalSearchScope = {
    definition.getResolveScope
  }

  override def getUseScope: SearchScope = {
    definition.getUseScope
  }

  override def toString: String = {
    "PsiClassWrapper(" + definition.toString + ")"
  }

  override def isEquivalentTo(another: PsiElement): Boolean = {
    another match {
      case wrapper: PsiClassWrapper =>
        wrapper.definition.isEquivalentTo(definition)
      case _ => false
    }
  }

  override def getIcon(flags: Int): Icon = {
    definition.getIcon(flags)
  }

  def getModifierList: PsiModifierList = {
    definition.getModifierList
  }

  def hasModifierProperty(name: String): Boolean = {
    definition.hasModifierProperty(name)
  }

  def getDocComment: PsiDocComment = {
    definition.getDocComment
  }

  def isDeprecated: Boolean = {
    definition.isDeprecated
  }

  override def getPresentation: ItemPresentation = {
    definition.getPresentation //todo: ?
  }

  override def navigate(requestFocus: Boolean) {
    definition.navigate(requestFocus)
  }

  override def canNavigate: Boolean = {
    definition.canNavigate
  }

  override def canNavigateToSource: Boolean = {
    definition.canNavigateToSource
  }

  def hasTypeParameters: Boolean = false

  def getTypeParameterList: PsiTypeParameterList = null

  def getTypeParameters: Array[PsiTypeParameter] = Array.empty
}

