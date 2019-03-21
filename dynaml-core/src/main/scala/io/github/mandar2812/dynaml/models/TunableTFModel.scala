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
package io.github.mandar2812.dynaml.models

import io.github.mandar2812.dynaml.optimization.GloballyOptimizable
import io.github.mandar2812.dynaml.utils
import io.github.mandar2812.dynaml.evaluation.Performance
import io.github.mandar2812.dynaml.pipes.{DataPipe, DataPipe2, MetaPipe}
import io.github.mandar2812.dynaml.tensorflow.data.{DataSet, TFDataSet}
import ammonite.ops._
import org.platanios.tensorflow.api.Graph
import org.platanios.tensorflow.api.implicits.helpers._
import org.platanios.tensorflow.api.learn.estimators.Estimator
import org.platanios.tensorflow.api.learn.layers.{Input, Layer}
import org.platanios.tensorflow.api.ops.{Function, Output}
import org.json4s._
import org.json4s.jackson.Serialization.{read => read_json, write => write_json}
import org.platanios.tensorflow.api.core.types.{IsFloatOrDouble, TF}

/**
  * <h4>Hyper-parameter based Tensorflow Model</h4>
  *
  * @tparam IT The type representing input tensors,
  *            e.g. `Tensor`, `(Tensor, Tensor)`, `Seq[Tensor]`  etc.
  * @tparam In The type representing symbolic tensors of the input patterns,
  *            e.g. `Output`, `(Output, Output)`, `Seq[Output]` etc.
  * @tparam ID The input pattern's TensorFlow data type,
  *            e.g. `FLOAT64`, `(FLOAT64, FLOAT64)`, etc.
  * @tparam IS The type of the input pattern's shape,
  *            e.g. `Shape`, `(Shape, Shape)`, `Seq[Shape]`
  * @tparam ArchOut The type of the symbolic tensor returned by the neural architecture,
  *           e.g. `Output`, `(Output, Output)`, `Seq[Output]`
  * @tparam TT The type representing target/label tensors,
  *            e.g. `Tensor`, `(Tensor, Tensor)`, `Seq[Tensor]`  etc.
  * @tparam Out The type representing symbolic tensors of the target patterns,
  *            e.g. `Output`, `(Output, Output)`, `Seq[Output]` etc.
  * @tparam TD The target pattern's TensorFlow data type,
  *            e.g. `FLOAT64`, `(FLOAT64, FLOAT64)`, etc.
  * @tparam TS The type of the target pattern's shape,
  *            e.g. `Shape`, `(Shape, Shape)`, `Seq[Shape]`
  *
  * @param modelFunction A DynaML [[MetaPipe]] (Curried Function) which takes the
  *                      hyper-parameters as input, returns a [[DataPipe]] which
  *                      outputs a [[TFModel]] given some training data.
  *
  * @param hyp_params The string identifiers of the hyper-parameters.
  *
  * @param training_data Training data, as a DynaML [[DataSet]].
  *
  * @param fitness_function A [[DataPipe2]] which takes a prediction-target tuple and outputs a
  *                         performance metric or fitness value. Ideally this value
  *                         should follow the "Lesser is better" paradigm.
  *
  * @param validation_data A validation data set, defaults to [[None]]. This data set
  *                        need not be specified if the [[data_split_func]] is defined.
  *
  * @param data_split_func A [[DataPipe]] which splits the [[training_data]] into train and
  *                        validation splits, defaults to [[None]]. This need not be specified
  *                        if [[validation_data]] is defined.
  *
  * */
