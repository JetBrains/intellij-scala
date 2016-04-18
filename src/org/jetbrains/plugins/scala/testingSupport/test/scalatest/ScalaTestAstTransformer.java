package org.jetbrains.plugins.scala.testingSupport.test.scalatest;

import com.intellij.execution.Location;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructor;
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral;
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern;
import org.jetbrains.plugins.scala.lang.psi.api.expr.*;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition;
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.*;
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScArgumentExprListImpl;
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScBlockExprImpl;
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.MixinNodes;
import org.jetbrains.plugins.scala.lang.psi.types.ScType;
import org.jetbrains.plugins.scala.lang.psi.types.api.ExtractClass$;
import org.jetbrains.plugins.scala.testingSupport.test.TestConfigurationUtil;
import org.scalatest.finders.AstNode;
import org.scalatest.finders.Finder;
import org.scalatest.finders.Selection;
import scala.Option;
import scala.collection.JavaConversions;
import scala.collection.Seq;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * @author Ksenia.Sautina
 * @since 8/6/12
 */

public class ScalaTestAstTransformer {

    public static Logger LOG = Logger.getInstance("org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestAstTransformer");

    protected static final List<String> itWordFqns = new LinkedList<String>();
    static {
        itWordFqns.add("org.scalatest.FlatSpecLike.ItWord");
        itWordFqns.add("org.scalatest.FunSpecLike.ItWord");
        itWordFqns.add("org.scalatest.WordSpecLike.ItWord");
        itWordFqns.add("org.scalatest.fixture.FlatSpecLike.ItWord");
        itWordFqns.add("org.scalatest.fixture.FunSpecLike.ItWord");
        itWordFqns.add("org.scalatest.fixture.WordSpecLike.ItWord");
        itWordFqns.add("org.scalatest.path.FunSpecLike.ItWord");
    }

    public Class<?> loadClass(String className, Module module) throws MalformedURLException, ClassNotFoundException {
        final List<OrderEntry> orderEntries = new ArrayList<OrderEntry>();
        OrderEnumerator.orderEntries(module).recursively().runtimeOnly().forEach(new Processor<OrderEntry>() {
            @Override
            public boolean process(OrderEntry orderEntry) {
                orderEntries.add(orderEntry);
                return true;
            }
        });

        List<URL> loaderUrls = new ArrayList<URL>();
        for (OrderEntry entry : orderEntries) {
            List<VirtualFile> virtualFiles = new ArrayList<VirtualFile>(Arrays.asList(entry.getFiles(OrderRootType.CLASSES)));
            List<String> rawUrls = new ArrayList<String>();
            for (VirtualFile vf : virtualFiles) {
                rawUrls.add(vf.getPresentableUrl());
            }
            for (String rawUrl : rawUrls) {
                File cpFile = new File(rawUrl);
                if (cpFile.exists() && cpFile.isDirectory() && !rawUrl.endsWith(File.separator)) {
                    loaderUrls.add(new URL("file:/" + rawUrl + "/"));
                } else {
                    loaderUrls.add(new URL("file:/" + rawUrl));
                }
            }
        }

        URL[] loaderUrlsArray = new URL[loaderUrls.size()];
        loaderUrls.toArray(loaderUrlsArray);

        URLClassLoader loader = new URLClassLoader(loaderUrlsArray, getClass().getClassLoader());
        return loader.loadClass(className);
    }

    @Nullable
    protected String getNameFromAnnotLiteral(@Nullable ScExpression expr) {
        if (expr == null) return null;
        if (expr instanceof ScLiteral && ((ScLiteral)expr).isString()) {
            Object value2 = ((ScLiteral) expr).getValue();
            if (value2 instanceof String) {
                return (String) value2;
            }
        }
        return null;
    }

    @Nullable
    protected String getNameFromAnnotAssign(@NotNull ScAssignStmt assignStmt) {
        if (assignStmt.getLExpression() instanceof ScReferenceExpression &&
                ((ScReferenceExpression) assignStmt.getLExpression()).refName().equals("value")) {
            ScExpression expr = assignStmt.getRExpression().get();
            if (expr != null) {
                if (expr instanceof ScMethodCall) {
                    ScMethodCall methodCall = (ScMethodCall) expr;
                    ScExpression invokedExpr = methodCall.getInvokedExpr();
                    if (invokedExpr instanceof ScReferenceExpression &&
                            ((ScReferenceExpression)invokedExpr).refName().equals("Array")) {
                        ScArgumentExprList constructorArgs = methodCall.args();
                        ScExpression[] argExprs = constructorArgs.exprsArray();
                        if (constructorArgs.invocationCount() == 1 && argExprs.length == 1) {
                            expr = argExprs[0];
                        }
                    }
                }
                return getNameFromAnnotLiteral(expr);
            }
        }
        return null;
    }

