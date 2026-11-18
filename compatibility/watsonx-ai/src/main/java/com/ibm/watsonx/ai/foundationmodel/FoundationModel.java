/*
 * Copyright 2025 IBM Corporation
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ibm.watsonx.ai.foundationmodel;

import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a foundation model with its metadata and capabilities.
 *
 * @param modelId the unique model identifier
 * @param label the display label
 * @param provider the model provider
 * @param source the model source
 * @param functions the list of supported functions
 * @param shortDescription brief description
 * @param longDescription detailed description
 * @param termsUrl URL to terms and conditions
 * @param inputTier the input pricing tier
 * @param outputTier the output pricing tier
 * @param numberParams the number of parameters
 * @param minShotSize minimum shot size for few-shot learning
 * @param taskIds the list of supported task identifiers
 * @param tasks the list of supported tasks
 * @param modelLimits the model limits
 * @param limits the limits map
 * @param lifecycle the lifecycle information
 * @param trainingParameters the training parameters
 * @param versions the list of model versions
 * @param supportedLanguages the list of supported languages
 * @param loraFineTuningParameters the LoRA fine-tuning parameters
 * @param dataType the data type
 * @param architectureType the architecture type
 * @param curatedModelInfo curated model information
 * @param deploymentParameters the deployment parameters
 */
