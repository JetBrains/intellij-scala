package org.jetbrains.plugins.scala.probes;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author Nikolay.Tropin
 */
public class Utilities {

    public static String currentStackTrace() {
        StackTraceElement[] stackTrace = new Exception().getStackTrace();
        StringBuilder buffer = new StringBuilder();
        for (int i = 2; i < stackTrace.length; i++) {
            StackTraceElement element = stackTrace[i];
            buffer.append(element.toString()).append("\n");
        }
        return buffer.toString();
    }

    public static String getContainingFileName(Object psiElem) {
        Object file = invokeMethod("getContainingFile", null, psiElem);
        return file != null ? file.toString() : "null";
    }

    public static String refName(Object ref) {
        return invokeMethod("refName", "not found", ref).toString();
    }

    public static String getName(Object namedPsiElem) {
        return (String) invokeMethod("getName", "not found", namedPsiElem);
    }

    public static String getTargetName(Object resolveResult) {
        Object element = invokeMethod("getElement", null, resolveResult);
        String fqn = getFQN(element);
        return element != null ? element.getClass().getSimpleName() + ": " + fqn : "not found";
    }

    public static String getFQN(Object namedPsiElem) {
        Class<?> aClass = namedPsiElem.getClass();
        Method getQN = findMethodByName(aClass, "getQualifiedName");
        if (getQN != null) {
            Object result = invokeMethod(getQN, "not found", namedPsiElem);
            return result != null ? (String) result : getName(namedPsiElem);
        }

        Method getContainingClass = findMethodByName(aClass, "getContainingClass");
        String name = invokeMethod("getName", "not found", namedPsiElem).toString();
        Object containingClass = getContainingClass != null ? invokeMethod(getContainingClass, null, namedPsiElem): null;
        String qualifier = containingClass != null ? invokeMethod("getQualifiedName", "", containingClass) + "." : "";
        return qualifier + "." + name;
    }

    public static int getOffset(Object psiElem) {
        Object textRange = invokeMethod("getTextRange", null, psiElem);
        if (textRange != null) {
            return (Integer) invokeMethod("getStartOffset", -1, textRange);
        }
        else {
            return -1;
        }
    }

    public static String getText(Object psiElem) {
        return (String) invokeMethod("getText", "not found", psiElem);
    }

    public static String toString(Object o) {
        return (String) invokeMethod("toString", "toString failed", o);
    }

    public static String name(Object psiElem) {
        return invokeMethod("name", "not found", psiElem).toString();
    }

    public static String presentableTextFromTypeResult(Object typeResult) {
        Class<?> aClass = typeResult.getClass();
        boolean success = aClass.getSimpleName().equals("Success");
        boolean some = aClass.getSimpleName().equals("Some");
        if (success || some) {
            Object scType = invokeMethod("get", null, typeResult);
            if (scType != null) return invokeMethod("presentableText", "not found", scType).toString();
        }
        return "not found";
    }

    public static String presentableText(Object scType) {
        return (String) invokeMethod("presentableText", "not found", scType);
    }

    public static String firstComponentText(Object tuple) {
        return invokeMethod("_1", "not found", tuple).toString();
    }

    public static boolean isStub(Object psiElement) {
        return invokeMethod("getStub", null, psiElement) != null;
    }

    private static Object invokeMethod(String methodName, Object dflt, Object obj, Object... args) {
        Class<?> aClass = obj.getClass();
        Method method = findMethodByName(aClass, methodName);
        if (method == null) throw new NoSuchMethodError("No method " + methodName + " found in " + obj.toString());
        return invokeMethod(method, dflt, obj, args);
    }

    private static Object invokeMethod(Method method, Object dflt, Object obj, Object... args) {
        try {
            return method.invoke(obj, args);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            printException(e);
        }
        return dflt;
    }

    private static Method findMethodByName(Class<?> aClass, String methodName) {
        for (Method method : aClass.getMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        return null;
    }

    private static void printException(Throwable e) {
        Throwable cause = e.getCause();
        if (cause != null) System.out.println(e.toString() + " caused by " + cause.toString());
        else System.out.println(e.toString());
    }

    public static long gcTime() {
        long res = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long collectionTime = bean.getCollectionTime();
            if (collectionTime > 0)
                res += collectionTime;
        }
        return res;
    }
}
