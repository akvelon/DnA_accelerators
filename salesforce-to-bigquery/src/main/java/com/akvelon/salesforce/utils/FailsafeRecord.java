/*
 * Copyright 2023 Akvelon Inc.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.akvelon.salesforce.utils;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.apache.avro.reflect.Nullable;
import org.apache.beam.sdk.coders.DefaultCoder;

/**
 * The {@link FailsafeRecord} class holds the current and original values of a record within a
 * pipeline and error message with stacktrace. This class allows to output the original record information
 * and error information to a dead-letter table if there was failure during one of the pipelines transforms.
 */
@DefaultCoder(FailsafeRecordCoder.class)
public class FailsafeRecord<OriginalT, CurrentT> {

    private final OriginalT originalPayload;
    private final CurrentT currentPayload;
    @Nullable private String errorMessage;
    @Nullable private String stacktrace;

    private FailsafeRecord(OriginalT originalPayload, CurrentT currentPayload) {
        this.originalPayload = originalPayload;
        this.currentPayload = currentPayload;
    }

    public static <OriginalT, CurrentT> FailsafeRecord<OriginalT, CurrentT> of(
            OriginalT originalPayload, CurrentT currentPayload) {
        return new FailsafeRecord<>(originalPayload, currentPayload);
    }

    public static <OriginalT, CurrentT> FailsafeRecord<OriginalT, CurrentT> of(
            FailsafeRecord<OriginalT, CurrentT> other) {
        return new FailsafeRecord<>(other.originalPayload, other.currentPayload)
                .setErrorMessage(other.getErrorMessage())
                .setStacktrace(other.getStacktrace());
    }

    public OriginalT getOriginalPayload() {
        return originalPayload;
    }

    public CurrentT getCurrentPayload() {
        return currentPayload;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public FailsafeRecord<OriginalT, CurrentT> setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    public String getStacktrace() {
        return stacktrace;
    }

    public FailsafeRecord<OriginalT, CurrentT> setStacktrace(String stacktrace) {
        this.stacktrace = stacktrace;
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        final FailsafeRecord other = (FailsafeRecord) obj;
        return Objects.deepEquals(this.originalPayload, other.getOriginalPayload())
                && Objects.deepEquals(this.currentPayload, other.getCurrentPayload())
                && Objects.deepEquals(this.errorMessage, other.getErrorMessage())
                && Objects.deepEquals(this.stacktrace, other.getStacktrace());
    }

    @Override
    public int hashCode() {
        return Objects.hash(originalPayload, currentPayload, errorMessage, stacktrace);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("originalPayload", originalPayload)
                .add("payload", currentPayload)
                .add("errorMessage", errorMessage)
                .add("stacktrace", stacktrace)
                .toString();
    }
}