class TunableTFModel[Pattern, In, Out, ArchOut, Loss: TF : IsFloatOrDouble, IT, ID, IS, TT, TD, TS, ITT, IDD, ISS](
  val modelFunction: TunableTFModel.ModelFunc[In, Out, ArchOut, Loss, IT, ID, IS, TT, TD, TS, ITT, IDD, ISS],
  val modelConfigFunc: TunableTFModel.ModelConfigFunc[IT, TT, ITT, In, Out],
  val hyp_params: Seq[String],
  protected val training_data: DataSet[Pattern],
  val convert_to_tensor: DataPipe[Pattern, (IT, TT)],
  val fitness_function: DataPipe2[ArchOut, Out, Output[Float]],
  protected val validation_data: Option[DataSet[Pattern]] = None,
  protected val data_split_func: Option[DataPipe[Pattern, Boolean]] = None)
  extends GloballyOptimizable {

  //Implicit required by the json4s library for reading and writing json
  implicit protected val formats: Formats = DefaultFormats

  override protected var hyper_parameters: List[String] = hyp_params.toList

  override protected var current_state: TunableTFModel.HyperParams = Map()

  protected def _data_splits: TFDataSet[Pattern] = {

    require(
      validation_data.isDefined || data_split_func.isDefined,
      "If validation data is not explicitly provided, then data_split_func must be defined")

    if(validation_data.isEmpty) training_data.partition(data_split_func.get)
    else TFDataSet(training_data, validation_data.get)

  }

  /**
    * Calculates the energy of the configuration,
    * in most global optimization algorithms
    * we aim to find an approximate value of
    * the hyper-parameters such that this function
    * is minimized.
    *
    * @param h       The value of the hyper-parameters in the configuration space
    * @param options Optional parameters about configuration
    * @return Configuration Energy E(h)
    **/
  override def energy(h: TunableTFModel.HyperParams, options: Map[String, String]): Double = {

    //Check that all the model hyper-parameters are contained in
    //the input `h`
    require(
      hyp_params.forall(h.contains),
      s"All hyper-parameters: [$hyp_params] of the model, must be contained in the input `h` when calling energy(h)")

    //Set the current state to `h`
    current_state = h

    //Obtain training and validation data splits
    val TFDataSet(train_split, validation_split) = _data_splits

    //Separate the validation data inputs and outputs
    val (validation_inputs, validation_targets) = (
      validation_split.map(convert_to_tensor).map(DataPipe((c: (IT, TT)) => c._1)),
      validation_split.map(convert_to_tensor).map(DataPipe((c: (IT, TT)) => c._2))
    )


    //Get the model instance.
    val model_instance = modelFunction(h)
    val train_config   = modelConfigFunc(h)

    val fitness_metric = Performance[(ArchOut, (In, Out))](
      "Energy", DataPipe[(ArchOut, (In, Out)), Output[Float]](c => fitness_function(c._1, c._2._2))
    )

    //Compute the model fitness, guard against weird exceptions
    val (fitness, comment) = try {
      //Train the model instance
      model_instance.train(train_split.map(convert_to_tensor), train_config)

      //Dont shuffle and repeat the data set when performing validation 
      val computed_energy = model_instance.evaluate(
        validation_split.map(convert_to_tensor), 
        Seq(fitness_metric), 
        train_config.data_processing.copy(shuffleBuffer = 0, repeat = 0)
      ).head.scalar.toDouble 
      
      //If all goes well, return the fitness and no comment.
      (computed_energy, None)
    } catch {
      case e: java.lang.IllegalStateException => (Double.PositiveInfinity, Some(e.getMessage))
      case e: Throwable =>
        e.printStackTrace()
        (Double.PositiveInfinity, Some(e.getMessage))
    } finally {
      model_instance.close()
    }

    //Append the model fitness to the hyper-parameter configuration
    val hyp_config_json = write_json(h ++ Map("energy" -> fitness, "comment" -> comment.getOrElse("")))

    //Write the configuration along with its fitness into the model
    //instance's summary directory
    write(
      train_config.summaryDir/"state.json",
      hyp_config_json)

    //Return the model fitness.
    fitness
  }
}

object TunableTFModel {

  type HyperParams = Map[String, Double]

  /**
    * Type-alias for "Model Functions".
    *
    * Model Functions take hyper-parameters as input
    * and return an instantiated TensorFlow Model [[TFModel]].
    *
    * */
  type ModelFunc[In, Out, ArchOut, Loss, IT, ID, IS, TT, TD, TS, ITT, IDD, ISS] = DataPipe[
    HyperParams,
    TFModel[In, Out, ArchOut, Loss, IT, ID, IS, TT, TD, TS, ITT, IDD, ISS]
    ]

  type ModelConfigFunc[IT, TT, ITT, In, Out] = DataPipe[HyperParams, TFModel.Config[IT, TT, ITT, In, Out]]


  /**
    * <h4>Model Functions</h4>
    *
    * Helpful methods for creating [[ModelFunc]] instances, which
    * are needed for creating [[TunableTFModel]].
    * */
  object ModelFunction {


