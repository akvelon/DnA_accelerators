package com.akvelon.hubspot.options;

import io.cdap.plugin.common.Constants;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.Validation;

/**
 * The {@link BaseCdapOptions} interface provides the custom execution options passed by the
 * executor at the command-line for examples with Cdap plugins.
 */
public interface BaseCdapOptions extends PipelineOptions {

    @Validation.Required
    @Description(Constants.Reference.REFERENCE_NAME_DESCRIPTION)
    String getReferenceName();

    void setReferenceName(String referenceName);

    @Description(
            "The Cloud Pub/Sub topic to publish to. "
                    + "The name should be in the format of "
                    + "projects/<project-id>/topics/<topic-name>.")
    @Validation.Required
    String getOutputTopic();

    void setOutputTopic(String outputTopic);
}
