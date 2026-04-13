@org.springframework.modulith.ApplicationModule(
        displayName = "portfolio",
        allowedDependencies = {
                "spreading",
                "spreading::domain",
                "spreading::infrastructure",
                "customer",
                "customer::domain",
                "model",
                "model::domain",
                "shared",
                "shared::audit",
                "shared::config",
                "shared::domain",
                "shared::events",
                "shared::exception",
                "shared::security"
        }
)
package com.numera.portfolio;