    val config_to_str: DataPipe[Map[String, Double], String] = DataPipe(_.map(c => s"${c._1}_${c._2}").mkString("-"))
    val generate_token: DataPipe[String, String]             = DataPipe(utils.tokenGenerator.generateMD5Token)

    val to_token = config_to_str > generate_token

    private def get_summary_dir(
      top_dir: Path,
      h: HyperParams,
      create_working_dir: Option[DataPipe[HyperParams, String]] = Some(to_token)): Path = create_working_dir match {
      case None => top_dir
      case Some(working_dir_gen) => top_dir/working_dir_gen(h)
    }

    val hyper_params_to_dir: MetaPipe[Path, HyperParams, Path] = MetaPipe(top_dir => h => get_summary_dir(top_dir, h))

    /**
      * Create a [[ModelFunc]] from a "loss generator".
      *
      * @param loss_gen A function which takes the [[HyperParams]] and creates
      *                 the Loss function.
      *
      * @param architecture The model architecture.
      *
      * @param input Data type and shape of the model inputs.
      *
      * @param target Data type and shape of the model outputs/training labels.
      *
      *
      * @param get_training_config A data pipe which generates a [[TFModel.Config]]
      *                            object from some hyper-parameter assignment.
      *
      * @param data_processing An instance of type [[TFModel.DataOps]], contains details
      *                        on the data processing pipeline to be applied.
      *
      * @param inMemory Set to true if the model should be entirely in memory. Defaults
      *                 to false.
      *
      * @param existingGraph Defaults to None, set this parameter if the model should
      *                      be created in an existing TensorFlow graph.
      *
      * @param data_handles Defaults to None, set this parameter if you wish to instantiate
      *                     the model input-output handles.
      * */
    def from_loss_generator[In, Out, ArchOut, Loss: TF : IsFloatOrDouble, IT, ID, IS, TT, TD, TS, ITT, IDD, ISS](
      loss_gen: HyperParams => Layer[(ArchOut, Out), Output[Loss]],
      architecture: Layer[In, ArchOut],
      input: (ID, IS),
      target: (TD, TS),
      inMemory: Boolean = false,
      existingGraph: Option[Graph] = None,
      data_handles: Option[TFModel.DataHandles[In, Out]] = None)(
      implicit
      evDataTypeToOutputI: DataTypeToOutput.Aux[ID, In],
      evDataTypeToOutputT: DataTypeToOutput.Aux[TD, Out],
      evTensorToOutput: TensorToOutput.Aux[(IT, TT), (In, Out)],
      evTensorToDataType: TensorToDataType.Aux[(IT, TT), (ID, TD)],
      evTensorToShape: TensorToShape.Aux[(IT, TT), (IS, TS)],
      evOutputToDataTypeI: OutputToDataType.Aux[In, ID],
      evOutputToDataTypeT: OutputToDataType.Aux[Out, TD],
      evOutputToDataType: OutputToDataType.Aux[(In, Out), (ID, TD)],
      evDataTypeToShape: DataTypeToShape.Aux[(ID, TD), (IS, TS)],
      evOutputToShapeI: OutputToShape.Aux[In, IS],
      evOutputToShapeT: OutputToShape.Aux[Out, TS],
      evOutputToShape: OutputToShape.Aux[(In, Out), (IS, TS)],
      evOutputStructure: OutputStructure[(In, Out)],
      evOutputStructureArchOut: OutputStructure[ArchOut],
      evOutputStructureI: OutputStructure[In],
      evOutputToDataTypeOut: OutputToDataType.Aux[ArchOut, IDD],
      evOutputToShapeOut: OutputToShape.Aux[ArchOut, ISS],
      evOutputToTensorOut: OutputToTensor.Aux[ArchOut, ITT],
      evOutputToTensorIn: OutputToTensor.Aux[In, IT],
      ev: Estimator.SupportedInferInput[In, IT, ITT, IT, ITT],
      // This implicit helps the Scala 2.11 compiler.
      evOutputToTensorInOut: OutputToTensor.Aux[(In, ArchOut), (IT, ITT)])
    : ModelFunc[In, Out, ArchOut, Loss, IT, ID, IS, TT, TD, TS, ITT, IDD, ISS] = {

      DataPipe((h: TunableTFModel.HyperParams) => TFModel(
        architecture, input, target, loss_gen(h),
        inMemory, existingGraph, data_handles)
      )
    }

