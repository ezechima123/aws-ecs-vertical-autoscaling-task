
# Problem Statements/Requirements
AWS ECS deployment does not support vertical autoscaling.  Vertical autoscaling automatically adds more resource capacity, CPU or Memory, on the same instance.  Your development teams want to add this capability to their arsenal.  Your task is to provide a simplified solution architectural diagram and implementation in a language of your choice, such as python, javascript, java, Go, etc.  The code should be expected to run as a docker container in ECS or a lambda function.

## Solution

This solution enables vertical (cpu and memory) autoscaling on an ECS Cluster  by using a Lambda function written in Java and connected to the SNS Topic as a Subscriber.
Furthermore,I created  an alarm to monitor CPU and Memory Utilization metrics based on 80% threashold and which action is also connected to the same SNS Topic as a Producer.
<img src="resources/solution.jpg"
     alt="Solution Architecture"
     style="float: left; margin-right: 10px;" />




## Build and Deployment

## Simulation

## Enhancements