    protected String getFinderClassFqn(ScTypeDefinition suiteTypeDef, Module module, String... annotationFqns) {
        String finderClassName = null;
        Annotation[] annotations = null;
        for (String annotationFqn: annotationFqns) {
            Option<ScAnnotation> annotationOption = suiteTypeDef.hasAnnotation(annotationFqn);
            if (annotationOption.isDefined() && annotationOption.get() != null) {
                ScAnnotation styleAnnotation = annotationOption.get();
                try {
                    ScConstructor constructor = (ScConstructor) styleAnnotation.getClass().getMethod("constructor").invoke(styleAnnotation);
                    if (constructor != null) {
                        ScArgumentExprList args = constructor.args().isDefined() ? constructor.args().get() : null;
                        ScAnnotationExpr annotationExpr = styleAnnotation.annotationExpr();
                        List<ScNameValuePair> valuePairs = JavaConversions.seqAsJavaList(annotationExpr.getAttributes());
                        if (args == null && !valuePairs.isEmpty()) {
                            finderClassName = valuePairs.get(0).getLiteralValue();
                        } else if (args != null) {
                            List<ScExpression> exprs = JavaConversions.seqAsJavaList(args.exprs());
                            if (exprs.size() > 0) {
                                ScExpression expr = exprs.get(0);
                                if (expr instanceof ScAssignStmt) {
                                    finderClassName = getNameFromAnnotAssign((ScAssignStmt) expr);
                                } else {
                                    finderClassName = getNameFromAnnotLiteral(expr);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    LOG.debug("Failed to extract finder class name from annotation " + styleAnnotation + ":\n" + e);
                }
                if (finderClassName != null) return finderClassName;
                //the annotation is present, but arguments are not: have to load a Class, not PsiClass, in order to extract finder FQN
                if (annotations == null) {
                    try {
                        Class suiteClass = loadClass(suiteTypeDef.qualifiedName(), module);
                        annotations = suiteClass.getAnnotations();
                    } catch (Exception e) {
                        LOG.debug("Failed to load suite class " + suiteTypeDef.qualifiedName());
                    }
                }
                if (annotations != null) {
                    for (Annotation a : annotations) {
                        if (a.annotationType().getName().equals(annotationFqn)) {
                            try {
                                Method valueMethod = a.annotationType().getMethod("value");
                                String[] args = ((String[]) valueMethod.invoke(a));
                                if (args.length != 0) {
                                    return args[0];
                                }
                            } catch (Exception e) {
                                LOG.debug("Failed to extract finder class name from annotation " + styleAnnotation + ":\n" + e);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public Finder getFinder(ScClass clazz, Module module) throws MalformedURLException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, InstantiationException {
        Seq<ScType> classes = MixinNodes.linearization(clazz);
        List<ScType> list = JavaConversions.seqAsJavaList(classes);
        List<PsiClass> newList = new ArrayList<PsiClass>();
        for (ScType type : list) {
            PsiClass c = ExtractClass$.MODULE$.unapply(type, clazz.getProject()).get();
            if (c != null) {
                newList.add(c);
            }
        }
        for (PsiClass clazzz : newList) {
            if (clazzz instanceof ScClass || clazzz instanceof ScTrait) {
                ScTypeDefinition typeDef = (ScTypeDefinition) clazzz;
                String finderFqn = getFinderClassFqn(typeDef, module, "org.scalatest.Style", "org.scalatest.Finders");
                if (finderFqn != null) {
                    try {
                        Class finderClass = Class.forName(finderFqn);
                        return (Finder) finderClass.newInstance();
                    } catch (ClassNotFoundException e) {
                        LOG.debug("Failed to load finders API class " + finderFqn);
                    }
                }
            }
        }
        return null;
    }

    class StConstructorBlock extends org.scalatest.finders.ConstructorBlock {
        public String pClassName;
        public PsiElement element;

        public StConstructorBlock(String pClassName, PsiElement element) {
            super(pClassName, null, new AstNode[0]);
            this.element = element;
            this.pClassName = pClassName;
        }

        @Override
        public AstNode[] children() {
            return getChildren(pClassName, element);
        }

        @Override
        public boolean equals(Object other) {
            return other != null && other instanceof StConstructorBlock && element.equals(((StConstructorBlock) other).element);
        }

        @Override
        public int hashCode() {
            return element.hashCode();
        }
    }

    class StMethodDefinition extends org.scalatest.finders.MethodDefinition {
        public final String pClassName;
        public final PsiElement element;

        public StMethodDefinition(String pClassName, PsiElement element, String... pParamTypes) {
            super(pClassName, null, new AstNode[0], TestConfigurationUtil.getStaticTestNameOrDefault(element, "", false), pParamTypes);
            this.pClassName = pClassName;
            this.element = element;
        }

        @Override
        public AstNode parent() {
            return getParentNode(className(), element);
        }

        @Override
        public AstNode[] children() {
            return getChildren(pClassName, element);
        }

        @Override
        public boolean canBePartOfTestName() {
            return TestConfigurationUtil.getStaticTestName(element, false).isDefined();
        }

        @Override
        public boolean equals(Object other) {
            return other != null && other instanceof StMethodDefinition &&
                    element.equals(((StMethodDefinition) other).element);
        }

        @Override
        public int hashCode() {
            return element.hashCode();
        }
    }


    private class StMethodInvocation extends org.scalatest.finders.MethodInvocation {
        public final String pClassName;
        public final AstNode pTarget;
        public final MethodInvocation invocation;
        public final PsiElement nameSource;

        public StMethodInvocation(String pClassName, AstNode pTarget, MethodInvocation invocation, String pName, PsiElement nameSource, AstNode... args) {
            super(pClassName, pTarget, null, new AstNode[0], pName, args);
            this.pClassName = pClassName;
            this.pTarget = pTarget;
            this.invocation = invocation;
            this.nameSource = nameSource;
        }

        @Override
        public AstNode parent() {
            return getParentNode(pClassName, invocation);
        }

        @Override
        public AstNode[] children() {
            return getChildren(pClassName, invocation);
        }

        private PsiElement closestInvocationElement() {
            return PsiTreeUtil.getParentOfType(nameSource, MethodInvocation.class);
        }

        @Override
        public boolean canBePartOfTestName() {
            return super.canBePartOfTestName() && TestConfigurationUtil.getStaticTestName(closestInvocationElement(), false).isDefined();
        }

        @Override
        public boolean equals(Object other) {
            return other != null && other instanceof StMethodInvocation &&
                    invocation.equals(((StMethodInvocation) other).invocation);
        }

        @Override
        public int hashCode() {
            return invocation.hashCode();
        }

        @Override
        public String toString() {
            return TestConfigurationUtil.getStaticTestNameOrDefault(closestInvocationElement(), name(), false);
        }
    }


    private class StStringLiteral extends org.scalatest.finders.StringLiteral {
        public String pClassName;
        public PsiElement element;
        public String pValue;

        public StStringLiteral(String pClassName, PsiElement element, String pValue) {
            super(pClassName, null, pValue);
            this.pClassName = pClassName;
            this.element = element;
            this.pValue = pValue;
        }

        @Override
        public AstNode parent() {
            return getParentNode(pClassName, element);
        }

        @Override
        public boolean equals(Object other) {
            return other != null && other instanceof StStringLiteral &&
                    element.equals(((StStringLiteral) other).element);
        }

        @Override
        public int hashCode() {
            return element.hashCode();
        }
    }

    private class StToStringTarget extends org.scalatest.finders.ToStringTarget {
        public String pClassName;
        public PsiElement element;
        public Object target;

        public StToStringTarget(String pClassName, PsiElement element, Object target) {
            super(pClassName, null, new AstNode[0], target);
            this.pClassName = pClassName;
            this.element = element;
            this.target = target;
        }

        protected boolean isIt() {
            return element instanceof ScReferenceExpression && target.equals("it") &&
                    itWordFqns.contains(pClassName);
        }

        @Override
        public AstNode parent() {
            return getParentNode(pClassName, element);
        }


        @Override
        public AstNode[] children() {
            return getChildren(pClassName, element);
        }

        @Override
        public boolean canBePartOfTestName() {
            return isIt() || TestConfigurationUtil.getStaticTestName(element, false).isDefined();
        }

        @Override
        public String toString() {
            if (isIt()) {
                return "";
            } else {
                return TestConfigurationUtil.getStaticTestNameOrDefault(element, name(), false);
            }
        }

        @Override
        public boolean equals(Object other) {
            return other != null && other instanceof StToStringTarget &&
                    element.equals(((StToStringTarget) other).element);
        }

        @Override
        public int hashCode() {
            return element.hashCode();
        }
    }

    public AstNode getSelectedAstNode(String className, PsiElement element) {
        AstNode astNodeOpt = transformNode(className, element);
        if (astNodeOpt != null) {
            return astNodeOpt;
        } else {
            PsiElement parent = element.getParent();
            if (parent == null)
                return null;
            else
                return getSelectedAstNode(className, parent);
        }
    }

    public AstNode getTarget(String className, PsiElement element, MethodInvocation selected) {
        PsiElement firstChild = element.getFirstChild();
        if (firstChild instanceof ScLiteral && (((ScLiteral) firstChild).isString())) {
            return new StToStringTarget(className, firstChild, ((ScLiteral)firstChild).getValue().toString());
        } else if (firstChild instanceof MethodInvocation) {
            StMethodInvocation inv = getScalaTestMethodInvocation(selected, (MethodInvocation) firstChild, Collections.<ScExpression>emptyList(), className);
            if (inv != null) {
                return inv;
            } else {
                return new StToStringTarget(className, firstChild, firstChild.getText());
            }
        } else {
            return new StToStringTarget(className, firstChild, firstChild.getText());
        }
    }

    public StMethodInvocation getScalaTestMethodInvocation(MethodInvocation selected, MethodInvocation current,
                                                           List<ScExpression> currentParamsExpr, String className) {
        List<ScExpression> paramsExpr = new ArrayList<ScExpression>();
        paramsExpr.addAll(JavaConversions.seqAsJavaList(current.argumentExpressions()));
        paramsExpr.addAll(currentParamsExpr);
        if (current.getInvokedExpr() instanceof ScReferenceExpression) {
            AstNode target = getTarget(className, current, selected);
            ScReferenceExpression ref = (ScReferenceExpression) current.getInvokedExpr();
            PsiElement resolve = ref.resolve();
            String containingClassName;
            if (resolve != null) {
                if (resolve instanceof ScMember) {
                    ScTemplateDefinition containingClass = ((ScMember) resolve).containingClass();
                    containingClassName = containingClass == null ? null : containingClass.qualifiedName();
                } else if (resolve instanceof ScBindingPattern) {
                    PsiElement context = ((ScBindingPattern) resolve).nameContext();
                    if (context instanceof ScMember) {
                        ScTemplateDefinition containingClass = ((ScMember) context).containingClass();
                        containingClassName = containingClass == null ? null : containingClass.qualifiedName();
                    } else {
                        containingClassName = null;
                    }
                } else {
                    containingClassName = null;
                }
            } else {
                containingClassName = null;
            }
            List<AstNode> args = new ArrayList<AstNode>();
            for (ScExpression expr : paramsExpr) {
                if (expr instanceof ScLiteral && ((ScLiteral) expr).isString()) {
                    args.add(new StStringLiteral(containingClassName, expr, ((ScLiteral) expr).getValue().toString()));
                } else {
                    args.add(new StToStringTarget(containingClassName, expr, expr.getText()));
                }

            }
            String pName = (current.isApplyOrUpdateCall()) ? "apply" : ref.refName();
            PsiElement nameSource = (current.isApplyOrUpdateCall()) ? null : ref;
            AstNode[] array = new AstNode[args.size()];
            args.toArray(array);
            return new StMethodInvocation(containingClassName, target, selected, pName, nameSource, array);
        } else if (current.getInvokedExpr() instanceof MethodInvocation) {
            return getScalaTestMethodInvocation(selected, ((MethodInvocation) current.getInvokedExpr()), paramsExpr, className);
        } else {
            return null;
        }
    }

    public StMethodDefinition getScalaTestMethodDefinition(ScFunctionDefinition methodDef) {
        ScTemplateDefinition containingClass = methodDef.containingClass();
        if (containingClass != null) {
            // For inner method, this will be null
            String className = containingClass.qualifiedName();
            String name = methodDef.name();
            List<String> paramTypes = new ArrayList<String>();
            for (ScParameter param : JavaConversions.seqAsJavaList(methodDef.parameters())) {
                paramTypes.add(param.getType().getCanonicalText());
            }
            String[] array = new String[paramTypes.size()];
            paramTypes.toArray(array);
            return new StMethodDefinition(className, methodDef, array);
        } else {
            return null;      // May be to build the nested AST nodes too.
        }
    }

    public AstNode transformNode(String className, PsiElement element) {
        if (element instanceof MethodInvocation) {
            MethodInvocation invocation = (MethodInvocation) element;
            return getScalaTestMethodInvocation(invocation, invocation, Collections.<ScExpression>emptyList(), className);
        } else if (element instanceof ScFunctionDefinition) {
            return getScalaTestMethodDefinition((ScFunctionDefinition) element);
        } else if (element instanceof ScTemplateBody) {
            return new StConstructorBlock(className, element);
        } else {
            return null;
        }
    }

    public Selection testSelection(Location<? extends PsiElement> location) {
        PsiElement element = location.getPsiElement();
        ScClass clazz = PsiTreeUtil.getParentOfType(element, ScClass.class, false);
        if (clazz == null) return null;
        Finder finder = null;
        try {
            finder = getFinder(clazz, location.getModule());
        } catch (Exception e) {
            LOG.debug("Failed to load scalatest-finders API class for test sute " + clazz.qualifiedName()
                    + ": " + e.getMessage());
        }
        if (finder != null) {
            AstNode selectedAst = getSelectedAstNode(clazz.qualifiedName(), element);
            AstNode selectedAstOpt = (selectedAst == null) ? null : selectedAst;
            if (selectedAstOpt != null) {
                //TODO add logging here
                /*selectedAst match {
                  case org.scalatest.finders.MethodInvocation(className, target, parent, children, name, args) =>
                    println("######parent: " + parent.getClass.getName)
                  case _ =>
                    println("######Other!!")
                }*/
                Selection selection = finder.find(selectedAstOpt);
                /*selectionOpt match {
                  case Some(selection) =>
                    println("***Test Found, display name: " + selection.displayName() + ", test name(s):")
                    selection.testNames.foreach(println(_))
                  case None =>
                    println("***Test Not Found!!")
                }*/
                return selection;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public AstNode getParentNode(String className, PsiElement element) {
        PsiElement elementParent = element.getParent();
        if (elementParent == null) {
            return null;
        } else {
            AstNode parentOpt = transformNode(className, elementParent);
            if (parentOpt != null) {
                return parentOpt;
            } else {
                return getParentNode(className, elementParent);
            }
        }
    }

    public PsiElement getTopInvocation(MethodInvocation element) {
        PsiElement invocationParent = element.getParent();
        if (invocationParent instanceof MethodInvocation) {
            return getTopInvocation((MethodInvocation) invocationParent);
        } else {
            return element;
        }
    }

    List<PsiElement> getElementNestedBlockChildren(PsiElement element) {
        if (element instanceof ScBlockExpr || element instanceof ScTemplateBody) {
            return Arrays.asList(element.getChildren());
        } else {
            List<PsiElement> nestedChildren = new ArrayList<PsiElement>();
            PsiElement[] children = element.getChildren();
            for (int i = 0; i < children.length; i++) {
                PsiElement child = children[i];
                if (child instanceof ScArgumentExprListImpl) {
                    ScArgumentExprListImpl argExprList = (ScArgumentExprListImpl) child;
                    PsiElement[] aelChildren = argExprList.getChildren();
                    if (aelChildren.length > 0 && aelChildren[0] instanceof ScBlockExpr) {
                        ScBlockExpr blockExpr = (ScBlockExpr) aelChildren[0];
                        nestedChildren.addAll(Arrays.asList(blockExpr.getChildren()));
                    }
                } else if (child instanceof ScBlockExprImpl) {
                    ScBlockExprImpl blockExpr = (ScBlockExprImpl) child;
                    nestedChildren.addAll(Arrays.asList(blockExpr.getChildren()));
                } else if (child instanceof ScReferenceExpression) {
                    ScReferenceExpression refExpr = (ScReferenceExpression) child;
                    if (refExpr.getParent() instanceof MethodInvocation) {
                        MethodInvocation refExprParentInvocation = (MethodInvocation) refExpr.getParent();
                        nestedChildren.addAll(Arrays.asList(getTopInvocation(refExprParentInvocation).getLastChild().getLastChild().getChildren()));
                    }
                }
            }
            return nestedChildren;
        }
    }

    public AstNode[] getChildren(String className, PsiElement element) {
        List<PsiElement> nestedChildren = getElementNestedBlockChildren(element);
        List<AstNode> result = new ArrayList<AstNode>();
        for (PsiElement child : nestedChildren) {
            AstNode parentOpt = transformNode(className, child);
            if (parentOpt != null) {
                result.add(parentOpt);
            }
        }
        AstNode[] resultArray = new AstNode[result.size()];
        result.toArray(resultArray);
        return resultArray;
    }
}
