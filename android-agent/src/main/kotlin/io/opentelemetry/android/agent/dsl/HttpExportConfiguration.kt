/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.agent.dsl

import io.opentelemetry.android.Incubating
import io.opentelemetry.android.agent.connectivity.ClientTlsConnectivity
import io.opentelemetry.android.agent.connectivity.Compression
import io.opentelemetry.android.agent.connectivity.HeadersConfig
import io.opentelemetry.android.agent.connectivity.HttpEndpointConnectivity
import io.opentelemetry.android.agent.connectivity.SSLContextConnectivity

/**
 * Type-safe config DSL that controls how HTTP export of telemetry should behave.
 */
@OpenTelemetryDslMarker
class HttpExportConfiguration internal constructor() {
    /**
     * Global URL for HTTP export requests.
     */
    var baseUrl: String = ""

    private var globalHeaders: HeadersConfig = HeadersConfig.Static(emptyMap())

    /**
     * Global headers that should be attached to any HTTP export requests.
     * Assigning this property replaces any previously configured [baseHeaders] supplier.
     * Returns an empty map when a supplier is configured, without invoking it.
     */
    var baseHeaders: Map<String, String>
        get() = (globalHeaders as? HeadersConfig.Static)?.headers ?: emptyMap()
        set(value) {
            globalHeaders = HeadersConfig.Static(value)
        }

    /**
     * Supplies global headers for each HTTP export request across all signals, replacing the
     * static [baseHeaders] map. Signal-specific headers are retained, with global headers taking
     * precedence on matching keys. The supplier runs on exporter threads and must be thread-safe
     * and non-blocking. Use it to read a cached authentication token rather than refreshing the
     * token here.
     */
    fun baseHeaders(supplier: () -> Map<String, String>) {
        globalHeaders = HeadersConfig.Dynamic(supplier)
    }

    /**
     * Default compression algorithm for all export requests.
     */
    var compression: Compression = Compression.GZIP

    /**
     * Default SSL context for all export requests.
     */
    var sslContext: SSLContextConnectivity? = null

    /**
     * Sets ths client key and the certificate chain to use for verifying client
     * for all requests when TLS is enabled.
     */
    @Incubating
    var clientTls: ClientTlsConnectivity? = null

    private val spansConfig: EndpointConfiguration = EndpointConfiguration("")
    private val logsConfig: EndpointConfiguration = EndpointConfiguration("")
    private val metricsConfig: EndpointConfiguration = EndpointConfiguration("")

    internal fun spansEndpoint(): HttpEndpointConnectivity =
        @OptIn(Incubating::class)
        HttpEndpointConnectivity.forTraces(
            chooseUrlSource(spansConfig),
            isFullUrl(spansConfig),
            resolveEndpointHeaders(spansConfig),
            chooseCompression(spansConfig.compression),
            sslContext,
            clientTls,
        )

    internal fun logsEndpoint(): HttpEndpointConnectivity =
        @OptIn(Incubating::class)
        HttpEndpointConnectivity.forLogs(
            chooseUrlSource(logsConfig),
            isFullUrl(logsConfig),
            resolveEndpointHeaders(logsConfig),
            chooseCompression(logsConfig.compression),
            sslContext,
            clientTls,
        )

    internal fun metricsEndpoint(): HttpEndpointConnectivity =
        @OptIn(Incubating::class)
        HttpEndpointConnectivity.forMetrics(
            chooseUrlSource(metricsConfig),
            isFullUrl(metricsConfig),
            resolveEndpointHeaders(metricsConfig),
            chooseCompression(metricsConfig.compression),
            sslContext,
            clientTls,
        )

    private fun resolveEndpointHeaders(cfg: EndpointConfiguration): HeadersConfig {
        val signalHeaders = cfg.headers.toMap()
        return when (val headers = globalHeaders) {
            is HeadersConfig.Static -> HeadersConfig.Static(signalHeaders + headers.headers)
            is HeadersConfig.Dynamic -> HeadersConfig.Dynamic { signalHeaders + headers.supplier() }
        }
    }

    private fun chooseUrlSource(cfg: EndpointConfiguration): String =
        cfg.fullUrl?.takeUnless { it.isBlank() } ?: cfg.url.ifBlank { baseUrl }

    private fun isFullUrl(cfg: EndpointConfiguration): Boolean = !cfg.fullUrl.isNullOrBlank()

    private fun chooseCompression(signalConfigCompression: Compression?): Compression = signalConfigCompression ?: this.compression

    /**
     * Override the default configuration for the v1/traces endpoint only.
     */
    fun spans(action: EndpointConfiguration.() -> Unit) {
        spansConfig.action()
    }

    /**
     * Override the default configuration for the v1/logs endpoint only.
     */
    fun logs(action: EndpointConfiguration.() -> Unit) {
        logsConfig.action()
    }

    /**
     * Override the default configuration for the v1/metrics endpoint only.
     */
    fun metrics(action: EndpointConfiguration.() -> Unit) {
        metricsConfig.action()
    }
}