    /**
      * Create a [[ModelFunc]] from a "architecture-loss generator".
      *
      * @param arch_loss_gen A function which takes the [[HyperParams]] and creates
      *                      an architecture-loss tuple.
      *
      * @param input Data type and shape of the model inputs.
      *
      * @param target Data type and shape of the model outputs/training labels.
      *
      *
      * @param get_training_config A data pipe which generates a [[TFModel.Config]]
      *                            object from some hyper-parameter assignment.
      *
      * @param data_processing An instance of type [[TFModel.DataOps]], contains details
      *                        on the data processing pipeline to be applied.
      *
      * @param inMemory Set to true if the model should be entirely in memory. Defaults
      *                 to false.
      *
      * @param existingGraph Defaults to None, set this parameter if the model should
      *                      be created in an existing TensorFlow graph.
      *
      * @param data_handles Defaults to None, set this parameter if you wish to instantiate
      *                     the model input-output handles.
      * */
    def from_arch_loss_generator[In, Out, ArchOut, Loss: TF : IsFloatOrDouble, IT, ID, IS, TT, TD, TS, ITT, IDD, ISS](
      arch_loss_gen: HyperParams => (Layer[In, ArchOut], Layer[(ArchOut, Out), Output[Loss]]),
      input: (ID, IS),
      target: (TD, TS),
      inMemory: Boolean = false,
      existingGraph: Option[Graph] = None,
      data_handles: Option[TFModel.DataHandles[In, Out]] = None)(
      implicit
      evDataTypeToOutputI: DataTypeToOutput.Aux[ID, In],
      evDataTypeToOutputT: DataTypeToOutput.Aux[TD, Out],
      evTensorToOutput: TensorToOutput.Aux[(IT, TT), (In, Out)],
      evTensorToDataType: TensorToDataType.Aux[(IT, TT), (ID, TD)],
      evTensorToShape: TensorToShape.Aux[(IT, TT), (IS, TS)],
      evOutputToDataTypeI: OutputToDataType.Aux[In, ID],
      evOutputToDataTypeT: OutputToDataType.Aux[Out, TD],
      evOutputToDataType: OutputToDataType.Aux[(In, Out), (ID, TD)],
      evDataTypeToShape: DataTypeToShape.Aux[(ID, TD), (IS, TS)],
      evOutputToShapeI: OutputToShape.Aux[In, IS],
      evOutputToShapeT: OutputToShape.Aux[Out, TS],
      evOutputToShape: OutputToShape.Aux[(In, Out), (IS, TS)],
      evOutputStructure: OutputStructure[(In, Out)],
      evOutputStructureArchOut: OutputStructure[ArchOut],
      evOutputStructureI: OutputStructure[In],
      evOutputToDataTypeOut: OutputToDataType.Aux[ArchOut, IDD],
      evOutputToShapeOut: OutputToShape.Aux[ArchOut, ISS],
      evOutputToTensorOut: OutputToTensor.Aux[ArchOut, ITT],
      evOutputToTensorIn: OutputToTensor.Aux[In, IT],
      ev: Estimator.SupportedInferInput[In, IT, ITT, IT, ITT],
      // This implicit helps the Scala 2.11 compiler.
      evOutputToTensorInOut: OutputToTensor.Aux[(In, ArchOut), (IT, ITT)])
    : ModelFunc[In, Out, ArchOut, Loss, IT, ID, IS, TT, TD, TS, ITT, IDD, ISS] = {

      DataPipe(
        (h: TunableTFModel.HyperParams) => {

            val (architecture, loss) = arch_loss_gen(h)

            TFModel(
              architecture, input, target, loss,
              inMemory, existingGraph, data_handles)
          }
      )

    }


