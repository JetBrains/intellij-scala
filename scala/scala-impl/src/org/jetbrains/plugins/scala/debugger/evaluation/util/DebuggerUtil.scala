package org.jetbrains.plugins.scala.debugger.evaluation.util

import com.intellij.debugger.engine.{DebugProcess, DebugProcessImpl, JVMName, JVMNameUtil}
import com.intellij.debugger.{JavaDebuggerBundle, NoDataException, SourcePosition}
import com.intellij.lang.ASTNode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.sun.jdi.{Field, ObjectReference, ReferenceType}
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.debugger.ScalaPositionManager
import org.jetbrains.plugins.scala.debugger.evaluation.{EvaluationException, ScalaEvaluatorBuilderUtil}
import org.jetbrains.plugins.scala.debugger.filters.ScalaDebuggerSettings
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScCaseClause}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScalaConstructor, _}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScPackaging, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ValueClassType}
import org.jetbrains.plugins.scala.lang.psi.{ElementScope, ScalaPsiUtil}
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_12
import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectPsiElementExt}

import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.reflect.NameTransformer

/**
 * User: Alefas
 * Date: 19.10.11
 */
object DebuggerUtil {
  val packageSuffix = ".package$"

  class JVMNameBuffer {
    def append(evaluator: JVMName): Unit = {
      buffer += evaluator
    }

    def append(name: Char): Unit = {
      append(Character.toString(name))
    }

    def append(text: String): Unit = {
      buffer += JVMNameUtil.getJVMRawText(text)
    }

    def toName: JVMName = {
      new JVMName {
        private var myName: String = _
        override def getName(process: DebugProcessImpl): String = {
          if (myName == null) {
            var name: String = ""
            for (nameEvaluator <- buffer) {
              name += nameEvaluator.getName(process)
            }
            myName = name
          }
          myName
        }

        private var myDisplayName: String = _
        override def getDisplayName(debugProcess: DebugProcessImpl): String = {
          if (myDisplayName == null) {
            var displayName: String = ""
            for (nameEvaluator <- buffer) {
              displayName += nameEvaluator.getDisplayName(debugProcess)
            }
            myDisplayName = displayName
          }
          myDisplayName
        }
      }
    }

    private var buffer = new ArrayBuffer[JVMName]
  }

  def getJVMQualifiedName(tp: ScType): JVMName = {
    val stdTypes = tp.projectContext.stdTypes
    import stdTypes._

    tp match {
      case Any => JVMNameUtil.getJVMRawText("java.lang.Object")
      case Null => JVMNameUtil.getJVMRawText("scala.Null") //shouldn't be
      case AnyRef => JVMNameUtil.getJVMRawText("java.lang.Object") //shouldn't be
      case Nothing => JVMNameUtil.getJVMRawText("scala.Nothing") //shouldn't be
      case Singleton => JVMNameUtil.getJVMRawText("java.lang.Object")
      case AnyVal => JVMNameUtil.getJVMRawText("scala.AnyVal") //shouldn't be
      case Unit => JVMNameUtil.getJVMRawText("java.lang.Void")
      case Boolean => JVMNameUtil.getJVMRawText("java.lang.Boolean")
      case Char => JVMNameUtil.getJVMRawText("java.lang.Character")
      case Int => JVMNameUtil.getJVMRawText("java.lang.Int")
      case Long => JVMNameUtil.getJVMRawText("java.lang.Long")
      case Float => JVMNameUtil.getJVMRawText("java.lang.Float")
      case Double => JVMNameUtil.getJVMRawText("java.lang.Double")
      case Byte => JVMNameUtil.getJVMRawText("java.lang.Byte")
      case Short => JVMNameUtil.getJVMRawText("java.lang.Short")
      case JavaArrayType(argument) =>
        val buff = new JVMNameBuffer()
        buff.append(getJVMQualifiedName(argument))
        buff.append("[]")
        buff.toName
      case ParameterizedType(arr, Seq(arg)) if arr.extractClass.exists(_.qualifiedName == "scala.Array") =>
        val buff = new JVMNameBuffer()
        buff.append(getJVMQualifiedName(arg))
        buff.append("[]")
        buff.toName
      case _ =>
        tp.extractClass match {
          case Some(clazz) => getClassJVMName(clazz)
          case None => JVMNameUtil.getJVMRawText(tp.canonicalText)
        }
    }
  }

