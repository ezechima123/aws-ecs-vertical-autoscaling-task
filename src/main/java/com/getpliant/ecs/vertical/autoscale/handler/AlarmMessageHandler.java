package com.getpliant.ecs.vertical.autoscale.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.DescribeTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.DescribeTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.ListClustersResponse;
import software.amazon.awssdk.services.ecs.model.ListServicesRequest;
import software.amazon.awssdk.services.ecs.model.ListServicesResponse;
import software.amazon.awssdk.services.ecs.model.ListTaskDefinitionsRequest;
import software.amazon.awssdk.services.ecs.model.ListTaskDefinitionsResponse;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.TaskDefinition;
import software.amazon.awssdk.services.ecs.model.UpdateServiceRequest;
import software.amazon.awssdk.services.ecs.model.UpdateServiceResponse;

/**
 *
 * @author Chima Ezeamama
 * @version 1.0
 * @since 19.02.2023
 */
public class AlarmMessageHandler implements RequestHandler<SNSEvent, String> {

    private EcsClient ecsClient;
    private String clusterArn;
    private String serviceName;
    private String taskDefintionArn;
    private String taskDefintionFamily;
    // List of available CPU and memory values in ECS Fargate
    private static final List<String> CPU_VALUES = Arrays.asList("256", "512", "1024", "2048");
    private static final List<String> MEMORY_VALUES = Arrays.asList("512", "1024", "2048", "3072", "4096", "5120", "6144", "7168", "8192");

    @Override
    public String handleRequest(SNSEvent event, Context context) {

        LambdaLogger logger = context.getLogger();
        try {
            String eventMessage = event.getRecords().get(0).getSNS().getMessage();
            ecsClient = EcsClient.builder().region(Region.AWS_GLOBAL).build();

            ListClustersResponse listClustersResponse = ecsClient.listClusters();
            clusterArn = listClustersResponse.clusterArns().get(0);
            logger.log("The ClusterArn is " + clusterArn + "\n");

            ListServicesResponse listServicesRespone = ecsClient.listServices(ListServicesRequest.builder().cluster(clusterArn).build());
            String serviceArn = listServicesRespone.serviceArns().get(0);
            logger.log("The ServiceArn is " + serviceArn + "\n");

            serviceName = serviceArn.substring(serviceArn.indexOf("/") + 1);
            logger.log("The Service Name " + serviceName + "\n");

            // List the task definitions for the task definition family and the cluster
            ListTaskDefinitionsRequest listTaskDefinitionsRequest = ListTaskDefinitionsRequest.builder()
                    .status("ACTIVE")
                    .maxResults(1)
                    .build();
            ListTaskDefinitionsResponse listTaskDefinitionsResponse = ecsClient.listTaskDefinitions(listTaskDefinitionsRequest);

            // Get the task definition ARN
            taskDefintionArn = listTaskDefinitionsResponse.taskDefinitionArns().get(0);
            logger.log("The Task ARN is " + taskDefintionArn + "\n");

            logger.log("About to get Taskdefinition: " + "\n");
            // Describe the task definition
            DescribeTaskDefinitionResponse describeTaskDefinitionResponse = ecsClient.describeTaskDefinition(
                    DescribeTaskDefinitionRequest.builder()
                            .taskDefinition(taskDefintionArn)
                            .build());

            logger.log("About to get taskDefintionFamily,Current Cpu and Memory capacities: " + "\n");
            // Get the current CPU and memory values
            String currentCpuValue = describeTaskDefinitionResponse.taskDefinition().cpu();
            String currentMemoryValue = describeTaskDefinitionResponse.taskDefinition().memory();
            taskDefintionFamily = describeTaskDefinitionResponse.taskDefinition().family();

            logger.log("Current CPU value: " + currentCpuValue + "\n");
            logger.log("Current memory value: " + currentMemoryValue + "\n");
            logger.log("Task Definition Family: " + taskDefintionFamily + "\n");

            // Check if the current CPU and memory values are in the list of available values
            if (!CPU_VALUES.contains(currentCpuValue) || !MEMORY_VALUES.contains(currentMemoryValue)) {
                logger.log("Current CPU or memory value is not in the list of available values." + "\n");
                return "INVALID_CPU_MEMORY_VALUES";
            }

            // Get the index of the current CPU and memory values in the list of available values
            int currentCpuIndex = CPU_VALUES.indexOf(currentCpuValue);
            int currentMemoryIndex = MEMORY_VALUES.indexOf(currentMemoryValue);

            // Get the next CPU and memory values from the list of available values
            String nextCpuValue = (currentCpuIndex < CPU_VALUES.size() - 1) ? CPU_VALUES.get(currentCpuIndex + 1) : CPU_VALUES.get(0);
            String nextMemoryValue = (currentMemoryIndex < MEMORY_VALUES.size() - 1) ? MEMORY_VALUES.get(currentMemoryIndex + 1) : MEMORY_VALUES.get(0);

            logger.log("Next CPU value: " + nextCpuValue + "\n");
            logger.log("Next memory value: " + nextMemoryValue + "\n");

            // Get the task definition from the response
            TaskDefinition taskDefinition = describeTaskDefinitionResponse.taskDefinition();
            logger.log("Creating another Taskdefinition verion : " + "\n");
            // Modify the CPU and memory values
            TaskDefinition updatedTaskDefinition = taskDefinition.toBuilder()
                    .cpu(nextCpuValue)
                    .memory(nextMemoryValue)
                    .build();

            logger.log("About to register new Task version : " + "\n");
            // Register the updated task definition
            RegisterTaskDefinitionResponse registerTaskDefinitionResponse = ecsClient.registerTaskDefinition(
                    RegisterTaskDefinitionRequest.builder()
                            .family(taskDefintionFamily)
                            .containerDefinitions(updatedTaskDefinition.containerDefinitions())
                            .taskRoleArn(updatedTaskDefinition.taskRoleArn())
                            .executionRoleArn(updatedTaskDefinition.executionRoleArn())
                            .networkMode(updatedTaskDefinition.networkMode())
                            .volumes(updatedTaskDefinition.volumes())
                            .placementConstraints(updatedTaskDefinition.placementConstraints())
                            .requiresCompatibilities(updatedTaskDefinition.requiresCompatibilities())
                            .cpu(updatedTaskDefinition.cpu())
                            .memory(updatedTaskDefinition.memory())
                            .build());

            logger.log("Task definition updated: " + registerTaskDefinitionResponse.taskDefinition().taskDefinitionArn() + "\n");

            // Update the service with the new task definition to get the Next Revision
            UpdateServiceResponse updateServiceResponse = ecsClient.updateService(
                    UpdateServiceRequest.builder()
                            .cluster(clusterArn)
                            .service(serviceName)
                            .taskDefinition(registerTaskDefinitionResponse.taskDefinition().taskDefinitionArn())
                            .build());
            logger.log("Service updated: " + updateServiceResponse.service().serviceName() + "\n");

        } catch (AwsServiceException | SdkClientException ex) {
            logger.log("Error => : " + ex.getMessage());
            return "Invalid Response3";
        } finally {
            if (ecsClient != null) {
                ecsClient.close();
            }
        }
        return "SUCCESS";
    }

}