    /**
      * Create a [[ModelFunc]] from a "architecture generator".
      *
      * @param arch_generator A function which takes the [[HyperParams]] and creates
      *                       a neural architecture.
      *
      * @param input Data type and shape of the model inputs.
      *
      * @param target Data type and shape of the model outputs/training labels.
      *
      *
      * @param get_training_config A data pipe which generates a [[TFModel.Config]]
      *                            object from some hyper-parameter assignment.
      *
      * @param data_processing An instance of type [[TFModel.DataOps]], contains details
      *                        on the data processing pipeline to be applied.
      *
      * @param inMemory Set to true if the model should be entirely in memory. Defaults
      *                 to false.
      *
      * @param existingGraph Defaults to None, set this parameter if the model should
      *                      be created in an existing TensorFlow graph.
      *
      * @param data_handles Defaults to None, set this parameter if you wish to instantiate
      *                     the model input-output handles.
      * */
    def from_arch_generator[In, Out, ArchOut, Loss: TF : IsFloatOrDouble, IT, ID, IS, TT, TD, TS, ITT, IDD, ISS](
      arch_generator: HyperParams => Layer[In, ArchOut],
      loss: Layer[(ArchOut, Out), Output[Loss]],
      input: (ID, IS),
      target: (TD, TS),
      inMemory: Boolean = false,
      existingGraph: Option[Graph] = None,
      data_handles: Option[TFModel.DataHandles[In, Out]] = None)(
      implicit
      evDataTypeToOutputI: DataTypeToOutput.Aux[ID, In],
      evDataTypeToOutputT: DataTypeToOutput.Aux[TD, Out],
      evTensorToOutput: TensorToOutput.Aux[(IT, TT), (In, Out)],
      evOutputToDataTypeI: OutputToDataType.Aux[In, ID],
      evOutputToDataTypeT: OutputToDataType.Aux[Out, TD],
      evOutputToDataType: OutputToDataType.Aux[(In, Out), (ID, TD)],
      evTensorToDataType: TensorToDataType.Aux[(IT, TT), (ID, TD)],
      evTensorToShape: TensorToShape.Aux[(IT, TT), (IS, TS)],
      evDataTypeToShape: DataTypeToShape.Aux[(ID, TD), (IS, TS)],
      evOutputToShapeI: OutputToShape.Aux[In, IS],
      evOutputToShapeT: OutputToShape.Aux[Out, TS],
      evOutputToShape: OutputToShape.Aux[(In, Out), (IS, TS)],
      evOutputStructure: OutputStructure[(In, Out)],
      evOutputStructureArchOut: OutputStructure[ArchOut],
      evOutputStructureI: OutputStructure[In],
      evOutputToDataTypeOut: OutputToDataType.Aux[ArchOut, IDD],
      evOutputToShapeOut: OutputToShape.Aux[ArchOut, ISS],
      evOutputToTensorOut: OutputToTensor.Aux[ArchOut, ITT],
      evOutputToTensorIn: OutputToTensor.Aux[In, IT],
      ev: Estimator.SupportedInferInput[In, IT, ITT, IT, ITT],
      // This implicit helps the Scala 2.11 compiler.
      evOutputToTensorInOut: OutputToTensor.Aux[(In, ArchOut), (IT, ITT)])
    : ModelFunc[In, Out, ArchOut, Loss, IT, ID, IS, TT, TD, TS, ITT, IDD, ISS] = {

      DataPipe(
        (h: TunableTFModel.HyperParams) => {

            val architecture = arch_generator(h)



                TFModel(
                  architecture, input, target, loss,
                  inMemory, existingGraph, data_handles
                )
              }
          )

        }

  }

