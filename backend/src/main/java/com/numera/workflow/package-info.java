@org.springframework.modulith.ApplicationModule(
        displayName = "workflow",
        allowedDependencies = {
                "shared",
                "shared::security",
                "shared::events",
                "shared::notification",
                "shared::config",
                "shared::domain",
                "shared::exception",
                "shared::infrastructure",
                "auth"
        }
)
package com.numera.workflow;
