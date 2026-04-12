@org.springframework.modulith.ApplicationModule(
        displayName = "reporting",
        allowedDependencies = {
                "spreading",
                "spreading::domain",
                "spreading::infrastructure",
                "covenant",
                "covenant::domain",
                "covenant::infrastructure",
                "customer",
                "customer::domain",
                "shared",
                "shared::audit",
                "shared::config",
                "shared::domain",
                "shared::events",
                "shared::exception",
                "shared::pdf",
                "shared::security"
        }
)
package com.numera.reporting;