  def getJVMStringForType(tp: ScType, isParam: Boolean = true): String = {
    val stdTypes = tp.projectContext.stdTypes
    import stdTypes._

    tp match {
      case AnyRef => "Ljava/lang/Object;"
      case Any => "Ljava/lang/Object;"
      case Singleton => "Ljava/lang/Object;"
      case Null => "Lscala/Null$;"
      case Nothing => "Lscala/Nothing$;"
      case Boolean => "Z"
      case Byte => "B"
      case Char => "C"
      case Short => "S"
      case Int => "I"
      case Long => "J"
      case Float => "F"
      case Double => "D"
      case Unit if isParam => "Lscala/runtime/BoxedUnit;"
      case Unit => "V"
      case JavaArrayType(arg) => "[" + getJVMStringForType(arg)
      case ParameterizedType(ScDesignatorType(clazz: PsiClass), Seq(arg))
        if clazz.qualifiedName == "scala.Array" => "[" + getJVMStringForType(arg)
      case _ =>
        tp.extractClass match {
          case Some(obj: ScObject) => "L" + obj.getQualifiedNameForDebugger.replace('.', '/') + "$;"
          case Some(obj: ScTypeDefinition) => "L" + obj.getQualifiedNameForDebugger.replace('.', '/') + ";"
          case Some(clazz) => "L" + clazz.qualifiedName.replace('.', '/') + ";"
          case _ => "Ljava/lang/Object;"
        }
    }
  }

  def getFunctionJVMSignature(function: ScMethodLike): JVMName = {
    val typeParams = function match {
      case fun: ScFunction if !fun.isConstructor => fun.typeParameters
      case _: ScFunction | _: ScPrimaryConstructor =>
        function.containingClass match {
          case td: ScTypeDefinition => td.typeParameters
          case _ => Seq.empty
        }
      case _ => Seq.empty
    }
    val upperBounds = typeParams.map(_.upperBound.getOrAny)
    val subst = ScSubstitutor.bind(typeParams, upperBounds)
    val localParameters = function match {
      case fun: ScFunctionDefinition if fun.isLocal => localParamsForFunDef(fun)
      case ScalaConstructor.in(c: ScClass) =>
        localParamsForConstructor(c)
      case _ => Seq.empty
    }
    val valueClassParameter = function.containingClass match {
      case cl: ScClass if ValueClassType.isValueClass(cl) =>
        cl.constructors match {
          case Seq(pc: ScPrimaryConstructor) => pc.parameters.headOption
          case _ => None
        }
      case _ => None
    }
    val simpleParameters = function.effectiveParameterClauses.flatMap(_.effectiveParameters)
    val parameters = valueClassParameter ++: simpleParameters ++: localParameters
    val paramTypes = parameters.map(parameterForJVMSignature(_, subst)).mkString("(", "", ")")
    val resultType = function match {
      case fun: ScFunction if !fun.isConstructor =>
        getJVMStringForType(subst(fun.returnType.getOrAny), isParam = false)
      case _: ScFunction | _: ScPrimaryConstructor => "V"
    }
    JVMNameUtil.getJVMRawText(paramTypes + resultType)
  }

  def constructorSignature(named: PsiNamedElement): JVMName = {
    named match {
      case fun: ScFunction => getFunctionJVMSignature(fun)
      case constr: ScPrimaryConstructor =>
        constr.containingClass match {
          case td: ScTypeDefinition if td.isTopLevel => getFunctionJVMSignature(constr)
          case clazz => new JVMConstructorSignature(clazz)
        }
      case method: PsiMethod => JVMNameUtil.getJVMSignature(method)
      case clazz: ScClass if clazz.isTopLevel => clazz.constructor match {
        case Some(cnstr) => getFunctionJVMSignature(cnstr)
        case _ => JVMNameUtil.getJVMRawText("()V")
      }
      case clazz: ScClass => new JVMConstructorSignature(clazz)
      case _: PsiClass => JVMNameUtil.getJVMRawText("()V")
      case _ => JVMNameUtil.getJVMRawText("()V")
    }
  }

  def lambdaJVMSignature(lambda: PsiElement): Option[String] = {
    val (argumentTypes, returnType) = lambda match {
      case (expr: ScExpression) && Typeable(tp) if ScalaPsiUtil.isByNameArgument(expr) => (Seq.empty, tp)
      case Typeable(FunctionType(retT, argTypes)) => (argTypes, retT)
      case _ => return None
    }
    val trueReturnType = returnType match {
      case ValueClassType(inner) => inner
      case _ => returnType
    }

    val paramText = argumentTypes.map(getJVMStringForType(_, isParam = true)).mkString("(", "", ")")
    val returnTypeText = getJVMStringForType(trueReturnType, isParam = false)
    Some(paramText + returnTypeText)
  }