public record FoundationModel(
    @JsonProperty("model_id") String modelId,
    String label,
    String provider,
    String source,
    List<Function> functions,
    @JsonProperty("short_description") String shortDescription,
    @JsonProperty("long_description") String longDescription,
    @JsonProperty("terms_url") String termsUrl,
    @JsonProperty("input_tier") String inputTier,
    @JsonProperty("output_tier") String outputTier,
    @JsonProperty("number_params") String numberParams,
    @JsonProperty("min_shot_size") Integer minShotSize,
    @JsonProperty("task_ids") List<String> taskIds,
    List<Task> tasks,
    @JsonProperty("model_limits") ModelLimits modelLimits,
    Map<String, Limit> limits,
    List<Lifecycle> lifecycle,
    @JsonProperty("training_parameters") TrainingParameters trainingParameters,
    List<Version> versions,
    @JsonProperty("supported_languages") List<String> supportedLanguages,
    @JsonProperty("lora_fine_tuning_parameters") LoRaFineTuningParameters loraFineTuningParameters,
    @JsonProperty("data_type") String dataType,
    @JsonProperty("architecture_type") String architectureType,
    @JsonProperty("curated_model_info") CuratedModelInfo curatedModelInfo,
    @JsonProperty("deployment_parameters") List<DeploymentParameter> deploymentParameters) {

    public FoundationModel {
        functions = isNull(functions) ? null : List.copyOf(functions);
        taskIds = isNull(taskIds) ? null : List.copyOf(taskIds);
        tasks = isNull(tasks) ? null : List.copyOf(tasks);
        limits = isNull(limits) ? null : Collections.unmodifiableMap(new LinkedHashMap<>(limits));
        lifecycle = isNull(lifecycle) ? null : List.copyOf(lifecycle);
        versions = isNull(versions) ? null : List.copyOf(versions);
        supportedLanguages = isNull(supportedLanguages) ? null : List.copyOf(supportedLanguages);
        deploymentParameters = isNull(deploymentParameters) ? null : List.copyOf(deploymentParameters);
    }

    /**
     * Returns the maximum sequence length supported by the model.
     *
     * @return the maximum sequence length, or {@code null} if not available
     */
    public Integer maxSequenceLength() {
        return ofNullable(modelLimits).map(m -> m.maxSequenceLength()).orElse(null);
    }

    /**
     * Returns the maximum number of output tokens supported by the model.
     *
     * @return the maximum output tokens, or {@code null} if not available
     */
    public Integer maxOutputTokens() {
        return ofNullable(modelLimits).map(m -> m.maxOutputTokens()).orElse(null);
    }

    /**
     * Represents a function supported by the model.
     *
     * @param id the function identifier
     */
    public record Function(String id) {}

    /**
     * Represents quality ratings.
     *
     * @param quality the quality rating
     */
    public record Ratings(Integer quality) {}

    /**
     * Represents a default value.
     *
     * @param defaultValue the default value
     */
    public record DefaultValue(@JsonProperty("default") String defaultValue) {}

    /**
     * Represents target modules configuration.
     *
     * @param defaultValue the list of default target modules
     */
    public record TargetModules(@JsonProperty("default") List<String> defaultValue) {
        public TargetModules {
            defaultValue = isNull(defaultValue) ? null : List.copyOf(defaultValue);
        }
    }

    /**
     * Represents gradient checkpointing configuration.
     *
     * @param defaultValue whether gradient checkpointing is enabled by default
     */
    public record GradientCheckpointing(@JsonProperty("default") Boolean defaultValue) {}

    /**
     * Represents GPU configuration.
     *
     * @param defaultValue the default number of GPUs
     */
    public record NumGpus(@JsonProperty("default") Integer defaultValue) {}

    /**
     * Represents a task supported by the model.
     *
     * @param id the task identifier
     * @param ratings the task ratings
     * @param tags the task tags
     * @param trainingParameters the training parameters for this task
     */
    public record Task(
        String id,
        Ratings ratings,
        List<String> tags,
        @JsonProperty("training_parameters") TrainingParameters trainingParameters) {
        public Task {
            tags = isNull(tags) ? null : List.copyOf(tags);
        }
    }

    /**
     * Represents model limits and constraints.
     *
     * @param maxSequenceLength the maximum sequence length
     * @param maxOutputTokens the maximum output tokens
     * @param trainingDataMaxRecords the maximum training data records
     * @param embeddingDimension the embedding dimension
     */
    public record ModelLimits(
        @JsonProperty("max_sequence_length") Integer maxSequenceLength,
        @JsonProperty("max_output_tokens") Integer maxOutputTokens,
        @JsonProperty("training_data_max_records") Integer trainingDataMaxRecords,
        @JsonProperty("embedding_dimension") Integer embeddingDimension) {}

    /**
     * Represents usage limits.
     *
     * @param callTime the call time limit
     * @param maxOutputTokens the maximum output tokens
     */
    public record Limit(
        @JsonProperty("call_time") String callTime,
        @JsonProperty("max_output_tokens") Integer maxOutputTokens) {}

    /**
     * Represents model lifecycle information.
     *
     * @param id the lifecycle identifier
     * @param startDate the start date
     * @param alternativeModelIds the list of alternative model identifiers
     */
    public record Lifecycle(
        String id,
        @JsonProperty("start_date") String startDate,
        @JsonProperty("alternative_model_ids") List<String> alternativeModelIds) {
        public Lifecycle {
            alternativeModelIds = isNull(alternativeModelIds) ? null : List.copyOf(alternativeModelIds);
        }
    }

    /**
     * Represents training parameters for the model.
     *
     * @param initMethod the initialization method
     * @param initText the initialization text
     * @param numVirtualTokens the number of virtual tokens
     * @param numEpochs the number of epochs range
     * @param verbalizer the verbalizer configuration
     * @param batchSize the batch size range
     * @param maxInputTokens the maximum input tokens range
     * @param maxOutputTokens the maximum output tokens range
     * @param torchDtype the torch data type
     * @param accumulateSteps the accumulate steps range
     * @param learningRate the learning rate range
     */
    public record TrainingParameters(
        @JsonProperty("init_method") InitMethod initMethod,
        @JsonProperty("init_text") DefaultValue initText,
        @JsonProperty("num_virtual_tokens") NumVirtualTokens numVirtualTokens,
        @JsonProperty("num_epochs") IntRange numEpochs,
        DefaultValue verbalizer,
        @JsonProperty("batch_size") IntRange batchSize,
        @JsonProperty("max_input_tokens") IntRange maxInputTokens,
        @JsonProperty("max_output_tokens") IntRange maxOutputTokens,
        @JsonProperty("torch_dtype") DefaultValue torchDtype,
        @JsonProperty("accumulate_steps") IntRange accumulateSteps,
        @JsonProperty("learning_rate") DoubleRange learningRate) {}

    /**
     * Represents initialization method configuration.
     *
     * @param supported the list of supported methods
     * @param defaultValue the default method
     */
    public record InitMethod(
        List<String> supported,
        @JsonProperty("default") String defaultValue) {
        public InitMethod {
            supported = isNull(supported) ? null : List.copyOf(supported);
        }
    }

    /**
     * Represents virtual tokens configuration.
     *
     * @param supported the list of supported values
     * @param defaultValue the default value
     */
    public record NumVirtualTokens(
        List<Integer> supported,
        @JsonProperty("default") Integer defaultValue) {
        public NumVirtualTokens {
            supported = isNull(supported) ? null : List.copyOf(supported);
        }
    }

    /**
     * Represents an integer range with default, min, and max values.
     *
     * @param defaultValue the default value
     * @param min the minimum value
     * @param max the maximum value
     */
    public record IntRange(
        @JsonProperty("default") Integer defaultValue,
        Integer min,
        Integer max) {}

    /**
     * Represents a double range with default, min, and max values.
     *
     * @param defaultValue the default value
     * @param min the minimum value
     * @param max the maximum value
     */
    public record DoubleRange(
        @JsonProperty("default") Double defaultValue,
        Double min,
        Double max) {}

    /**
     * Represents a model version.
     *
     * @param version the version identifier
     * @param availableDate the availability date
     */
    public record Version(
        String version,
        @JsonProperty("available_date") String availableDate) {}

    /**
     * Represents LoRA fine-tuning parameters.
     *
     * @param numEpochs the number of epochs range
     * @param verbalizer the verbalizer configuration
     * @param batchSize the batch size range
     * @param accumulateSteps the accumulate steps range
     * @param learningRate the learning rate range
     * @param maxSeqLength the maximum sequence length range
     * @param tokenizer the tokenizer configuration
     * @param responseTemplate the response template configuration
     * @param numGpus the number of GPUs configuration
     * @param peftParameters the PEFT parameters
     * @param gradientCheckpointing the gradient checkpointing configuration
     */
    public record LoRaFineTuningParameters(
        @JsonProperty("num_epochs") IntRange numEpochs,
        DefaultValue verbalizer,
        @JsonProperty("batch_size") IntRange batchSize,
        @JsonProperty("accumulate_steps") IntRange accumulateSteps,
        @JsonProperty("learning_rate") DoubleRange learningRate,
        @JsonProperty("max_seq_length") IntRange maxSeqLength,
        DefaultValue tokenizer,
        @JsonProperty("response_template") DefaultValue responseTemplate,
        @JsonProperty("num_gpus") NumGpus numGpus,
        @JsonProperty("peft_parameters") PeftParameters peftParameters,
        @JsonProperty("gradient_checkpointing") GradientCheckpointing gradientCheckpointing) {}

    /**
     * Represents PEFT (Parameter-Efficient Fine-Tuning) parameters.
     *
     * @param type the PEFT type configuration
     * @param rank the rank configuration
     * @param targetModules the target modules configuration
     * @param loraAlpha the LoRA alpha range
     * @param loraDropout the LoRA dropout range
     */
    public record PeftParameters(
        Type type,
        Rank rank,
        @JsonProperty("target_modules") TargetModules targetModules,
        @JsonProperty("lora_alpha") IntRange loraAlpha,
        @JsonProperty("lora_dropout") DoubleRange loraDropout) {}

    /**
     * Represents type configuration.
     *
     * @param supported the list of supported types
     * @param defaultValue the default type
     */
    public record Type(
        List<String> supported,
        @JsonProperty("default") String defaultValue) {
        public Type {
            supported = isNull(supported) ? null : List.copyOf(supported);
        }
    }

    /**
     * Represents rank configuration.
     *
     * @param supported the list of supported ranks
     * @param defaultValue the default rank
     */
    public record Rank(
        List<Integer> supported,
        @JsonProperty("default") Integer defaultValue) {
        public Rank {
            supported = isNull(supported) ? null : List.copyOf(supported);
        }
    }

    /**
     * Represents curated model information.
     *
     * @param baseModelId the base model identifier
     * @param hardwareSpec the hardware specification
     * @param loraHardwareSpec the LoRA hardware specification
     * @param configuredMaxSequenceLength the configured maximum sequence length
     * @param configuredMaxOutputTokens the configured maximum output tokens
     * @param category the model category
     * @param maxGpuLoras the maximum GPU LoRAs
     * @param maxCpuLoras the maximum CPU LoRAs
     * @param maxLoraRank the maximum LoRA rank
     */
    public record CuratedModelInfo(
        @JsonProperty("base_model_id") String baseModelId,
        @JsonProperty("hardware_spec") String hardwareSpec,
        @JsonProperty("lora_hardware_spec") String loraHardwareSpec,
        @JsonProperty("configured_max_sequence_length") Integer configuredMaxSequenceLength,
        @JsonProperty("configured_max_output_tokens") Integer configuredMaxOutputTokens,
        String category,
        @JsonProperty("max_gpu_loras") Integer maxGpuLoras,
        @JsonProperty("max_cpu_loras") Integer maxCpuLoras,
        @JsonProperty("max_lora_rank") Integer maxLoraRank) {}

    /**
     * Represents a deployment parameter.
     *
     * @param name the parameter name
     * @param displayName the display name
     * @param defaultValue the default value
     * @param type the parameter type
     */
    public record DeploymentParameter(
        String name,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("default") Object defaultValue,
        String type) {}
}
