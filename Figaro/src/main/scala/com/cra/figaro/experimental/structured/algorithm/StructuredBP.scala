/*
 * StructuredBP.scala
 * A structured factored inference algorithm using belief propagation.
 *
 * Created By:      Avi Pfeffer (apfeffer@cra.com)
 * Creation Date:   March 1, 2015
 *
 * Copyright 2015 Avrom J. Pfeffer and Charles River Analytics, Inc.
 * See http://www.cra.com or email figaro@cra.com for information.
 *
 * See http://www.github.com/p2t2/figaro for a copy of the software license.
 */
package com.cra.figaro.experimental.structured.algorithm

import com.cra.figaro.algorithm.OneTimeProbQuery
import com.cra.figaro.algorithm.Algorithm
import com.cra.figaro.language.Element
import com.cra.figaro.language.Universe
import com.cra.figaro.algorithm.factored.factors.Factor
import com.cra.figaro.experimental.structured.ComponentCollection
import com.cra.figaro.experimental.structured.Problem
import com.cra.figaro.experimental.structured.strategy.recursiveSolver
import com.cra.figaro.experimental.structured.solver.beliefPropagation
import com.cra.figaro.algorithm.factored.factors.SumProductSemiring
import com.cra.figaro.experimental.structured.factory.Factory

class StructuredBP(val universe: Universe, iterations: Int, targets: Element[_]*) extends Algorithm with OneTimeProbQuery {
  val queryTargets = targets

  var targetFactors: Map[Element[_], Factor[Double]] = _

  var cc: ComponentCollection = _

  def run() {
    cc = new ComponentCollection
    targetFactors = Map()
    val problem = new Problem(cc, targets.toList)
    val evidenceElems = universe.conditionedElements ::: universe.constrainedElements
    evidenceElems.foreach(elem => if (!cc.contains(elem)) problem.add(elem))
    recursiveSolver(beliefPropagation(iterations))(problem)
    val joint = problem.solution.foldLeft(Factory.unit(SumProductSemiring()))(_.product(_))

    def marginalizeToTarget(target: Element[_]): Unit = {
      val targetVar = cc(target).variable
      val unnormalizedTargetFactor = joint.marginalizeTo(SumProductSemiring(), targetVar)
      val z = unnormalizedTargetFactor.foldLeft(0.0, _ + _)
      val targetFactor = unnormalizedTargetFactor.mapTo((d: Double) => d / z)
      targetFactors += target -> targetFactor
    }

    targets.foreach(marginalizeToTarget(_))
  }

  /**
   * Computes the normalized distribution over a single target element.
   */
  def computeDistribution[T](target: Element[T]): Stream[(Double, T)] = {
    val factor = targetFactors(target)
    val targetVar = cc(target).variable
    val dist = factor.getIndices.filter(f => targetVar.range(f.head).isRegular).map(f => (factor.get(f), targetVar.range(f.head).value))
    // normalization is unnecessary here because it is done in marginalizeTo
    dist.toStream
  }

 /**
   * Computes the expectation of a given function for single target element.
   */
  def computeExpectation[T](target: Element[T], function: T => Double): Double = {
    def get(pair: (Double, T)) = pair._1 * function(pair._2)
    (0.0 /: computeDistribution(target))(_ + get(_))
  }
}

/*
 * StructuredBP.scala
 * A structured belief propagation algorithm.
 *
 * Created By:      Avi Pfeffer (apfeffer@cra.com)
 * Creation Date:   March 1, 2015
 *
 * Copyright 2015 Avrom J. Pfeffer and Charles River Analytics, Inc.
 * See http://www.cra.com or email figaro@cra.com for information.
 *
 * See http://www.github.com/p2t2/figaro for a copy of the software license.
 */

object StructuredBP {
  /**
   * Create a structured belief propagation algorithm.
   * @param iterations the number of iterations to use for each subproblem
   * @param targets the query targets, which will all be part of the top level problem
   */
  def apply(iterations: Int, targets: Element[_]*) = {
    if (targets.isEmpty) throw new IllegalArgumentException("Cannot run VE with no targets")
    val universes = targets.map(_.universe).toSet
    if (universes.size > 1) throw new IllegalArgumentException("Cannot have targets in different universes")
    new StructuredBP(targets(0).universe, iterations, targets:_*)
  }

  /**
   * Use BP to compute the probability that the given element satisfies the given predicate.
   */
  def probability[T](target: Element[T], predicate: T => Boolean, iterations: Int): Double = {
    val alg = StructuredBP(iterations, target)
    alg.start()
    val result = alg.probability(target, predicate)
    alg.kill()
    result
  }

  /**
   * Use BP to compute the probability that the given element has the given value.
   */
  def probability[T](target: Element[T], value: T, iterations: Int = 100): Double =
    probability(target, (t: T) => t == value, iterations)
}
