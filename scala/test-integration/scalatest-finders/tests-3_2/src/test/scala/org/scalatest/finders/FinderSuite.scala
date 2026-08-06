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

package org.scalatest.finders

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers._

import scala.annotation.nowarn

@nowarn("cat=deprecation")
trait FinderSuite extends AnyFunSuite {

  def expectSelection(selection: Selection, expectedClassName: String, expectedDisplayName: String, expectedTestNames: Array[String]) {
    assert(
      selection != null,
      "Test is null, " +
        s"expected className=$expectedClassName, " +
        s"displayName=$expectedDisplayName, " +
        s"testNames=${expectedTestNames.toIndexedSeq.toString}"
    )
    selection.className shouldBe expectedClassName
    selection.displayName shouldBe expectedDisplayName
    selection.testNames should contain theSameElementsAs expectedTestNames.toIndexedSeq
  }

}
