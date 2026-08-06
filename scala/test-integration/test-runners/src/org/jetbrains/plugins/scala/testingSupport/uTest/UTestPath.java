package org.jetbrains.plugins.scala.testingSupport.uTest;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class UTestPath {
  private final String qualifiedClassName;
  private final List<String> path;

  // NOTE: actually this is a redundant info to be placed in a each instance
  // because uTest tests contain all the tests in the same test method `def tests`
  private final Method method;

  public UTestPath(String qualifiedClassName, List<String> path, Method method) {
    this.method = method;
    this.qualifiedClassName = qualifiedClassName;
    this.path = path;
  }

  public UTestPath(String qualifiedClassName, Method method) {
    this(qualifiedClassName, new LinkedList<>(), method);
  }

  public UTestPath(String qualifiedClassName) {
    this(qualifiedClassName, new LinkedList<>(), null);
  }

  public UTestPath append(String scope) {
    List<String> newPath = new LinkedList<>(path);
    newPath.add(scope);
    return new UTestPath(qualifiedClassName, newPath, method);
  }

  public UTestPath append(List<String> scopes) {
    List<String> newPath = new LinkedList<>(path);
    newPath.addAll(scopes);
    return new UTestPath(qualifiedClassName, newPath, method);
  }

  public Method getMethod() {
    return method;
  }

  public String getQualifiedClassName() {
    return qualifiedClassName;
  }

  public UTestPath getClassTestPath() {
    return new UTestPath(qualifiedClassName);
  }

  public static UTestPath getMethodPath(String classFqn, Method method) {
    return new UTestPath(classFqn, new LinkedList<>(), method);
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
    final String name;
    if (!path.isEmpty()) {
      name = path.get(path.size() - 1);
    } else if (method != null) {
      name = method.getName(); // TODO: we don't need intermediate test node in results tree
    } else {
      name = getClassSimpleName(qualifiedClassName);
    }
    return name;
  }

  private String getClassSimpleName(String classFqn) {
    int lastDotPosition = classFqn.lastIndexOf(".");
    return (lastDotPosition != -1) ? classFqn.substring(lastDotPosition + 1) : classFqn;
  }

  public List<String> getPath() {
    return Collections.unmodifiableList(path);
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
      return otherPath.qualifiedClassName.equals(qualifiedClassName) &&
              otherPath.path.equals(path) &&
              (otherPath.method != null && otherPath.method.equals(method) || otherPath.method == null && method == null);
    } else {
      return false;
    }
  }
}