  private def parameterForJVMSignature(param: ScTypedDefinition, subst: ScSubstitutor) = param match {
      case p: ScParameter if p.isRepeatedParameter => "Lscala/collection/Seq;"
      case p: ScParameter if p.isCallByNameParameter => "Lscala/Function0;"
      case _ => getJVMStringForType(subst(param.`type`().getOrAny))
    }

  class JVMClassAt(sourcePosition: SourcePosition) extends JVMName {
    override def getName(process: DebugProcessImpl): String = {
      jvmClassAtPosition(sourcePosition, process) match {
        case Some(refType) => refType.name
        case _ =>
          throw EvaluationException(JavaDebuggerBundle.message("error.class.not.loaded", getDisplayName(process)))
      }
    }

    override def getDisplayName(debugProcess: DebugProcessImpl): String = {
      ApplicationManager.getApplication.runReadAction(new Computable[String] {
        override def compute: String = {
          JVMNameUtil.getSourcePositionClassDisplayName(debugProcess, sourcePosition)
        }
      })
    }
  }

  class JVMConstructorSignature(clazz: PsiClass) extends JVMName {
    val position: SourcePosition = SourcePosition.createFromElement(clazz)

    override def getName(process: DebugProcessImpl): String = {
      jvmClassAtPosition(position, process) match {
        case Some(refType) => refType.methodsByName("<init>").get(0).signature()
        case None =>
          throw EvaluationException(JavaDebuggerBundle.message("error.class.not.loaded", inReadAction(clazz.qualifiedName)))
      }
    }

    override def getDisplayName(debugProcess: DebugProcessImpl): String = getName(debugProcess)
  }

  def isScala(refType: ReferenceType, default: Boolean = true): Boolean = {
    ScalaPositionManager.cachedSourceName(refType).map(_.endsWith(".scala")).getOrElse(default)
  }

  def jvmClassAtPosition(sourcePosition: SourcePosition, debugProcess: DebugProcess): Option[ReferenceType] = {
    val allClasses = try {
      debugProcess.getPositionManager.getAllClasses(sourcePosition)
    } catch {
      case _: NoDataException => return None
    }

    if (!allClasses.isEmpty) Some(allClasses.get(0))
    else None
  }

  def withoutBackticks(name: String): String = {
    val backticked = """\$u0060(.+)\$u0060""".r
    name match {
      case null => null
      case backticked(id) => id
      case _ => name
    }
  }

  def getClassJVMName(clazz: PsiClass, withPostfix: Boolean = false): JVMName = {
    clazz match {
      case t: ScNewTemplateDefinition =>
        new JVMClassAt(SourcePosition.createFromElement(t))
      case t: ScTypeDefinition =>
        if (isLocalClass(t)) new JVMClassAt(SourcePosition.createFromElement(t))
        else {
          val qual = t.getQualifiedNameForDebugger + classnamePostfix(t, withPostfix)
          JVMNameUtil.getJVMRawText(qual)
        }
      case _ => JVMNameUtil.getJVMQualifiedName(clazz)
    }
  }

  def classnamePostfix(t: ScTemplateDefinition, withPostfix: Boolean = false): String = {
    t match {
      case _: ScTrait if withPostfix => "$class"
      case o: ScObject if withPostfix || o.isPackageObject => "$"
      case c: ScClass if withPostfix && ValueClassType.isValueClass(c) => "$" //methods from a value class always delegate to the companion object
      case _ => ""
    }
  }

  def getSourcePositions(elem: PsiElement, lines: mutable.Set[SourcePosition] = mutable.Set.empty): Set[SourcePosition] = {
    val node = elem.getNode
    val children: Array[ASTNode] = if (node != null) node.getChildren(null) else Array.empty[ASTNode]
    if (children.isEmpty) {
      val position = SourcePosition.createFromElement(elem)
      if (!lines.exists(_.getLine == position.getLine)) {
        lines += position
      }
    }
    for (child <- children) {
      getSourcePositions(child.getPsi, lines)
    }
    lines.toSet
  }

