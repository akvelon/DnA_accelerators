package com.akvelon.salesforce.options;

import org.apache.beam.sdk.options.Description;

/**
 * The {@link SalesforceBaseSourceOptions} interface provides the custom execution options passed by the
 * executor at the command-line for example with Cdap Salesforce batch source plugins.
 */
public interface SalesforceBatchSourceOptions extends SalesforceBaseSourceOptions {

    //Optional

    @Description("Salesforce SObject query offset.")
    String getOffset();

    void setOffset(String offset);

    @Description("Salesforce SObject query duration.")
    String getDuration();

    void setDuration(String duration);

    @Description("Salesforce SObject query datetime filter. Example: 2019-03-12T11:29:52Z")
    String getDatetimeBefore();

    void setDatetimeBefore(String datetimeBefore);

    @Description("Salesforce SObject query datetime filter. Example: 2019-03-12T11:29:52Z")
    String getDatetimeAfter();

    void setDatetimeAfter(String datetimeAfter);

    @Description("The SOQL query to retrieve results from. Example: select Id, Name from Opportunity")
    String getQuery();

    void setQuery(String query);
}
