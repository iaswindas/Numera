@org.springframework.modulith.ApplicationModule(
        displayName = "integration",
        allowedDependencies = {
                "shared",
                "shared::security",
                "shared::domain",
                "shared::exception",
                "shared::config",
                "shared::infrastructure",
                "document",
                "document::domain",
                "spreading",
                "spreading::domain",
                "spreading::infrastructure",
                "customer",
                "customer::domain",
                "model",
                "model::domain"
        }
)
package com.numera.integration;
