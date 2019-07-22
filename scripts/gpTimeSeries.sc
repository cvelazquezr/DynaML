{
  import breeze.linalg.eig
  import breeze.stats.distributions.{ContinuousDistr, Gamma}
  import io.github.mandar2812.dynaml.kernels._
  import io.github.mandar2812.dynaml.models.bayes.{
    LinearTrendESGPrior,
    LinearTrendGaussianPrior,
    GaussianProcessPrior
  }
  import io.github.mandar2812.dynaml.modelpipe._
  import io.github.mandar2812.dynaml.probability._
  import io.github.mandar2812.dynaml.DynaMLPipe._
  import io.github.mandar2812.dynaml.graphics.charts.Highcharts._
  import io.github.mandar2812.dynaml.analysis.implicits._
  import io.github.mandar2812.dynaml.optimization.GPMixtureMachine
  import io.github.mandar2812.dynaml.pipes._
  import io.github.mandar2812.dynaml.tensorflow.dtfdata
  import io.github.mandar2812.dynaml.probability.distributions.UnivariateGaussian
  import spire.implicits._

  //For p order auto-regressive dynamics
  val p                = 3
  val num_sample_paths = 10
  //Simulation time of the time series models.
  val len = 100

  val xs = Seq.tabulate[Double](len)(1d * _)

  implicit val ev = VectorField(p * (p + 1))

  val y0 = RandomVariable(UnivariateGaussian(0.0, 1.0))

  val urv = UniformRV(-1.0, 1.0)

  val coeff_gen =
    (order: Int) =>
      MultGaussianRV(
        DenseVector.tabulate[Double](order * (order + 1))(
          i => if (i == 0) 0d else -0.01d
        ),
        diag(
          DenseVector.tabulate[Double](order * (order + 1))(
            i =>
              if (i < order) 0.05d
              else 0.001
          )
        )
      )

  val w_prior = coeff_gen(p)

  //Draw the AR(p) coefficients from the prior distribution w_prior
  val w = w_prior.draw

  //Define some kernels for use later.
  val rbfc         = new SEKernel(1d, 2.0)
  val mlpKernel    = new MLPKernel(1d, 0.5d)
  val polyKernel   = new PolynomialKernel(2, 0d)
  val fbm          = new FBMCovFunction(0.5)
  val stKernel     = new TStudentKernel(0.5)
  val maternKernel = new GenericMaternKernel[DenseVector[Double]](2.5, p)

  val perKernel = new PeriodicCovFunc(2d, 1.5d, 0.1d)
  val arpKernel = new GenericMaternKernel[Double](2.5, p)
  val n         = new MAKernel(1.0)
  val noise     = new DiracKernel(0.5d)

  val gsmKernel = GaussianSpectralKernel(
    DenseVector.zeros[Double](p),
    DenseVector.ones[Double](p),
    GaussianSpectralKernel.getEncoderforBreezeDV(p)
  )

  
  val linear_coeff    = (0d, 0d)
  val quadratic_coeff = (-0.01d, -0.75d, 0d)

  //Define the trend functions. One a linear trend
  //and the other a parabola.

  val linear_vec_trend = MetaPipe(
    (p: DenseVector[Double]) =>
      (x: DenseVector[Double]) => {
        p dot x
      }
  )

  val basis_func_mapping = DataPipe(
    (x: DenseVector[Double]) =>
      (x * DenseVector.vertcat(DenseVector(1d), x).t).toDenseVector
  )

  val quadratic_vec_trend = MetaPipe(
    (p: DenseVector[Double]) =>
      (x: DenseVector[Double]) => {
        p dot basis_func_mapping(x)
      }
  )

  val linear_trend_mean = MetaPipe(
    (p: (Double, Double)) =>
      (x: Double) => {
        (p._1 * x) + p._2
      }
  )

  val quadratic_trend_mean = MetaPipe(
    (p: (Double, Double, Double)) =>
      (x: Double) => {
        (p._1 * x * x) + (p._2 * x) + p._3
      }
  )

  //Determine how the trend coefficients map to key-value pairs
  val linear_trend_encoder = Encoder(
    (cs: (Double, Double)) => Map("slope" -> cs._1, "intercept" -> cs._2),
    (conf: Map[String, Double]) => (conf("slope"), conf("intercept"))
  )

  val linear_vec_trend_encoder = Encoder(
    (cs: DenseVector[Double]) => {
      cs.toArray.zipWithIndex.map(cp => (s"w_${cp._2}", cp._1)).toMap
    },
    (conf: Map[String, Double]) =>
      DenseVector(conf.toSeq.sortBy(_._1).map(_._2).toArray)
  )

  val quadratic_trend_encoder = Encoder(
    (cs: (Double, Double, Double)) =>
      Map("a" -> cs._1, "b" -> cs._2, "d" -> cs._3),
    (conf: Map[String, Double]) => (conf("a"), conf("b"), conf("d"))
  )

  //Define a Matern(p + 1/2) covariance based gaussian process
  val gp_explicit = GaussianProcessPrior[Double, (Double, Double)](
    arpKernel,
    new MAKernel(0.5d),
    linear_trend_mean,
    linear_trend_encoder,
    linear_coeff
  )


  maternKernel.block("p")

  //Define a gaussian process for GP Time Series models.
  val gp_prior = GaussianProcessPrior[DenseVector[Double], DenseVector[Double]](
    rbfc,
    noise,
    linear_vec_trend,
    linear_vec_trend_encoder,
    w(0 until p)
  )

  val gpModelPipe =
    GPRegressionPipe[Seq[(DenseVector[Double], Double)], DenseVector[Double]](
      identityPipe[Seq[(DenseVector[Double], Double)]],
      rbfc,
      noise,
      linear_vec_trend(w(0 until p))
    )

  val y_explicit: MultGaussianPRV = gp_explicit.priorDistribution(xs)

  //Generate samples for GP process on explicit time. Matern(p + 1/2)
  val samples_gp_explicit =
    y_explicit.iid(10).draw.map(s => s.toBreezeVector.toArray.toSeq).toSeq

  //Generate samples for GP-NAR process in a recursive manner
  val ys_ar_rec = RandomVariable[Seq[Double]](() => {
    // Generate y0 and y1
    val u0: DenseVector[Double] = DenseVector.tabulate(p)(_ => y0.draw)
    val u1: Double              = gp_prior.priorDistribution(Seq(u0)).draw.toStream.head
    val d                       = Seq((u0, u1))
    val (_, xsamples) =
      (1 to xs.length - p - 1)
        .scanLeft((d, DenseVector(Array(u1) ++ u0(0 to -2).toArray)))(
          (dc, _) => {

            val (data, x) = dc
            val gpModel   = gpModelPipe(data)
            val y         = gpModel.predictiveDistribution(Seq(x)).draw.toStream.head
            (data :+ (x, y), DenseVector(Array(y) ++ x(0 to -2).toArray))
          }
        )
        .unzip

    u0.toArray.toSeq ++ xsamples.map(_(0))
  })

  //Generate samples from a conventional AR(p) process
  //with coefficients given by w
  val markov_process = (n: Int) =>
    RandomVariable[Seq[Double]](() => {
      val u0 = DenseVector.tabulate(p)(_ => y0.draw)
      val xs_tail = (1 to n - p - 1)
        .scanLeft(
          u0
        )((y: DenseVector[Double], _) => {
          val y_new: Double = quadratic_vec_trend(w)(y) + math.sqrt(0.5)*y0.draw
          DenseVector(Array(y_new) ++ y(0 to -2).toArray)
        })
        .toSeq
        .map(_(0))

      u0.toArray.toSeq ++ xs_tail
    })

  val samples_ar_rec = ys_ar_rec.iid(10).draw.toSeq

  val samples_markov = markov_process(xs.length).iid(10).draw.toSeq

  val plot_legend_labels =
    (1 to num_sample_paths).map(i => s"Path $i").toSeq

  spline(xs, samples_gp_explicit.head)
  hold()
  samples_gp_explicit.tail.foreach((s: Seq[Double]) => spline(xs, s))
  unhold()
  title(
    s"""GP Explicit Time AR($p): ${gp_explicit.covariance.toString
      .split("\\.")
      .last}"""
  )
  legend(plot_legend_labels)

  val tuple_encoder =
    TupleIntegerEncoder(List(p, p + 1))

  val markov_formula = w.toArray.toSeq.zipWithIndex
    .map(
      cp => {
        val indices = tuple_encoder.i(cp._2)

        if (cp._2 < p) {
          if (cp._1 >= 0d && cp._2 != 0) f"+${cp._1}%3.2f*y(t-${cp._2 + 1})"
          else f"${cp._1}%3.2f*y(t-${cp._2 + 1})"
        } else if (cp._1 >= 0d)
          f"+${cp._1}%3.2f*y(t-${indices.head + 1})*y(t-${indices.last + 1})"
        else f"${cp._1}%3.2f*y(t-${indices.head + 1})*y(t-${indices.last + 1})"
      }
    )
    .reduceLeft(_ ++ _)

  spline(xs, samples_markov.head)
  hold()
  samples_markov.tail.foreach((s: Seq[Double]) => spline(xs, s))
  unhold()
  title(
    s"""AR($p): y(t) = ${markov_formula} + noise"""
  )
  legend(plot_legend_labels)

  spline(xs, samples_ar_rec.head)
  hold()
  samples_ar_rec.tail.foreach((s: Seq[Double]) => spline(xs, s))
  unhold()
  title(
    s"""GP-AR Recurrent: ${gpModelPipe.covariance.toString.split("\\.").last}"""
  )
  legend(plot_legend_labels)

  println(s"Linear coefficients: ${w.toArray.toSeq}")

  //Now train a GP-AR model based on the non-linear time series data.
  val train_split = 0.4
  val markov_chain_train_data = dtfdata
    .dataset(samples_markov.tail.flatMap(_.sliding(p + 1).toSeq))
    .map(h => (DenseVector(h.take(p).toArray), h.last))
    .to_supervised(identityPipe[(DenseVector[Double], Double)])

  val markov_chain_first_sample = dtfdata
    .dataset(samples_markov.head.sliding(p + 1).toSeq)
    .map(h => (DenseVector(h.take(p).toArray), h.last))
    .to_supervised(identityPipe[(DenseVector[Double], Double)])
    .partition(train_split)

  val markov_chain_samples = dtfdata.tf_dataset(
    markov_chain_train_data.concatenate(markov_chain_first_sample.training_dataset),
    markov_chain_first_sample.test_dataset
  )

  val test_split_size = markov_chain_samples.test_dataset.size

  gp_prior.globalOptConfig_(
    Map(
      "gridStep"  -> "0.15",
      "gridSize"  -> "2",
      "globalOpt" -> "CSA",
      "policy"    -> "GS",
      "maxIt"     -> "3"
    )
  )

  val gp_nar_model = gp_prior.posteriorModel(
    markov_chain_samples.training_dataset.data.toSeq
  )

  val (test_preds, lower_bar, upper_bar) = gp_nar_model
    .predictionWithErrorBars(
      markov_chain_samples.test_dataset
        .map(tup2_1[DenseVector[Double], Double])
        .data
        .toSeq,
      3
    )
    .map(pattern => (pattern._2, pattern._3, pattern._4))
    .unzip3

  val last_train_sample_index = (train_split * samples_markov.head
    .sliding(p + 1)
    .toSeq
    .length) - 1

  spline(xs.zip(samples_markov.head))
  hold()
  spline(xs.takeRight(test_split_size).zip(test_preds))
  spline(xs.takeRight(test_split_size).zip(lower_bar))
  spline(xs.takeRight(test_split_size).zip(upper_bar))
  unhold()
  title("Time Series Prediction")
  legend(
    Seq("Time Series", "MAP Prediction", "Lower Error Bar", "Upper Error Bar")
  )

}