  def unwrapScalaRuntimeRef(value: AnyRef): AnyRef = {
    value match {
      case _ if !ScalaDebuggerSettings.getInstance().DONT_SHOW_RUNTIME_REFS => value
      case objRef: ObjectReference =>
        val refType = objRef.referenceType()
        if (isScalaRuntimeRef(refType.name))
          runtimeRefField(refType).map(objRef.getValue).getOrElse(objRef)
        else objRef
      case _ => value
    }
  }

  def isScalaRuntimeRef(typeFqn: String): Boolean = {
    typeFqn.startsWith("scala.runtime.") && typeFqn.endsWith("Ref")
  }

  object scalaRuntimeRefTo {
    def unapply(objRef: ObjectReference): Option[AnyRef] = {
      val typeName = objRef.referenceType().name()
      if (isScalaRuntimeRef(typeName)) Some(unwrapScalaRuntimeRef(objRef))
      else None
    }
  }

  def runtimeRefField(refType: ReferenceType): Option[Field] = {
    refType.fieldByName("elem").toOption
      .orElse(refType.fieldByName("_value").toOption)
  }

  def localParamsForFunDef(fun: ScFunctionDefinition, visited: mutable.Set[PsiElement] = mutable.Set.empty): Seq[ScTypedDefinition] = {
    val container = ScalaEvaluatorBuilderUtil.getContextClass(fun)
    fun.body match { //to exclude references from default parameters
      case Some(b) => localParams(b, fun, container, visited)
      case _ => Seq.empty
    } 
  }

  def localParamsForConstructor(cl: ScClass, visited: mutable.Set[PsiElement] = mutable.Set.empty): Seq[ScTypedDefinition] = {
    val container = ScalaEvaluatorBuilderUtil.getContextClass(cl)
    val extendsBlock = cl.extendsBlock //to exclude references from default parameters
    localParams(extendsBlock, cl, container, visited)
  }

  def localParamsForDefaultParam(param: ScParameter, visited: mutable.Set[PsiElement] = mutable.Set.empty): Seq[ScTypedDefinition] = {
    val owner = param.owner
    val container = ScalaEvaluatorBuilderUtil.getContextClass {
      owner match {
        case pc: ScPrimaryConstructor => pc.containingClass
        case owner /* function/funcexpr/extension */ => owner
      }
    }
    param.getDefaultExpression match {
      case Some(expr) => localParams(expr, owner, container, visited)
      case None => Seq.empty
    }
  }

  private def localParams(block: PsiElement, excludeContext: PsiElement, contextClass: PsiElement,
                          visited: mutable.Set[PsiElement] = mutable.Set.empty): Seq[ScTypedDefinition] = {
    def atRightPlace(elem: PsiElement): Boolean = {
      if (PsiTreeUtil.isContextAncestor(excludeContext, elem, false)) return false

      contextClass match {
        case (_: ScExpression) childOf ScFor(enumerators, _) if PsiTreeUtil.isContextAncestor(enumerators, elem, true) =>
          val generators = enumerators.generators
          if (generators.size <= 1) true
          else {
            val lastGenerator = generators.last
            elem.getTextOffset >= lastGenerator.getTextOffset
          }
        case _ => PsiTreeUtil.isContextAncestor(contextClass, elem, false)
      }
    }

    def isArgName(ref: ScReference): Boolean = ref match {
      case ChildOf(a @ ScAssignment(`ref`, _)) => a.isNamedParameter
      case _ => false
    }

    val builder = ArraySeq.newBuilder[ScTypedDefinition]
    block.accept(new ScalaRecursiveElementVisitor {
      override def visitReference(ref: ScReference): Unit = {
        if (ref.qualifier.isDefined || isArgName(ref)) {
          super.visitReference(ref)
          return
        }
        val elem = ref.resolve()
        elem match {
          case null =>
          case fun: ScFunctionDefinition if fun.isLocal && !visited.contains(fun) =>
            visited += fun
            builder ++= localParamsForFunDef(fun, visited).filter(atRightPlace)
          case ScalaConstructor(fun) if !visited.contains(fun) =>
            fun.containingClass match {
              case c: ScClass if isLocalClass(c) =>
                visited += c
                builder ++= localParamsForConstructor(c, visited).filter(atRightPlace)
              case _ =>
            }
          case td: ScTypedDefinition if isLocalV(td) && atRightPlace(td) =>
            builder += td
          case _ => super.visitReference(ref)
        }
      }
    })

    val result = builder.result().distinct
    if (isAtLeast212(block)) result.sortBy(e => e.is[ScObject])
    else result.sortBy(e => (e.is[ScObject], e.startOffset))
  }

