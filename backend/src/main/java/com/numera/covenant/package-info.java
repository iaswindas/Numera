@org.springframework.modulith.ApplicationModule(
        displayName = "covenant",
        allowedDependencies = {
                "document",
                "model::application",
                "customer::domain",
                "customer::infrastructure",
                "document::infrastructure",
                "shared::audit",
                "shared::config",
                "shared::domain",
                "shared::events",
                "shared::exception",
                "shared::infrastructure",
                "shared::security"
        }
)
package com.numera.covenant;
