# ScalaMultiAgentDPOP
Implementation of a multi agent constraint problem using DPOP, written in Scala

## Original paper
This work is based on the original paper http://ijcai.org/papers/0445.pdf. Please refer to it for better understanding.

## The basic idea
The basic idea of this project is to use the agents in Scala and Akka framework to compute colors in a map, where the states are connected through a tree with pseudo-parents. The constraint is that no side by side map should have the same color.

The paper presents a solution where the child calculates their possible values and transmit this information to its parents. The parent will be responsible for checking what would be the best combination of his color and its children, passing along possible best values for its own parent. 
The root of the tree is going to find the best solution for the system and broadcast its decision to its children. They will pick the best value according to its parent and its children.

The algorithm finishes when the final leaves of the tree choose their colors.

## Advantages
The algorithm advantages are in the paper, but basically it takes less messages, processing and time to come to the final solution than other constrain optimization algorithms.

The programming in Scala/Akka makes it easy to program the actors into small, functional and simple components that will communicate and solve the problem by themselves (the base of multiagent systems).

## Using the code
The code is in MapColorCalculator.scala.

I have used IntelliJ as the tool for programming, so importing it using this tool would be easier to run the program.