  def apply[Pattern, In, Out, ArchOut, Loss: TF : IsFloatOrDouble, IT, ID, IS, TT, TD, TS, ITT, IDD, ISS](
    loss_func_gen: HyperParams => Layer[(ArchOut, Out), Output[Loss]],
    hyp: List[String],
    training_data: DataSet[Pattern],
    convert_to_tensor: DataPipe[Pattern, (IT, TT)],
    fitness_function: DataPipe2[ArchOut, Out, Output[Float]],
    architecture: Layer[In, ArchOut],
    input: (ID, IS),
    target: (TD, TS),
    get_training_config: ModelConfigFunc[IT, TT, ITT, In, Out],
    validation_data: Option[DataSet[Pattern]] = None,
    data_split_func: Option[DataPipe[Pattern, Boolean]] = None,
    inMemory: Boolean = false,
    existingGraph: Option[Graph] = None,
    data_handles: Option[TFModel.DataHandles[In, Out]] = None)(
    implicit
    evDataTypeToOutputI: DataTypeToOutput.Aux[ID, In],
    evDataTypeToOutputT: DataTypeToOutput.Aux[TD, Out],
    evTensorToOutput: TensorToOutput.Aux[(IT, TT), (In, Out)],
    evTensorToDataType: TensorToDataType.Aux[(IT, TT), (ID, TD)],
    evTensorToShape: TensorToShape.Aux[(IT, TT), (IS, TS)],
    evOutputToDataTypeI: OutputToDataType.Aux[In, ID],
    evOutputToDataTypeT: OutputToDataType.Aux[Out, TD],
    evOutputToDataType: OutputToDataType.Aux[(In, Out), (ID, TD)],
    evDataTypeToShape: DataTypeToShape.Aux[(ID, TD), (IS, TS)],
    evOutputToShapeI: OutputToShape.Aux[In, IS],
    evOutputToShapeT: OutputToShape.Aux[Out, TS],
    evOutputToShape: OutputToShape.Aux[(In, Out), (IS, TS)],
    evOutputStructure: OutputStructure[(In, Out)],
    evOutputStructureArchOut: OutputStructure[ArchOut],
    evOutputStructureI: OutputStructure[In],
    evOutputToDataTypeOut: OutputToDataType.Aux[ArchOut, IDD],
    evOutputToShapeOut: OutputToShape.Aux[ArchOut, ISS],
    evOutputToTensorOut: OutputToTensor.Aux[ArchOut, ITT],
    evOutputToTensorIn: OutputToTensor.Aux[In, IT],
    ev: Estimator.SupportedInferInput[In, IT, ITT, IT, ITT],
    // This implicit helps the Scala 2.11 compiler.
    evOutputToTensorInOut: OutputToTensor.Aux[(In, ArchOut), (IT, ITT)])
  : TunableTFModel[Pattern, In, Out, ArchOut, Loss, IT, ID, IS, TT, TD, TS, ITT, IDD, ISS] = {

    val modelFunc = ModelFunction.from_loss_generator(
      loss_func_gen, architecture, input, target, inMemory,
      existingGraph, data_handles
    )

    new TunableTFModel[Pattern, In, Out, ArchOut, Loss, IT, ID, IS, TT, TD, TS, ITT, IDD, ISS](
      modelFunc, get_training_config,
      hyp, training_data,
      convert_to_tensor,
      fitness_function,
      validation_data,
      data_split_func
    )

  }

  def apply[Pattern, In, Out, ArchOut, Loss: TF : IsFloatOrDouble, IT, ID, IS, TT, TD, TS, ITT, IDD, ISS](
    arch_loss_gen: HyperParams => (Layer[In, ArchOut], Layer[(ArchOut, Out), Output[Loss]]),
    hyp: List[String],
    training_data: DataSet[Pattern],
    convert_to_tensor: DataPipe[Pattern, (IT, TT)],
    fitness_function: DataPipe2[ArchOut, Out, Output[Float]],
    input: (ID, IS),
    target: (TD, TS),
    get_training_config: ModelConfigFunc[IT, TT, ITT, In, Out],
    validation_data: Option[DataSet[Pattern]],
    data_split_func: Option[DataPipe[Pattern, Boolean]],
    inMemory: Boolean,
    existingGraph: Option[Graph],
    data_handles: Option[TFModel.DataHandles[In, Out]])(
    implicit
    evDataTypeToOutputI: DataTypeToOutput.Aux[ID, In],
    evDataTypeToOutputT: DataTypeToOutput.Aux[TD, Out],
    evTensorToOutput: TensorToOutput.Aux[(IT, TT), (In, Out)],
    evOutputToDataTypeI: OutputToDataType.Aux[In, ID],
    evOutputToDataTypeT: OutputToDataType.Aux[Out, TD],
    evOutputToDataType: OutputToDataType.Aux[(In, Out), (ID, TD)],
    evTensorToDataType: TensorToDataType.Aux[(IT, TT), (ID, TD)],
    evTensorToShape: TensorToShape.Aux[(IT, TT), (IS, TS)],
    evDataTypeToShape: DataTypeToShape.Aux[(ID, TD), (IS, TS)],
    evOutputToShapeI: OutputToShape.Aux[In, IS],
    evOutputToShapeT: OutputToShape.Aux[Out, TS],
    evOutputToShape: OutputToShape.Aux[(In, Out), (IS, TS)],
    evOutputStructure: OutputStructure[(In, Out)],
    evOutputStructureArchOut: OutputStructure[ArchOut],
    evOutputStructureI: OutputStructure[In],
    evOutputToDataTypeOut: OutputToDataType.Aux[ArchOut, IDD],
    evOutputToShapeOut: OutputToShape.Aux[ArchOut, ISS],
    evOutputToTensorOut: OutputToTensor.Aux[ArchOut, ITT],
    evOutputToTensorIn: OutputToTensor.Aux[In, IT],
    ev: Estimator.SupportedInferInput[In, IT, ITT, IT, ITT],
    // This implicit helps the Scala 2.11 compiler.
    evOutputToTensorInOut: OutputToTensor.Aux[(In, ArchOut), (IT, ITT)])
  : TunableTFModel[Pattern, In, Out, ArchOut, Loss, IT, ID, IS, TT, TD, TS, ITT, IDD, ISS] = {

    val modelFunc = ModelFunction.from_arch_loss_generator(
      arch_loss_gen, input, target,
      inMemory, existingGraph, data_handles
    )

    new TunableTFModel[Pattern, In, Out, ArchOut, Loss, IT, ID, IS, TT, TD, TS, ITT, IDD, ISS](
      modelFunc, get_training_config,
      hyp, training_data,
      convert_to_tensor,
      fitness_function,
      validation_data,
      data_split_func
    )

  }

