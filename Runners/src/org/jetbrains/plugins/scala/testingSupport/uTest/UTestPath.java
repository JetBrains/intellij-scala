package org.jetbrains.plugins.scala.testingSupport.uTest;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Roman.Shein
 * @since 12.08.2015.
 */
public class UTestPath {
  private final Method method;
  private final String qualifiedClassName;
  private final List<String> path;

  public UTestPath(String qualifiedClassName, List<String> path, Method method) {
    this.method = method;
    this.qualifiedClassName = qualifiedClassName;
    this.path = path;
  }

  public UTestPath(String qualifiedClassName, Method method) {
    this(qualifiedClassName, new LinkedList<String>(), method);
  }

  public UTestPath(String qualifiedClassName) {
    this(qualifiedClassName, new LinkedList<String>(), null);
  }

  public UTestPath append(String scope) {
    List<String> newPath = new LinkedList<String>(path);
    newPath.add(scope);
    return new UTestPath(qualifiedClassName, newPath, method);
  }

  public UTestPath append(List<String> scopes) {
    List<String> newPath = new LinkedList<String>(path);
    newPath.addAll(scopes);
    return new UTestPath(qualifiedClassName, newPath, method);
  }

  public Method getMethod() {
    return method;
  }

  public String getQualifiedClassName() {
    return qualifiedClassName;
  }

  public UTestPath getclassTestPath() {
    return new UTestPath(qualifiedClassName);
  }

  public UTestPath getMethodPath() {
    return new UTestPath(qualifiedClassName, new LinkedList<String>(), method);
  }

  public UTestPath parent() {
    if (path.isEmpty() && method == null) {
      return null;
    } else if (path.isEmpty()) {
      return new UTestPath(qualifiedClassName);
    } else {
      return new UTestPath(qualifiedClassName, path.subList(0, path.size() - 1), method);
    }
  }

  public String getTestName() {
    if (path.isEmpty()) {
      if (method == null) {
        return qualifiedClassName;
      } else {
        return method.getName();
      }
    } else {
      return path.get(path.size() - 1);
    }
  }

  public List<String> getPath() {
    return Collections.unmodifiableList(path);
  }

  public boolean isMethodRepresentation() {
    return path.isEmpty() && method != null;
  }

  public boolean isSuiteRepresentation() {
    return path.isEmpty() && method == null;
  }

  @Override
  public String toString() {
    StringBuilder resBuilder = new StringBuilder(qualifiedClassName).append(method == null ? "" : "." + method.getName());
    for (String pathMember: path) {
      resBuilder.append("\\").append(pathMember);
    }
    return resBuilder.toString();
  }

  @Override
  public int hashCode() {
    return qualifiedClassName.hashCode() + path.hashCode() + (method == null ? 0 : method.hashCode());
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof UTestPath) {
      UTestPath otherPath = (UTestPath) other;
      return otherPath.qualifiedClassName.equals(qualifiedClassName) && otherPath.path.equals(path) &&
          (otherPath.method != null && otherPath.method.equals(method) || otherPath.method == null && method == null);
    } else {
      return false;
    }
  }
}
