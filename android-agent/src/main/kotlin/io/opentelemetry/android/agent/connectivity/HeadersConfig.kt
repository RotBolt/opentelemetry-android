/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.agent.connectivity

internal sealed interface HeadersConfig {
    class Static(
        val headers: Map<String, String>,
    ) : HeadersConfig

    class Dynamic(
        val supplier: () -> Map<String, String>,
    ) : HeadersConfig
}
