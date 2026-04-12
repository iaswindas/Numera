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
                "spreading",
                "customer"
        }
)
package com.numera.integration;
