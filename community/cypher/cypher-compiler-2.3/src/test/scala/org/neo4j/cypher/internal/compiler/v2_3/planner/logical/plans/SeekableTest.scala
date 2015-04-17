/**
 * Copyright (c) 2002-2015 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.compiler.v2_3.planner.logical.plans

import org.mockito.Mockito
import org.neo4j.cypher.internal.commons.CypherFunSuite
import org.neo4j.cypher.internal.compiler.v2_3.ast._

class SeekableTest extends CypherFunSuite with AstConstructionTestSupport {

  val expr1 = mock[Expression]
  val expr2 = mock[Expression]

  val nodeA = ident("a")

  test("Seek finds Equals") {
    assertMatches(Equals(expr1, expr2)_) {
      case Seekable(lhs, rhs) =>
        lhs should equal(expr1)
        rhs should equal(SingleSeekableArg(expr2))
    }
  }

  test("Seek finds In") {
    assertMatches(In(expr1, expr2)_) {
      case Seekable(lhs, rhs) =>
        lhs should equal(expr1)
        rhs should equal(ManySeekableArgs(expr2))
    }
  }

  test("MultiSeekRhs has size hint for collections") {
    ManySeekableArgs(Collection(Seq(expr1, expr2))_).sizeHint should equal(Some(2))
  }

  test("IdSeek works") {
    val leftExpr: FunctionInvocation = FunctionInvocation(FunctionName("id") _, nodeA)_
    Mockito.when(expr2.dependencies).thenReturn(Set.empty[Identifier])
    val expr: Equals = Equals(leftExpr, expr2) _

    assertMatches(expr) {
      case IdSeekable(lhs, rhs) =>
        lhs.ident should equal(nodeA)
        lhs.expr should equal(leftExpr)
        lhs.name should equal(nodeA.name)
        rhs.expr should equal(expr2)
        rhs.sizeHint should equal(Some(1))
    }
  }

  test("IdSeek does not match if rhs depends on lhs identifier") {
    val leftExpr: FunctionInvocation = FunctionInvocation(FunctionName("id") _, nodeA)_
    Mockito.when(expr2.dependencies).thenReturn(Set(nodeA))
    val expr: Equals = Equals(leftExpr, expr2) _

    assertDoesNotMatch(expr) {
      case IdSeekable(_, _) => (/* oh noes */)
    }
  }

  test("IdSeek does not match if function is not the id function") {
    val leftExpr: FunctionInvocation = FunctionInvocation(FunctionName("rand") _, nodeA)_
    Mockito.when(expr2.dependencies).thenReturn(Set.empty[Identifier])
    val expr: Equals = Equals(leftExpr, expr2) _

    assertDoesNotMatch(expr) {
      case IdSeekable(_, _) => (/* oh noes */)
    }
  }

  test("PropertySeek works with plain expressions") {
    val leftExpr: Property = Property(nodeA, PropertyKeyName("id")_)_
    val expr: Expression = In(leftExpr, expr2)_
    Mockito.when(expr2.dependencies).thenReturn(Set.empty[Identifier])

    assertMatches(expr) {
      case PropertySeekable(lhs, rhs) =>
        lhs.ident should equal(nodeA)
        lhs.expr should equal(leftExpr)
        lhs.name should equal(nodeA.name)
        rhs.expr should equal(expr2)
        rhs.sizeHint should equal(None)
    }
  }

  test("PropertySeek works with collection expressions") {
    val leftExpr: Property = Property(nodeA, PropertyKeyName("id")_)_
    val rightExpr: Collection = Collection(Seq(expr1, expr2))_
    val expr: Expression = In(leftExpr, rightExpr)_
    Mockito.when(expr1.dependencies).thenReturn(Set.empty[Identifier])
    Mockito.when(expr2.dependencies).thenReturn(Set.empty[Identifier])

    assertMatches(expr) {
      case PropertySeekable(lhs, rhs) =>
        lhs.ident should equal(nodeA)
        lhs.expr should equal(leftExpr)
        lhs.name should equal(nodeA.name)
        rhs.expr should equal(rightExpr)
        rhs.sizeHint should equal(Some(2))
    }
  }

  test("PropertySeek does not match if rhs depends on lhs identifier") {
    val leftExpr: Property = Property(nodeA, PropertyKeyName("id")_)_
    Mockito.when(expr2.dependencies).thenReturn(Set(nodeA))
    val expr: Expression = In(leftExpr, expr2)_

    assertDoesNotMatch(expr) {
      case PropertySeekable(_, _) => (/* oh noes */)
    }
  }

  def assertMatches[T](item: Expression)(pf: PartialFunction[Expression, T]) =
    if (pf.isDefinedAt(item)) pf(item) else fail(s"Failed to match: $item")

  def assertDoesNotMatch[T](item: Expression)(pf: PartialFunction[Expression, T]) =
    if (pf.isDefinedAt(item)) fail(s"Erroneously matched: $item")
}