  def apply[Pattern, In, Out, ArchOut, Loss: TF : IsFloatOrDouble, IT, ID, IS, TT, TD, TS, ITT, IDD, ISS](
    arch_gen: HyperParams => Layer[In, ArchOut],
    loss: Layer[(ArchOut, Out), Output[Loss]],
    hyp: List[String],
    training_data: DataSet[Pattern],
    convert_to_tensor: DataPipe[Pattern, (IT, TT)],
    fitness_function: DataPipe2[ArchOut, Out, Output[Float]],
    input: (ID, IS),
    target: (TD, TS),
    get_training_config: ModelConfigFunc[IT, TT, ITT, In, Out],
    validation_data: Option[DataSet[Pattern]],
    data_split_func: Option[DataPipe[Pattern, Boolean]],
    inMemory: Boolean,
    existingGraph: Option[Graph],
    data_handles: Option[TFModel.DataHandles[In, Out]])(
    implicit
    evDataTypeToOutputI: DataTypeToOutput.Aux[ID, In],
    evDataTypeToOutputT: DataTypeToOutput.Aux[TD, Out],
    evTensorToOutput: TensorToOutput.Aux[(IT, TT), (In, Out)],
    evTensorToDataType: TensorToDataType.Aux[(IT, TT), (ID, TD)],
    evTensorToShape: TensorToShape.Aux[(IT, TT), (IS, TS)],
    evOutputToDataTypeI: OutputToDataType.Aux[In, ID],
    evOutputToDataTypeT: OutputToDataType.Aux[Out, TD],
    evOutputToDataType: OutputToDataType.Aux[(In, Out), (ID, TD)],
    evDataTypeToShape: DataTypeToShape.Aux[(ID, TD), (IS, TS)],
    evOutputToShapeI: OutputToShape.Aux[In, IS],
    evOutputToShapeT: OutputToShape.Aux[Out, TS],
    evOutputToShape: OutputToShape.Aux[(In, Out), (IS, TS)],
    evOutputStructure: OutputStructure[(In, Out)],
    evOutputStructureArchOut: OutputStructure[ArchOut],
    evOutputStructureI: OutputStructure[In],
    evOutputToDataTypeOut: OutputToDataType.Aux[ArchOut, IDD],
    evOutputToShapeOut: OutputToShape.Aux[ArchOut, ISS],
    evOutputToTensorOut: OutputToTensor.Aux[ArchOut, ITT],
    evOutputToTensorIn: OutputToTensor.Aux[In, IT],
    ev: Estimator.SupportedInferInput[In, IT, ITT, IT, ITT],
    // This implicit helps the Scala 2.11 compiler.
    evOutputToTensorInOut: OutputToTensor.Aux[(In, ArchOut), (IT, ITT)])
  : TunableTFModel[Pattern, In, Out, ArchOut, Loss, IT, ID, IS, TT, TD, TS, ITT, IDD, ISS] = {

    val modelFunc = ModelFunction.from_arch_generator(
      arch_gen, loss, input, target,
      inMemory, existingGraph, data_handles
    )

    new TunableTFModel[Pattern, In, Out, ArchOut, Loss, IT, ID, IS, TT, TD, TS, ITT, IDD, ISS](
      modelFunc, get_training_config,
      hyp, training_data,
      convert_to_tensor,
      fitness_function,
      validation_data,
      data_split_func
    )

  }



}