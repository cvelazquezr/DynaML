package io.github.mandar2812.dynaml.tensorflow.utils

import org.scalatest.{FlatSpec, Matchers}
import io.github.mandar2812.dynaml.tensorflow._
import org.platanios.tensorflow.api._
import _root_.io.github.mandar2812.dynaml.probability.GaussianRV


class UtilsApiSpec extends FlatSpec with Matchers {

  "Regression Metrics on Tensors" should " compute the relevant metrics correctly" in {

    val t = dtf.random(FLOAT64, 10)(GaussianRV(1.0, 1.0))

    val metricsTF = new GenRegressionMetricsTF(t, t)

    assert(
      tfi.abs(
        metricsTF.results - dtf.tensor_f64(4)(0d, 0d, 1d, 1d)
      ).mean().scalar < 1E-7)

  }

  "Gaussian and Min-Max scaling " should "compute correctly on tensors" in {

    val data = dtf.tensor_f64(2)(0d, 1d)

    val sc_g = dtfpipe.gauss_std().run(data)
    val sc_m = dtfpipe.minmax_std().run(data)

    assert(
      sc_g._2.mean.reshape(Shape(1)) == Tensor(0.5) &&
        tfi.abs(sc_g._2.sigma.square.reshape(Shape(1)) - Tensor(0.5)).scalar < 1E-6)

    assert(
      sc_m._2.min.reshape(Shape(1)) == Tensor(0d) &&
        sc_m._2.max.reshape(Shape(1)) == Tensor(1d))
  }

}
