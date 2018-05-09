/*
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
* */
package io.github.mandar2812.dynaml

import java.nio.ByteBuffer

import io.github.mandar2812.dynaml.probability._
import io.github.mandar2812.dynaml.pipes._
import io.github.mandar2812.dynaml.tensorflow.utils._
import io.github.mandar2812.dynaml.tensorflow.layers._
import org.platanios.tensorflow.api._
import org.platanios.tensorflow.api.core.Shape
import org.platanios.tensorflow.api.learn.layers.{Activation, Input, Layer}
import org.platanios.tensorflow.api.ops.NN.SamePadding
import org.platanios.tensorflow.api.ops.io.data.Dataset
import org.platanios.tensorflow.api.ops.training.optimizers.Optimizer
import org.platanios.tensorflow.api.tensors.TensorConvertible
import org.platanios.tensorflow.api.types.{DataType, SupportedType}

/**
  * <h3>DynaML Tensorflow Utilities</h3>
  *
  * A collection of functions, transformations and
  * miscellaneous objects to help working with tensorflow
  * primitives and models.
  *
  * @author mandar2812 date: 23/11/2017
  * */
package object tensorflow {

  /**
    * <h4>DynaML Tensorflow Pointer</h4>
    * The [[dtf]] object is the entry point
    * for tensor related operations.
    * */
  object dtf {

    /**
      * Construct a tensor from a list of elements.
      *
      * @tparam T The type of the elements
      *
      * @param dtype The tensorflow data type of the elements,
      *              this is usually defined by tensorflow scala
      *              i.e. FLOAT64, INT32 etc
      *
      * @param shape The shape of the tensor i.e. Shape(1,2,3)
      *              denotes a rank 3 tensor with 1, 2 and 3 dimensions
      *              for the ranks respectively.
      *
      * @param buffer The elements of type [[T]], can accept varying
      *               number of arguments.
      *
      * @return A tensorflow [[Tensor]] of the appropriate data type
      *         and shape.
      *
      * <b>Usage</b> dtf.tensor_from(INT32, Shape(1, 2, 3))(1, 2, 3, 4, 5, 6)
      *
      * */
    def tensor_from[T](dtype: DataType.Aux[T], shape: Shape)(buffer: T*)(implicit ev: TensorConvertible[T]): Tensor = {
      Tensor(dtype, buffer.head, buffer.tail:_*).reshape(shape)
    }

    /**
      * Construct a tensor from a list of elements.
      *
      * @tparam T The type of the elements
      *
      * @param dtype The tensorflow data type of the elements,
      *              as a string i.e. "FLOAT64", "INT32" etc
      *
      * @param shape The shape of the tensor given as any number
      *              of integer arguments.
      *
      * @param buffer The elements of type [[T]], as a Sequence
      *
      * @return A tensorflow [[Tensor]] of the appropriate data type
      *         and shape.
      *
      * <b>Usage</b> dtf.tensor_from("INT32", 1, 2, 3)((1 to 6).toSeq)
      *
      * */
    def tensor_from[T](dtype: String, shape: Int*)(buffer: Seq[T])(implicit ev: TensorConvertible[T]): Tensor = {
      Tensor(DataType.fromName(dtype), buffer.head, buffer.tail:_*).reshape(Shape(shape:_*))
    }

    /**
      * Construct a tensor from a array of bytes.
      *
      * @tparam T The type of the elements
      *
      * @param dtype The tensorflow data type of the elements,
      *              as a string i.e. "FLOAT64", "INT32" etc
      *
      * @param shape The shape of the tensor given as any number
      *              of integer arguments.
      *
      * @param buffer The elements as a contiguous array of bytes
      *
      * @return A tensorflow [[Tensor]] of the appropriate data type
      *         and shape.
      *
      * <b>Usage</b> dtf.tensor_from_buffer(FLOAT32, 1, 1)((1 to 4).toArray.map(_.toByte))
      *
      * */
    def tensor_from_buffer[T](
      dtype: DataType.Aux[T], shape: Shape)(
      buffer: Array[Byte]): Tensor = {
      Tensor.fromBuffer(dtype, shape, buffer.length.toLong, ByteBuffer.wrap(buffer))
    }

    /**
      * Construct a tensor from a array of bytes.
      *
      * @param dtype The tensorflow data type of the elements,
      *              as a string i.e. "FLOAT64", "INT32" etc
      *
      * @param shape The shape of the tensor given as any number
      *              of integer arguments.
      *
      * @param buffer The elements as a contiguous array of bytes
      *
      * @return A tensorflow [[Tensor]] of the appropriate data type
      *         and shape.
      *
      * <b>Usage</b> dtf.tensor_from_buffer("FLOAT32", 1, 1)((1 to 4).toArray.map(_.toByte))
      *
      * */
    def tensor_from_buffer(
      dtype: String, shape: Int*)(
      buffer: Array[Byte]): Tensor =
      Tensor.fromBuffer(
        DataType.fromName(dtype), Shape(shape:_*),
        buffer.length.toLong, ByteBuffer.wrap(buffer))


    /**
      * Construct an 16 bit integer tensor from a list of elements.
      *
      * @param shape The shape of the tensor given as any number
      *              of integer arguments.
      *
      * @param buffer The elements in row major format.
      *
      * @return A tensorflow [[Tensor]] of the appropriate data type
      *         and shape.
      *
      * <b>Usage</b> dtf.tensor_i16(1, 2, 3)(1, 2, 3, 4, 5, 6)
      *
      * */
    def tensor_i16(shape: Int*)(buffer: Int*)(implicit ev: TensorConvertible[Double]) =
      Tensor(INT16, buffer.head, buffer.tail:_*).reshape(shape)

    /**
      * Construct an 32 bit integer tensor from a list of elements.
      *
      * @param shape The shape of the tensor given as any number
      *              of integer arguments.
      *
      * @param buffer The elements in row major format.
      *
      * @return A tensorflow [[Tensor]] of the appropriate data type
      *         and shape.
      *
      * <b>Usage</b> dtf.tensor_i32(1, 2, 3)(1, 2, 3, 4, 5, 6)
      *
      * */
    def tensor_i32(shape: Int*)(buffer: Int*)(implicit ev: TensorConvertible[Double]) =
      Tensor(INT32, buffer.head, buffer.tail:_*).reshape(shape)

    /**
      * Construct an 64 bit integer tensor from a list of elements.
      *
      * @param shape The shape of the tensor given as any number
      *              of integer arguments.
      *
      * @param buffer The elements in row major format.
      *
      * @return A tensorflow [[Tensor]] of the appropriate data type
      *         and shape.
      *
      * <b>Usage</b> dtf.tensor_i64(1, 2, 3)(1, 2, 3, 4, 5, 6)
      *
      * */
    def tensor_i64(shape: Int*)(buffer: Int*)(implicit ev: TensorConvertible[Double]) =
      Tensor(INT64, buffer.head, buffer.tail:_*).reshape(shape)

    /**
      * Construct an 16 bit floating point tensor from a list of elements.
      *
      * @param shape The shape of the tensor given as any number
      *              of integer arguments.
      *
      * @param buffer The elements in row major format.
      *
      * @return A tensorflow [[Tensor]] of the appropriate data type
      *         and shape.
      *
      * <b>Usage</b> dtf.tensor_f16(1, 2, 3)(1.0, 2.0, 3.0, 4.0, 5.0, 6.0)
      *
      * */
    def tensor_f16(shape: Int*)(buffer: Double*)(implicit ev: TensorConvertible[Double]) =
      Tensor(FLOAT16, buffer.head, buffer.tail:_*).reshape(shape)

    /**
      * Construct an 32 bit floating point tensor from a list of elements.
      *
      * @param shape The shape of the tensor given as any number
      *              of integer arguments.
      *
      * @param buffer The elements in row major format.
      *
      * @return A tensorflow [[Tensor]] of the appropriate data type
      *         and shape.
      *
      * <b>Usage</b> dtf.tensor_f32(1, 2, 3)(1.0, 2.0, 3.0, 4.0, 5.0, 6.0)
      *
      * */
    def tensor_f32(shape: Int*)(buffer: Double*)(implicit ev: TensorConvertible[Double]) =
      Tensor(FLOAT32, buffer.head, buffer.tail:_*).reshape(shape)

    /**
      * Construct an 64 bit floating point tensor from a list of elements.
      *
      * @param shape The shape of the tensor given as any number
      *              of integer arguments.
      *
      * @param buffer The elements in row major format.
      *
      * @return A tensorflow [[Tensor]] of the appropriate data type
      *         and shape.
      *
      * <b>Usage</b>  dtf.tensor_f64(1, 2, 3)(1.0, 2.0, 3.0, 4.0, 5.0, 6.0)
      *
      * */
    def tensor_f64(shape: Int*)(buffer: Double*)(implicit ev: TensorConvertible[Double]) =
      Tensor(FLOAT64, buffer.head, buffer.tail:_*).reshape(shape)


    /**
      * Stack a list of tensors, the use must ensure that
      * the shapes of the tensors are appropriate for a stack
      * operation.
      *
      * @param inputs A sequence of tensors.
      *
      * @param axis The axis along which they should be stacked.
      *
      * @return The larger stacked tensor.
      * */
    def stack(inputs: Seq[Tensor], axis: Int = 0) = tfi.stack(inputs, axis)

    /**
      * Split a tensor into a list of tensors.
      * */
    def unstack(input: Tensor, number: Int = -1, axis: Int = 0) = tfi.unstack(input, number, axis)

    def concatenate(inputs: Seq[Tensor], axis: Tensor = 0): Tensor = tfi.concatenate(inputs, axis)

    /**
      * Generate a random tensor with independent and
      * identically distributed elements drawn from a
      * [[RandomVariable]] instance.
      * */
    def random[T](dtype: DataType.Aux[T], shape: Int*)(rv: RandomVariable[T])(implicit ev: TensorConvertible[T])
    : Tensor = {
      val buffer = rv.iid(shape.product).draw
      Tensor(dtype, buffer.head, buffer.tail:_*).reshape(Shape(shape:_*))
    }

    /**
      * Fill a tensor with a fixed value.
      * */
    def fill[T](dataType: DataType, shape: Int*)(value: T)(implicit ev: SupportedType[T]): Tensor =
      Tensor.fill(dataType, Shape(shape:_*))(value)

  }

  /**
    * <h4>DynaML Neural Net Building Blocks</h4>
    *
    * The [[dtflearn]] object contains components
    * that can be used to create custom neural architectures,
    * from basic building blocks.
    * */
  object dtflearn {

    type TFDATA = Dataset[(Tensor, Tensor), (Output, Output), (DataType, DataType), (Shape, Shape)]

    val Phi: layers.Phi.type                        = layers.Phi

    val Tanh: layers.Tanh.type                      = layers.Tanh

    val ctrnn: layers.FiniteHorizonCTRNN.type       = layers.FiniteHorizonCTRNN

    val dctrnn: layers.DynamicTimeStepCTRNN.type    = layers.DynamicTimeStepCTRNN

    val ts_linear: layers.FiniteHorizonLinear.type  = layers.FiniteHorizonLinear

    /**
      * Constructs a feed-forward layer.
      *
      * @param num_units The number of neurons in the layer
      * @param id A unique integer id for constructing the layer name.
      *
      * */
    def feedforward(num_units: Int, useBias: Boolean = true)(id: Int) =
      tf.learn.Linear("Linear_"+id, num_units, useBias)

    /**
      * Constructs a simple feed-forward stack of layers.
      *
      * @param get_act A function which given a layer index number,
      *                returns an activation function.
      *
      * @param dataType The data type of the layer weights/biases.
      *
      * @param layer_sizes A Sequence of layer sizes/dimensions/neuron counts.
      * */
    def feedforward_stack(
      get_act: (Int) => Activation,
      dataType: DataType)(
      layer_sizes: Seq[Int]): Layer[Output, Output] = {

      def stack_ff_layers_rec(
        ls: Seq[Int],
        layer_acc: Layer[Output, Output],
        layer_index: Int): Layer[Output, Output] = ls match {

        case Seq() => layer_acc

        case Seq(num_output_units) => layer_acc >> dtflearn.feedforward(num_output_units)(layer_index)

        case _ => stack_ff_layers_rec(
          ls.tail,
          layer_acc >> dtflearn.feedforward(ls.head)(layer_index) >> get_act(layer_index),
          layer_index + 1)
      }

      stack_ff_layers_rec(layer_sizes, tf.learn.Cast("Input/Cast", dataType), 1)

    }

    /**
      * Constructs a symmetric (square) convolutional layer from the provided dimensions.
      *
      * [[org.platanios.tensorflow.api.ops.NN.SamePadding]] is used as the padding mode.
      *
      * @param size The size of each square filter e.g. 2*2, 3*3 etc
      * @param num_channels_input The number of channels in the input
      * @param num_filters The number of channels in the layer output
      * @param strides A [[Tuple2]] with strides, for each direction i.e. breadth and height.
      * @param index The layer id or index, helps in creating a unique layer name
      * */
    def conv2d(size: Int, num_channels_input: Int, num_filters: Int, strides: (Int, Int))(index: Int) =
      tf.learn.Conv2D(
        "Conv2D_"+index,
        Shape(size, size, num_channels_input, num_filters),
        strides._1, strides._2,
        SamePadding)

    /**
      * Constructs a convolutional layer activated by a ReLU, with
      * an option of appending a dropout layer.
      *
      * */
    def conv2d_unit(
      shape: Shape, stride: (Int, Int) = (1, 1),
      relu_param: Float = 0.1f, dropout: Boolean = true,
      keep_prob: Float = 0.6f)(i: Int) =
      if(dropout) {
        tf.learn.Conv2D("Conv2D_"+i, shape, stride._1, stride._2, SamePadding) >>
          tf.learn.AddBias(name = "Bias_"+i) >>
          tf.learn.ReLU("ReLU_"+i, relu_param) >>
          tf.learn.Dropout("Dropout_"+i, keep_prob)
      } else {
        tf.learn.Conv2D("Conv2D_"+i, shape, stride._1, stride._2, SamePadding) >>
          tf.learn.AddBias(name = "Bias_"+i) >>
          tf.learn.ReLU("ReLU_"+i, relu_param)
      }

    /**
      * Constructs an inverted convolutional pyramid, consisting of
      * stacked versions of [Conv2d --> ReLU --> Dropout] layers.
      *
      * The number of filters learned in each Conv2d layer are
      * arranged in decreasing exponents of 2. They are costructed
      * using calls to [[conv2d_unit()]]
      *
      * ... Conv_unit(128) --> Conv_unit(64) --> Conv_unit(32) --> Conv_unit(16) ...
      *
      * @param size The size of the square convolutional filter to be applied
      *             in each segment.
      * @param num_channels_input The number of channels in the input.
      * @param start_num_bits The exponent of 2 which determines size/depth of the starting layer
      *                       e.g. set to 4 for a depth of 16.
      *
      * @param end_num_bits The exponent of 2 which determines the size/depth of the end layer.
      *
      * @param relu_param The activation barrier of the ReLU activation.
      *
      * @param dropout Set to true, if dropout units should be placed in each unit.
      *
      * @param keep_prob If dropout is enabled, then this determines the retain probability.
      * */
    def conv2d_pyramid(
      size: Int, num_channels_input: Int)(
      start_num_bits: Int, end_num_bits: Int)(
      relu_param: Float = 0.1f, dropout: Boolean = true,
      keep_prob: Float = 0.6f) = {

      require(
        start_num_bits > end_num_bits,
        "To construct a 2d-convolutional pyramid, you need to start_num_bits > end_num_bits")

      //Create the first layer segment.
      val head_segment = conv2d_unit(
        Shape(size, size, num_channels_input, math.pow(2, start_num_bits).toInt),
        stride = (1, 1), relu_param, dropout, keep_prob)(0)

      //Create the rest of the pyramid
      val tail_segments = (end_num_bits until start_num_bits).reverse.zipWithIndex.map(bitsAndIndices => {
        val (bits, index) = bitsAndIndices

        conv2d_unit(
          Shape(size, size, math.pow(2, bits+1).toInt, math.pow(2, bits).toInt),
          stride = (math.pow(2, index+1).toInt, math.pow(2, index+1).toInt),
          relu_param, dropout, keep_prob)(index+1)

      }).reduceLeft((a,b) => a >> b)

      //Join head to tail.
      head_segment >> tail_segments
    }

    /**
      * Constructs a Continuous Time Recurrent Neural Network (CTRNN) Layer, consisting
      * of some latent states, composed with a linear projection into the space of observables.
      *
      * @param states The number of states in the CTRNN
      * @param observables The dimensionality of the output space.
      * @param timestep The integration time step, if set to 0 or a negative
      *                 value, create a [[DynamicTimeStepCTRNN]].
      * @param horizon The number of steps in time to simulate the dynamical system
      * @param index The layer index, should be unique.
      * */
    def ctrnn_block(
      states: Int, observables: Int,
      horizon: Int, timestep: Double = -1d)(index: Int) =
      if (timestep <= 0d) {
        DynamicTimeStepCTRNN(s"FHctrnn_$index", states, horizon) >>
          FiniteHorizonLinear(s"FHlinear_$index", states, observables, horizon)
      } else {
        FiniteHorizonCTRNN(s"FHctrnn_$index", states, horizon, timestep) >>
          FiniteHorizonLinear(s"FHlinear_$index", states, observables, horizon)
      }

    /**
      * Trains a tensorflow model/estimator
      *
      * @tparam I The underlying data type of the input.
      * @tparam O The underlying data type of the output label.
      * @param architecture The network architecture
      * @param input The input meta data.
      * @param trainInput The output label meta data
      * @param trainingInputLayer A computation layer which converts
      *                           the original input into a format usable
      *                           by the Estimator API
      * @param loss The loss function to be optimized during training.
      * @param optimizer The optimization algorithm implementation.
      * @param summariesDir A filesystem path of type [[java.nio.file.Path]], which
      *                     determines where the intermediate model parameters/checkpoints
      *                     will be written.
      * @param iterations The maximum number of iterations.
      * @param training_data A training data set, as an instance of [[Dataset]].
      *
      * @return A [[Tuple2]] containing the model and estimator.
      * */
    def build_tf_model[I, O](
      architecture: Layer[Output, Output],
      input: Input[Tensor, Output, DataType.Aux[I], DataType, Shape],
      trainInput: Input[Tensor, Output, DataType.Aux[O], DataType, Shape],
      trainingInputLayer: Layer[Output, Output],
      loss: Layer[(Output, Output), Output],
      optimizer: Optimizer,
      summariesDir: java.nio.file.Path,
      iterations: Int)(
      training_data: TFDATA) = {

      val (model, estimator) = tf.createWith(graph = Graph()) {
        val model = tf.learn.Model(
          input, architecture,
          trainInput, trainingInputLayer,
          loss, optimizer)

        println("\nTraining the regression model.\n")

        val estimator = tf.learn.FileBasedEstimator(
          model,
          tf.learn.Configuration(Some(summariesDir)),
          tf.learn.StopCriteria(maxSteps = Some(iterations)),
          Set(
            tf.learn.StepRateLogger(
              log = false, summaryDir = summariesDir,
              trigger = tf.learn.StepHookTrigger(5000)),
            tf.learn.SummarySaver(summariesDir, tf.learn.StepHookTrigger(5000)),
            tf.learn.CheckpointSaver(summariesDir, tf.learn.StepHookTrigger(5000))),
          tensorBoardConfig = tf.learn.TensorBoardConfig(summariesDir, reloadInterval = 5000))

        estimator.train(() => training_data, tf.learn.StopCriteria(maxSteps = Some(iterations)))

        (model, estimator)
      }

      (model, estimator)
    }

  }

  /**
    * <h4>DynaML Tensorflow Pipes</h4>
    *
    * The [[dtfpipe]] contains workflows/pipelines to simplify working
    * with tensorflow data sets and models.
    * */
  object dtfpipe {

    val gaussian_standardization: DataPipe2[Tensor, Tensor, ((Tensor, Tensor), (GaussianScalerTF, GaussianScalerTF))] =
      DataPipe2((features: Tensor, labels: Tensor) => {

        val (features_mean, labels_mean) = (features.mean(axes = 0), labels.mean(axes = 0))

        val n_data = features.shape(0).scalar.asInstanceOf[Int].toDouble

        val (features_sd, labels_sd) = (
          features.subtract(features_mean).square.mean(axes = 0).multiply(n_data/(n_data - 1d)).sqrt,
          labels.subtract(labels_mean).square.mean(axes = 0).multiply(n_data/(n_data - 1d)).sqrt
        )

        val (features_scaler, labels_scaler) = (
          GaussianScalerTF(features_mean, features_sd),
          GaussianScalerTF(labels_mean, labels_sd)
        )

        val (features_scaled, labels_scaled) = (
          features_scaler(features),
          labels_scaler(labels)
        )

        ((features_scaled, labels_scaled), (features_scaler, labels_scaler))
      })

    val minmax_standardization: DataPipe2[Tensor, Tensor, ((Tensor, Tensor), (MinMaxScalerTF, MinMaxScalerTF))] =
      DataPipe2((features: Tensor, labels: Tensor) => {

        val (features_min, labels_min) = (features.min(axes = 0), labels.min(axes = 0))

        val (features_max, labels_max) = (
          features.max(axes = 0),
          labels.max(axes = 0)
        )

        val (features_scaler, labels_scaler) = (
          MinMaxScalerTF(features_min, features_max),
          MinMaxScalerTF(labels_min, labels_max)
        )

        val (features_scaled, labels_scaled) = (
          features_scaler(features),
          labels_scaler(labels)
        )

        ((features_scaled, labels_scaled), (features_scaler, labels_scaler))
      })

    val gauss_minmax_standardization: DataPipe2[Tensor, Tensor, ((Tensor, Tensor), (GaussianScalerTF, MinMaxScalerTF))] =
      DataPipe2((features: Tensor, labels: Tensor) => {

        val features_mean = features.mean(axes = 0)

        val (labels_min, labels_max) = (labels.min(axes = 0), labels.max(axes = 0))

        val n_data = features.shape(0).scalar.asInstanceOf[Int].toDouble

        val features_sd = 
          features.subtract(features_mean).square.mean(axes = 0).multiply(n_data/(n_data - 1d)).sqrt

        val (features_scaler, labels_scaler) = (
          GaussianScalerTF(features_mean, features_sd),
          MinMaxScalerTF(labels_min, labels_max)
        )

        val (features_scaled, labels_scaled) = (
          features_scaler(features),
          labels_scaler(labels)
        )

        ((features_scaled, labels_scaled), (features_scaler, labels_scaler))
      })

  }

}
