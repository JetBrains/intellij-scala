/*
 * Copyright 2001-2008 Artima, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scalatest.finders;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;


public class LocationUtils {
  
  private static List<Finder> getFinderInstances(Class<?> clazz) throws SecurityException, NoSuchMethodException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, ClassNotFoundException, InstantiationException {
    Annotation[] annotations = clazz.getAnnotations();
    Annotation findersAnnotation = null;
    for (Annotation annotation : annotations) {
      if (annotation.annotationType().getName().equals("org.scalatest.Finders")) {
        findersAnnotation = annotation;
        break;
      }
    }
    List<Finder> finderList = new ArrayList<>();
    if (findersAnnotation != null) {
      Method valueMethod = findersAnnotation.annotationType().getMethod("value");
      String[] finderClassNames = (String[]) valueMethod.invoke(findersAnnotation);
      for (String finderClassName : finderClassNames) {
        Class<?> finderClass = clazz.getClassLoader().loadClass(finderClassName);
        Object instance = finderClass.getDeclaredConstructor().newInstance();
        if (instance instanceof Finder)
          finderList.add((Finder) instance);
      }
    }
    
    return finderList;
  }
  
  private static List<Finder> lookInSuperClasses(Class<?> clazz) throws SecurityException, IllegalArgumentException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, ClassNotFoundException, InstantiationException {
    Class<?> superClass;
    List<Finder> finders = new ArrayList<>();
    while(finders.size() == 0 && (superClass = clazz.getSuperclass()) != null) {
      finders = getFinderInstances(superClass);
      clazz = superClass;
    }
    return finders;
  }
  
  private static List<Finder> lookInInterfaces(Class<?>[] interfaces) throws SecurityException, IllegalArgumentException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, ClassNotFoundException, InstantiationException {
    List<Finder> finders = new ArrayList<>();
    while(finders.size() == 0 && interfaces.length != 0) {
      List<Class<?>> newInterfaces = new ArrayList<>();
      for (Class<?> itf : interfaces) {
        finders = getFinderInstances(itf);
        if (finders.size() == 0)
          newInterfaces.addAll(Arrays.asList(itf.getInterfaces()));
        else
          break;
      }
      interfaces = newInterfaces.toArray(new Class<?>[0]);
    }
    return finders;
  }
  
  public static List<Finder> getFinders(Class<?> clazz) throws SecurityException, IllegalArgumentException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, ClassNotFoundException, InstantiationException {
    List<Finder> finders = getFinderInstances(clazz);
    if (finders.size() == 0) // Look for super interface first since style traits are compiled as Java interfaces
      finders = lookInInterfaces(clazz.getInterfaces());
    if (finders.size() == 0) // Look in super classes, in case custom test style is a class instead of trait.
      finders = lookInSuperClasses(clazz);
    return finders;
  }

  @SuppressWarnings("unchecked")
  public static <T extends AstNode> T getParentOfType(AstNode node, Class<T> clazz) {
    T result = null;
    while (result == null && node.parent() != null) {
      if (clazz.isAssignableFrom(node.parent().getClass()))
        result = (T) node.parent();
      else
        node = node.parent();
    }
    return result; 
  }
  
  public static <T extends AstNode> AstNode getParentBeforeType(AstNode node, Class<T> clazz) {
    AstNode result = null;
    while (result == null && node.parent() != null) {
      if (clazz.isAssignableFrom(node.parent().getClass()))
        result = node;
      else
        node = node.parent();
    }
    return result;
  }
  
  public static boolean isValidName(String name, Set<String> validNames) {
    return validNames.contains(name);
  }
  
  public static boolean isSingleStringParamInvocationWithName(MethodInvocation invocation, Set<String> validNames) {
    return isValidName(invocation.name(), validNames) && invocation.args().length == 1;
  }
}
