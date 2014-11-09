package org.jetbrains.plugins.scala.testingSupport.test.scalatest;

import com.intellij.execution.Location;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
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
import org.jetbrains.plugins.scala.lang.psi.types.ScType$;
import org.scalatest.finders.AstNode;
import org.scalatest.finders.Finder;
import org.scalatest.finders.Selection;
import org.scalatest.finders.ToStringTarget;
import scala.Option;
import scala.Option$;
import scala.collection.JavaConversions;
import scala.collection.Seq;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Ksenia.Sautina
 * @since 8/6/12
 */

public class ScalaTestAstTransformer {
    public Class<?> loadClass(String className, Module module) throws MalformedURLException {
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
                if (cpFile.exists() && cpFile.isDirectory() && !rawUrl.toString().endsWith(File.separator)) {
                    loaderUrls.add(new URL("file://" + rawUrl + "/"));
                } else {
                    loaderUrls.add(new URL("file://" + rawUrl));
                }
            }
        }

        URL[] loaderUrlsArray = new URL[loaderUrls.size()];
        loaderUrls.toArray(loaderUrlsArray);

        URLClassLoader loader = new URLClassLoader(loaderUrlsArray, getClass().getClassLoader());
        try {
            Class clazz = loader.loadClass(className);
            return clazz;
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public ScTypeDefinition clazzWithStyleOpt(ScClass clazz) {
        List<ScType> list = (List) MixinNodes.linearization(clazz);
        for (ScType t : list) {
            Option<PsiClass> tp = ScType$.MODULE$.extractClass(t, Option$.MODULE$.apply(clazz.getProject()));
            if ((tp instanceof ScClass) && (((ScClass) tp).hasAnnotation("org.scalatest.Style").get() != null)) {
                return (ScClass) tp;
            }
            if ((tp instanceof ScTrait) && (((ScTrait) tp).hasAnnotation("org.scalatest.Style").get() != null)) {
                return (ScTrait) tp;
            }
        }
        return null;
    }

    public Finder getFinder(ScClass clazz, Module module) throws MalformedURLException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, InstantiationException {
        Seq<ScType> classes = MixinNodes.linearization(clazz);
        List<ScType> list = JavaConversions.seqAsJavaList(classes);
        List<PsiClass> newList = new ArrayList<PsiClass>();
        for (ScType type : list) {
            PsiClass c = ScType$.MODULE$.extractClass(type, Option$.MODULE$.apply(clazz.getProject())).get();
            if (c != null) {
                newList.add(c);
            }
        }
        PsiClass classWithStyle = null;
        for (PsiClass clazzz : newList) {
            if (clazzz instanceof ScClass) {
                ScClass scClass = (ScClass) clazzz;
                if (scClass.hasAnnotation("org.scalatest.Style").isDefined() &&
                        scClass.hasAnnotation("org.scalatest.Style").get() != null) {
                    classWithStyle = scClass;
                    break;
                }
            } else if (clazzz instanceof ScTrait) {
                ScTrait scTrait = (ScTrait) clazzz;
                if (scTrait.hasAnnotation("org.scalatest.Style").isDefined() &&
                        scTrait.hasAnnotation("org.scalatest.Style").get() != null) {
                    classWithStyle = scTrait;
                    break;
                }
            }
        }

        if (classWithStyle != null) {
            ScTypeDefinition typeDef = (ScTypeDefinition) classWithStyle;
            try {
                String finderClassName;
                ScAnnotation styleAnnotation = typeDef.hasAnnotation("org.scalatest.Style").get();
                if (styleAnnotation != null) {
                    String notFound = "NOT FOUND STYLE TEXT";
                    ScConstructor constructor = (ScConstructor) styleAnnotation.getClass().getMethod("constructor").invoke(styleAnnotation);
                    if (constructor != null) {
                        ScArgumentExprList args = constructor.args().isDefined() ? constructor.args().get() : null;
                        ScAnnotationExpr annotationExpr = styleAnnotation.annotationExpr();
                        List<ScNameValuePair> valuepairs = JavaConversions.seqAsJavaList(annotationExpr.getAttributes());
                        if (args == null && !valuepairs.isEmpty()) {
                            finderClassName = valuepairs.get(0).getLiteralValue() == null ?
                                    notFound : valuepairs.get(0).getLiteralValue();
                        } else if (args != null) {
                            List<ScExpression> exprs = JavaConversions.seqAsJavaList(args.exprs());
                            if (exprs.size() > 0) {
                                ScExpression expr = exprs.get(0);
                                if (expr instanceof ScLiteral && ((ScLiteral) expr).isString()) {
                                    Object value = ((ScLiteral) expr).getValue();
                                    if (value instanceof String) {
                                        finderClassName = (String) value;
                                    } else {
                                        finderClassName = notFound;
                                    }
                                } else if (expr instanceof ScAssignStmt) {
                                    ScAssignStmt assignStmt = (ScAssignStmt) expr;
                                    if (assignStmt.getLExpression() instanceof ScReferenceExpression &&
                                            ((ScReferenceExpression) assignStmt.getLExpression()).refName().equals("value")) {
                                        ScExpression rExpr = assignStmt.getRExpression().get();
                                        if (rExpr == null) {
                                            finderClassName = notFound;
                                        } else if (rExpr instanceof ScLiteral && ((ScLiteral) rExpr).isString()) {
                                            Object value2 = ((ScLiteral) rExpr).getValue();
                                            if (value2 instanceof String) {
                                                finderClassName = (String) value2;
                                            } else {
                                                finderClassName = notFound;
                                            }
                                        } else {
                                            finderClassName = notFound;
                                        }
                                    } else {
                                        finderClassName = notFound;
                                    }
                                } else {
                                    finderClassName = notFound;
                                }
                            } else {
                                finderClassName = notFound;
                            }
                        } else {
                            finderClassName = notFound;
                        }
                    } else {
                        finderClassName = notFound;
                    }
                } else {
                    throw new RuntimeException("Match is not exhaustive!");
                }
                return (Finder) loadClass(finderClassName, module).newInstance();
            } catch (Exception e) {
                String suiteClassName = typeDef.qualifiedName();
                Class suiteClass = loadClass(suiteClassName, module);
                if (suiteClass == null) return null;
                List<Annotation> annotations = Arrays.asList(suiteClass.getAnnotations());
                Annotation styleOpt = null;
                for (Annotation a : annotations) {
                    if (a.annotationType().getName() == "org.scalatest.Style") {
                        styleOpt = a;
                    }
                }
                if (styleOpt != null) {
                    Method valueMethod = styleOpt.annotationType().getMethod("value");
                    String finderClassName = (String) valueMethod.invoke(styleOpt);
                    if (finderClassName != null) {
                        Class finderClass = loadClass(finderClassName, module);
                        Object instance = finderClass.newInstance();
                        if (instance instanceof Finder) {
                            return (Finder) instance;
                        } else {
                            return null;
                        }
                    } else {
                        return null;
                    }
                } else {
                    return null;
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
            if (other != null && other instanceof StConstructorBlock) {
                return element.equals(((StConstructorBlock) other).element);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return element.hashCode();
        }
    }

    class StMethodDefinition extends org.scalatest.finders.MethodDefinition {
        public String pClassName;
        public PsiElement element;
        public String pName;

        public StMethodDefinition(String pClassName, PsiElement element, String pName, String... pParamTypes) {
            super(pClassName, null, new AstNode[0], pName, pParamTypes);
            this.pClassName = pClassName;
            this.element = element;
            this.pName = pName;
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
        public boolean equals(Object other) {
            if (other != null && other instanceof StMethodDefinition) {
                return element.equals(((StMethodDefinition) other).element);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return element.hashCode();
        }
    }


    private class StMethodInvocation extends org.scalatest.finders.MethodInvocation {
        public String pClassName;
        public AstNode pTarget;
        public MethodInvocation invocation;
        public String pName;

        public StMethodInvocation(String pClassName, AstNode pTarget, MethodInvocation invocation, String pName, AstNode... args) {
            super(pClassName, pTarget, null, new AstNode[0], pName, args);
            this.pClassName = pClassName;
            this.pTarget = pTarget;
            this.pName = pName;
            this.invocation = invocation;
        }

        @Override
        public AstNode parent() {
            return getParentNode(pClassName, invocation);
        }

        @Override
        public AstNode[] children() {
            return getChildren(pClassName, invocation);
        }

        @Override
        public boolean equals(Object other) {
            if (other != null && other instanceof StMethodInvocation) {
                return invocation.equals(((StMethodInvocation) other).invocation);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return invocation.hashCode();
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
            if (other != null && other instanceof StStringLiteral) {
                return element.equals(((StStringLiteral) other).element);
            } else {
                return false;
            }
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


        @Override
        public AstNode parent() {
            return getParentNode(pClassName, element);
        }


        @Override
        public AstNode[] children() {
            return getChildren(pClassName, element);
        }

        @Override
        public boolean equals(Object other) {
            if (other != null && other instanceof StToStringTarget) {
                return element.equals(((StToStringTarget) other).element);
            } else {
                return false;
            }
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
        AstNode[] emptyArray = new AstNode[0];
        if (firstChild instanceof ScLiteral && (((ScLiteral) firstChild).isString())) {
            return new ToStringTarget(className, null, emptyArray, ((ScLiteral) firstChild).getValue().toString());
        } else if (firstChild instanceof MethodInvocation) {
            StMethodInvocation inv = getScalaTestMethodInvocation(selected, (MethodInvocation) firstChild, Collections.<ScExpression>emptyList(), className);
            if (inv != null) {
                return inv;
            } else {
                return new ToStringTarget(className, null, emptyArray, firstChild.getText());
            }
        } else {
            return new ToStringTarget(className, null, emptyArray, firstChild.getText());
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
                    containingClassName = ((ScMember) resolve).containingClass().qualifiedName();
                } else if (resolve instanceof ScBindingPattern) {
                    PsiElement context = ((ScBindingPattern) resolve).nameContext();
                    if (context instanceof ScMember) {
                        containingClassName = ((ScMember) context).containingClass().qualifiedName();
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
            AstNode[] array = new AstNode[args.size()];
            args.toArray(array);
            return new StMethodInvocation(containingClassName, target, selected, pName, array);
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
            return new StMethodDefinition(className, methodDef, name, array);
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

    public Selection testSelection(Location<? extends PsiElement> location) throws MalformedURLException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        PsiElement element = location.getPsiElement();
        ScClass clazz = PsiTreeUtil.getParentOfType(element, ScClass.class, false);
        if (clazz == null) return null;
        Finder finder = getFinder(clazz, location.getModule());
        if (finder != null) {
            AstNode selectedAst = getSelectedAstNode(clazz.qualifiedName(), element);
            AstNode selectedAstOpt = selectedAst == null ? selectedAst : selectedAst.parent();
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
            AstNode parentOpt = transformNode(className, element.getParent());
            if (parentOpt != null) {
                result.add(parentOpt);
            }
        }
        AstNode[] resultArray = new AstNode[result.size()];
        result.toArray(resultArray);
        return resultArray;
    }
}
