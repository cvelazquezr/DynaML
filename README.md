
<img src="docs/images/dynaml_logo_small.png" alt="DynaML Logo" style="width: 128px;"/>


[![Join the chat at https://gitter.im/Transcendent-AI/DynaML](https://badges.gitter.im/mandar2812/DynaML.svg)](https://gitter.im/Transcendent-AI/DynaML?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) [![Build Status](https://travis-ci.org/transcendent-ai-labs/DynaML.svg?branch=master)](https://travis-ci.org/transcendent-ai-labs/DynaML) [![](https://jitpack.io/v/transcendent-ai-labs/DynaML.svg)](https://jitpack.io/#transcendent-ai-labs/DynaML)

Aim
============

DynaML is a scala library/repl for implementing and working with general Machine Learning models. Machine Learning/AI applications make heavy use of various entities such as graphs, vectors, matrices etc as well as classes of mathematical models which deal with broadly three kinds of tasks, prediction, classification and clustering.

The aim is to build a robust set of abstract classes and interfaces, which can be extended easily to implement advanced models for small and large scale applications.

But the library can also be used as an educational/research tool for data analysis.

Currently DynaML supports.
* Regularized Ordinary Least Squares
* Logistic and Probit Models for binary classification
* Regression and Classification with kernel based Dual LS-SVM
* Regression and Classification with Gaussian Processes
* Feed forward Neural Networks
* Committee Models
  - Neural Committee Models
  - Gaussian Process Committee Models
* Model Learning and Optimization
  - Gradient Descent
  - Conjugate Gradient
  - Committee Model Solver
  - Back propogation with momentum
  - LSSVM linear solver
* Model tuning
  * Grid Search
  * Maximum Likelihood (ML-II)
  * Coupled Simulated Annealing
* Model validation metrics (RMSE, Area under ROC)
* Entropy based data subset selection
* Data Pipes for configurable workflows

Include
--------

To include DynaML in your maven JVM project edit your ```pom.xml``` file as follows

```xml
<repositories>
		<repository>
		    <id>jitpack.io</id>
		    <url>https://jitpack.io</url>
		</repository>
</repositories>
```

```xml
<dependency>
    <groupId>com.github.transcendent-ai-labs</groupId>
    <artifactId>DynaML</artifactId>
    <version>v1.4</version>
</dependency>
```

for sbt projects edit your `build.sbt` (see [JitPack](https://jitpack.io/#transcendent-ai-labs/DynaML) for more details)

```scala
    resolvers += "jitpack" at "https://jitpack.io"
    libraryDependencies += "com.github.transcendent-ai-labs" % "DynaML" % version
```



Installation
============

Platform Compatibility
----------------------
Currently DynaML installs and runs on *nix platforms, though it is possible to build the project on windows, running the generated .bat file might not work and one would need to resort to using the `java -jar` command.

Pre-requisites
-------------
* sbt
* A modern HTML5 enabled browser (to view plots generated by Wisp)
* BLAS, LAPACK and ARPACK binaries for your platform. In case they are not installed, it is possible to disable this feature by commenting out (`//`) the section of the build.sbt file given below.

  ```scala
    "org.scalanlp" % "breeze-natives_2.11" % "0.11.2" % "compile",
  ```

Steps
-------

* Clone this repository
* Run the following.
```shell
  sbt
```

The sbt shell will open

```shell
 [info] Loading project definition from ~/DynaML/project
 [info] Set current project to DynaML (in build file:~/Development/DynaML/)
 >
```

Now enter the following commands

```shell
>stage
>console
```

After the project builds, you should get the following prompt.

```
       _        _        _          _             _                  _   _         _
      /\ \     /\ \     /\_\       /\ \     _    / /\               /\_\/\_\ _    _\ \
     /  \ \____\ \ \   / / /      /  \ \   /\_\ / /  \             / / / / //\_\ /\__ \
    / /\ \_____\\ \ \_/ / /      / /\ \ \_/ / // / /\ \           /\ \/ \ \/ / // /_ \_\
   / / /\/___  / \ \___/ /      / / /\ \___/ // / /\ \ \         /  \____\__/ // / /\/_/
  / / /   / / /   \ \ \_/      / / /  \/____// / /  \ \ \       / /\/________// / /
 / / /   / / /     \ \ \      / / /    / / // / /___/ /\ \     / / /\/_// / // / /
/ / /   / / /       \ \ \    / / /    / / // / /_____/ /\ \   / / /    / / // / / ____
\ \ \__/ / /         \ \ \  / / /    / / // /_________/\ \ \ / / /    / / // /_/_/ ___/\
 \ \___\/ /           \ \_\/ / /    / / // / /_       __\ \_\\/_/    / / //_______/\__\/
  \/_____/             \/_/\/_/     \/_/ \_\___\     /____/_/        \/_/ \_______\/

Welcome to DynaML v1.4.1-beta.11
Interactive Scala shell for Machine Learning Research
(Scala 2.11.8 Java 1.8.0_101)
DynaML>

```

Getting Started
===============
Refer to the [user guide](https://transcendent-ai-labs.github.io/DynaML/mydoc_introduction.html) for a more detailed introduction, for contributing; refer to the [wiki](https://github.com/transcendent-ai-labs.github.io/DynaML/wiki).
