package com.causa.validation.shared;

/**
 * Supported validation evidence source types.
 *
 * @since 0.0.1
 */
public enum ValidationDataSourceType {
    POD_LOG,
    KUBERNETES_EVENT,
    RESOURCE_USAGE,
    KRUIZE_RECOMMENDATION
}
