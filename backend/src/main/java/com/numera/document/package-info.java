@org.springframework.modulith.ApplicationModule(
        displayName = "document",
        allowedDependencies = {
                "auth",
                "auth::domain",
                "auth::infrastructure",
                "customer",
                "customer::domain",
                "customer::infrastructure",
                "shared::audit",
                "shared::config",
                "shared::domain",
                "shared::exception",
                "shared::infrastructure",
                "shared::security"
        }
)
package com.numera.document;