  def isAtLeast212(element: PsiElement): Boolean =
    element.module.flatMap(_.scalaLanguageLevel).forall(_ >= Scala_2_12)

  def isLocalV(resolve: PsiElement): Boolean = {
    resolve match {
      case _: PsiLocalVariable => true
      case _: ScClassParameter => false
      case _: PsiParameter => true
      case b: ScBindingPattern =>
        ScalaPsiUtil.nameContext(b) match {
          case v @ (_: ScValue | _: ScVariable) =>
            !v.getContext.is[ScTemplateBody] && !v.getContext.is[ScEarlyDefinitions]
          case _: ScCaseClause => true
          case _ => true //todo: for generator/enumerators
        }
      case o: ScObject =>
        !o.getContext.is[ScTemplateBody] && ScalaPsiUtil.getContextOfType(o, true, classOf[PsiClass]) != null
      case _ => false
    }
  }

  def generatesAnonClass(newTd: ScNewTemplateDefinition): Boolean = {
    val extBl = newTd.extendsBlock
    extBl.templateBody.nonEmpty || extBl.templateParents.exists(_.typeElementsWithoutConstructor.nonEmpty)
  }

  @tailrec
  def isLocalClass(td: PsiClass): Boolean = td.getParent match {
    case _: ScTemplateBody =>
      td.parentOfType(classOf[PsiClass]) match {
        case Some(_: ScNewTemplateDefinition) | None => true
        case Some(clazz) => isLocalClass(clazz)
      }
    case _: ScPackaging | _: ScalaFile => false
    case _ => true
  }

  def getContainingMethod(elem: PsiElement): Option[PsiElement] = {
    elem.withParentsInFile.collectFirst {
      case c if ScalaPositionManager.isLambda(c) => c
      case m: PsiMethod => m
      case tb: ScTemplateBody => tb
      case ed: ScEarlyDefinitions => ed
      case ChildOf(f: ScalaFile) if f.isScriptFile => f
      case c: ScClass => c
    }
  }

  def inTheMethod(pos: SourcePosition, method: PsiElement): Boolean = {
    val elem: PsiElement = pos.getElementAt
    if (elem == null) return false
    getContainingMethod(elem).contains(method)
  }

  def getSignificantElement(elem: PsiElement): PsiElement = {
    elem match {
      case _: ScAnnotationsHolder | _: ScCommentOwner =>
        val firstSignificant = elem.children.find {
          case ElementType(t) if ScalaTokenTypes.WHITES_SPACES_AND_COMMENTS_TOKEN_SET.contains(t) => false
          case _: ScAnnotations => false
          case e if e.getTextLength == 0 => false
          case _ => true
        }
        firstSignificant.getOrElse(elem)
      case _ => elem
    }
  }

  def findClassesByQName(qName: String, elementScope: ElementScope, fallbackToProjectScope: Boolean): Seq[PsiClass] = {
    val cacheManager = ScalaShortNamesCacheManager.getInstance(elementScope.project)

    def classesInScope(scope: GlobalSearchScope): Seq[PsiClass] =
      if (qName.endsWith(packageSuffix))
        cacheManager.findPackageObjectByName(qName.stripSuffix(packageSuffix), scope).toSeq
      else
        cacheManager.getClassesByFQName(qName.replace(packageSuffix, "."), scope)

    val classes = classesInScope(elementScope.scope) match {
      case Seq() if fallbackToProjectScope => classesInScope(GlobalSearchScope.allScope(elementScope.project))
      case classes                         => classes
    }
    classes.filter(_.isValid)
  }

  def findPsiClassByQName(refType: ReferenceType, elementScope: ElementScope): Option[PsiClass] = {
    val originalQName = NameTransformer.decode(refType.name)
    val endsWithPackageSuffix = originalQName.endsWith(packageSuffix)
    val withoutSuffix =
      if (endsWithPackageSuffix) originalQName.stripSuffix(packageSuffix)
      else originalQName.stripSuffix("$").stripSuffix("$class")
    val withDots = withoutSuffix.replace(packageSuffix, ".").replace('$', '.')
    val transformed = if (endsWithPackageSuffix) withDots + packageSuffix else withDots

    val isScalaObject = originalQName.endsWith("$")
    val predicate: PsiClass => Boolean = {
      case _: ScObject => isScalaObject
      case _           => !isScalaObject
    }

    val classes = findClassesByQName(transformed, elementScope, fallbackToProjectScope = true)

    classes.find(predicate)
      .orElse(classes.headOption)
  }
}
