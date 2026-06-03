/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.components;

import org.jetbrains.annotations.NonNls;

public interface ScalaComponents {

  @NonNls
  String SCALA_FILE_CREATOR = "SCALA_FILE_CREATOR";

  String SCALA_CACHE_MANAGER = "SCALA_CACHE_MANAGER";

  String SCALA_PSI_ELEMENT_FINDER = "SCALA_PSI_ELEMENT_FINDER";

  String SCALA_SDK_CACHE_MANAGER = "SCALA_SDK_CACHE_MANAGER";

  String SCALA_LIBRARY_CACHE_MANAGER = "SCALA_LIBRARY_CACHE_MANAGER";
}
