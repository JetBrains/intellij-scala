package org.jetbrains.plugins.scala.lang.psi.impl.compiled

import api.base.ScModifierList
import api.statements.{ScFunction, ScTypeAlias}
import api.toplevel.templates.ScExtendsBlock
import api.toplevel.typedef.{ScTypeDefinition, ScMember}
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.impl.compiled.{ClsRepositoryPsiElement, ClsClassImpl}
import com.intellij.psi.impl.source.SourceTreeToPsiMap
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.meta.PsiMetaData
import com.intellij.util.ArrayUtil
import icons.Icons
import parser.ScalaElementTypes
import stubs.impl.ScExtendsBlockStubImpl
import stubs.ScTypeDefinitionStub
import types.{ScType, PhysicalSignature}
import com.intellij.psi._

import _root_.java.util.Collection;
import _root_.java.util.Collections;
import _root_.java.util.List;

/**
 * @author ilyas
 */

class ScClsTypeDefinitionImpl(stub: ScTypeDefinitionStub)
extends ClsClassImpl(stub) with ScTypeDefinition {

  object ClassTypes extends Enumeration {
    type CLASS_TYPE = Value
    val CLASS, TRAIT, OBJECT = Value
  }
  import ClassTypes._

  private def _type = {
    import ScalaElementTypes._
    getStub.getStubType match {
      case TRAIT_DEF => TRAIT
      case CLASS_DEF => CLASS
      case OBJECT_DEF => OBJECT
    }
  }

  override def getLanguage: Language = ScalaFileType.SCALA_LANGUAGE

  def aliases(): Seq[ScTypeAlias] = Seq.empty
  def members(): Seq[ScMember] = Seq.empty
  def allVals(): Iterator[Nothing] = Iterator.empty
  def innerTypeDefinitions(): Seq[ScTypeDefinition] = Seq.empty
  def superTypes(): Seq[ScType] = Seq.empty
  def allMethods(): Iterator[PhysicalSignature] = Iterator.empty
  def addMember(meth: PsiElement, editor: Option[Editor], offset: Int) = null
  def functions(): Seq[ScFunction] = Seq.empty
  def allTypes(): Iterator[Nothing] = Iterator.empty
  def functionsByName(name: String): Iterable[PsiMethod] = Seq.empty

  protected def findChildrenByClass[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] = Array[T]()
  protected def findChildByClass[T >: Null <: ScalaPsiElement](clazz: Class[T]): T = null
  def nameId(): PsiElement = getNameIdentifier

  override def getIconInner = _type match {
    case CLASS => Icons.CLASS
    case OBJECT => Icons.OBJECT
    case TRAIT => Icons.TRAIT
  }

  def extendsBlock(): ScExtendsBlock = {
    val ebStub = new ScExtendsBlockStubImpl(getStub, ScalaElementTypes.EXTENDS_BLOCK)
    new ScClsExtendsBlockImpl(ebStub)
  }

  /*
    public String getSourceFileName() {
      final String sfn = getStub().getSourceFileName();
      return sfn != null ? sfn : obtainSourceFileNameFromClassFileName();
    }
  */

  override def getSourceFileName: String = CompiledUtil.getSourceFileName(getStub.getSourceFileName) match {
    case Some(s) => s
    case None => super.getSourceFileName
  }

  override def appendMirrorText(indentLevel: Int, buffer: StringBuffer): Unit = {
    val clazzName = getStub.getName
    import ScalaElementTypes._

    val text = (getStub.getStubType match {
      case ScalaElementTypes.CLASS_DEF => "class"
      case ScalaElementTypes.OBJECT_DEF => "object"
      case _ => "trait"
    }) + " " + clazzName + " {\n  // Under construction...\n}"
    buffer.append(text)
  }

  override def setMirror(element: TreeElement) {
    val mirror: PsiClass = SourceTreeToPsiMap.treeElementToPsi(element).asInstanceOf[PsiClass]

    /* java legacy
        final PsiDocComment docComment = getDocComment();
        if (docComment != null) {
            ((ClsElementImpl)docComment).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getDocComment()));
        }
          ((ClsElementImpl)getModifierList()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getModifierList()));
          ((ClsElementImpl)getNameIdentifier()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getNameIdentifier()));
        if (!isAnnotationType() && !isEnum()) {
            ((ClsElementImpl)getExtendsList()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getExtendsList()));
        }
          ((ClsElementImpl)getImplementsList()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getImplementsList()));
          ((ClsElementImpl)getTypeParameterList()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getTypeParameterList()));
    */

    /*todo implement me!
        PsiField[] fields = getFields();
        PsiField[] mirrorFields = mirror.getFields();
        if (LOG.assertTrue(fields.length == mirrorFields.length)) {
          for (int i = 0; i < fields.length; i++) {
              ((ClsElementImpl)fields[i]).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirrorFields[i]));
          }
        }

        PsiMethod[] methods = getMethods();
        PsiMethod[] mirrorMethods = mirror.getMethods();
        if (LOG.assertTrue(methods.length == mirrorMethods.length)) {
          for (int i = 0; i < methods.length; i++) {
              ((ClsElementImpl)methods[i]).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirrorMethods[i]));
          }
        }
    */

    /*todo implement me!
        PsiClass[] classes = getInnerClasses();
        PsiClass[] mirrorClasses = mirror.getInnerClasses();
        if (LOG.assertTrue(classes.length == mirrorClasses.length)) {
          for (int i = 0; i < classes.length; i++) {
              ((ClsElementImpl)classes[i]).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirrorClasses[i]));
          }
        }
    */
    ;
  }

  /***********************************************  Stub methods ************************************/

  override def getExtendsList = null

  override def name = getStub.getName

  override def getQualifiedName(): String = getStub.getQualifiedName

  override def toString = "CompiledScalaClass: [" + getStub.getName + "]"

  override def getExtendsListTypes: Array[PsiClassType] = PsiClassType.EMPTY_ARRAY

  override def getImplementsListTypes: Array[PsiClassType] = PsiClassType.EMPTY_ARRAY

  override def getSuperClass: PsiClass = null

  override def getInterfaces: Array[PsiClass] = PsiClass.EMPTY_ARRAY

  override def getImplementsList: PsiReferenceList = null

  override def getSupers: Array[PsiClass] = PsiClass.EMPTY_ARRAY

  override def getSuperTypes: Array[PsiClassType] = PsiClassType.EMPTY_ARRAY

  override def getFields: Array[PsiField] = PsiField.EMPTY_ARRAY // todo

  override def getConstructors: Array[PsiMethod] = PsiMethod.EMPTY_ARRAY // todo

  override def getInnerClasses: Array[PsiClass] = PsiClass.EMPTY_ARRAY // todo

  override def getInitializers: Array[PsiClassInitializer] = PsiClassInitializer.EMPTY_ARRAY

  override def getAllFields: Array[PsiField] = getFields

  override def getAllMethods: Array[PsiMethod] = getMethods

  override def getAllInnerClasses: Array[PsiClass] = getInnerClasses

  override def findFieldByName(name: String, checkBases: Boolean): PsiField = null

  override def findMethodBySignature(patternMethod: PsiMethod, checkBases: Boolean): PsiMethod = null

  override def findMethodsBySignature(patternMethod: PsiMethod, checkBases: Boolean): Array[PsiMethod] = PsiMethod.EMPTY_ARRAY

  override def findInnerClassByName(name: String, checkBases: Boolean): PsiClass = null

  override def getLBrace: PsiJavaToken = null

  override def getRBrace: PsiJavaToken = null

  override def getNameIdentifier: PsiIdentifier = null

  override def getScope: PsiElement = null

  override def isInheritor(baseClass: PsiClass, checkDeep: Boolean): Boolean = false

  override def isInheritorDeep(baseClass: PsiClass, classToPass: PsiClass): Boolean = false

  override def getVisibleSignatures: Collection[HierarchicalMethodSignature] = Collections.emptyList[HierarchicalMethodSignature]

  override def getModifierList: ScModifierList = null

  override def hasModifierProperty(name: String): Boolean = name.equals(PsiModifier.PUBLIC)

  override def getDocComment: PsiDocComment = null

  override def isDeprecated: Boolean = false

  override def getMetaData: PsiMetaData = null

  override def hasTypeParameters: Boolean = false

  override def getTypeParameterList: PsiTypeParameterList = null

  override def getTypeParameters: Array[PsiTypeParameter] = PsiTypeParameter.EMPTY_ARRAY

  override def findMethodsByName(name: String, checkBases: Boolean): Array[PsiMethod] = Array[PsiMethod]()

  override def getMethods() = Array[PsiMethod]()


  override def getContainingClass(): PsiClass = null
}